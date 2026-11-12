package dev.gustavo.stewer.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.agendamento.AgendamentoRequest;
import dev.gustavo.stewer.dto.agendamento.AgendamentoResponse;
import dev.gustavo.stewer.dto.agendamento.RemarcarRequest;
import dev.gustavo.stewer.exception.AcessoNegadoException;
import dev.gustavo.stewer.exception.ConflitoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.AgendamentoMapper;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.HorariosLivresService.Janela;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Reservas: criacao, consulta e mudanca de status.
 *
 * <p>As checagens de horario reaproveitam o {@link HorariosLivresService} de
 * proposito. Se a validacao do POST tivesse copia propria da regra de grade e
 * de sobreposicao, uma hora ela divergiria do endpoint de horarios livres -- e
 * o cliente veria um slot verde que o servidor recusa.
 */
@ApplicationScoped
public class AgendamentoService {

    /** Filtros aceitos em GET /agendamentos/meus. */
    public enum Periodo {
        FUTUROS, HISTORICO, TODOS;

        public static Periodo de(String bruto) {
            if (bruto == null || bruto.isBlank()) {
                return TODOS;
            }
            try {
                return valueOf(bruto.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RegraNegocioException("PERIODO_INVALIDO",
                        "periodo deve ser futuros, historico ou todos");
            }
        }
    }



    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    HorariosLivresService horariosLivresService;

    @Inject
    UsuarioLogado usuarioLogado;

    @Inject
    LimitesDeReserva limitesDeReserva;

    @Inject
    Event<AgendamentoEvento> eventos;

    /**
     * Cria um agendamento.
     *
     * <p>CLIENTE reserva para si. PROFISSIONAL marca para um cliente, sempre na
     * propria agenda (o cliente que ligou ou mandou mensagem). ADMIN marca para
     * qualquer cliente em qualquer agenda.
     *
     * <p>A ordem das validacoes e deliberada: primeiro o que nao existe (404),
     * depois o que nao faz sentido (422) e por ultimo o que disputa recurso com
     * outra pessoa (409). Assim o front consegue distinguir "escolhi errado" de
     * "alguem chegou antes".
     */
    @Transactional
    public AgendamentoResponse criar(UUID tenantId, AgendamentoRequest dto) {
        Usuario cliente = resolverCliente(tenantId, dto.clienteId());
        Profissional profissional =
                horariosLivresService.exigirProfissionalAtivo(tenantId, dto.profissionalId());
        exigirPodeUsarAgenda(profissional);
        Servico servico = horariosLivresService.exigirServicoAtivo(tenantId, dto.servicoId());

        OffsetDateTime inicio = dto.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        Janela pedido = new Janela(inicio, inicio.plus(servico.duracao()));

        if (usuarioLogado.temRole(Role.CLIENTE)) {
            limitesDeReserva.exigirDentroDosLimites(tenantId, cliente.getId(), inicio);
        }
        exigirHorarioReservavel(tenantId, profissional.getId(), pedido, null);

        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setCliente(cliente);
        agendamento.setProfissional(profissional);
        agendamento.setServico(servico);
        agendamento.setInicio(pedido.inicio());
        agendamento.setFim(pedido.fim());
        agendamento.setStatus(StatusAgendamento.AGENDADO);
        agendamento.setObservacao(normalizar(dto.observacao()));

        // Flush para que id e createdAt ja existam ao montar o 201 e o Location.
        //
        // A checagem la em cima resolve o caso comum e devolve 409 sem ir ao
        // banco. Mas entre ela e este insert cabe a transacao de outro cliente:
        // em READ COMMITTED nenhuma das duas enxerga a outra. Quem fecha a porta
        // e a restricao EXCLUDE da V4, e ViolacaoDeBanco traduz a violacao dela
        // no mesmo 409 -- no mapper, porque o INSERT pode sair so no commit.
        agendamentoRepository.persistAndFlush(agendamento);

        eventos.fire(new AgendamentoEvento(agendamento.getId(), AgendamentoEvento.Tipo.CRIADO));
        return AgendamentoMapper.paraResponse(agendamento);
    }

    /**
     * Troca o horario (e, se pedido, o profissional) de um agendamento que ainda
     * vai acontecer, numa transacao so. O servico e a duracao ficam os mesmos.
     *
     * <p>O proprio agendamento e ignorado na checagem de sobreposicao: remarcar
     * 30 minutos para frente dentro do mesmo atendimento e valido.
     */
    @Transactional
    public AgendamentoResponse remarcar(UUID tenantId, UUID agendamentoId, RemarcarRequest dto) {
        Agendamento agendamento = agendamentoRepository.buscarVivo(tenantId, agendamentoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Agendamento", agendamentoId));

        exigirPermissaoSobre(agendamento);

        if (agendamento.getStatus() != StatusAgendamento.AGENDADO) {
            throw new ConflitoException("STATUS_FINAL",
                    "Este agendamento ja esta " + agendamento.getStatus()
                            + " e nao pode ser remarcado");
        }

        Profissional profissional = agendamento.getProfissional();
        if (dto.profissionalId() != null && !dto.profissionalId().equals(profissional.getId())) {
            profissional = horariosLivresService.exigirProfissionalAtivo(tenantId, dto.profissionalId());
            exigirPodeUsarAgenda(profissional);
        }

        OffsetDateTime inicio = dto.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        Janela pedido = new Janela(inicio, inicio.plus(agendamento.getServico().duracao()));
        exigirHorarioReservavel(tenantId, profissional.getId(), pedido, agendamento.getId());

        agendamento.setProfissional(profissional);
        agendamento.setInicio(pedido.inicio());
        agendamento.setFim(pedido.fim());
        // Horario novo, lembrete novo.
        agendamento.setLembreteEnviadoEm(null);
        agendamentoRepository.persistAndFlush(agendamento);

        eventos.fire(new AgendamentoEvento(agendamento.getId(), AgendamentoEvento.Tipo.REMARCADO));
        return AgendamentoMapper.paraResponse(agendamento);
    }

    /**
     * O mesmo funil de regras para criar e remarcar: passado e grade (422),
     * depois disputa com outro atendimento ou com um bloqueio (409).
     */
    private void exigirHorarioReservavel(UUID tenantId, UUID profissionalId, Janela pedido,
            UUID ignorarAgendamentoId) {
        if (pedido.inicio().isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new RegraNegocioException("HORARIO_NO_PASSADO",
                    "Nao da para agendar um horario que ja passou");
        }
        if (!horariosLivresService.cabeNaGrade(tenantId, profissionalId, pedido)) {
            throw new RegraNegocioException("FORA_DA_DISPONIBILIDADE",
                    "O profissional nao atende neste horario");
        }
        // Agendamento antes de bloqueio: HORARIO_OCUPADO e o motivo mais
        // especifico quando os dois coincidem.
        if (agendamentoRepository.existeSobreposicao(tenantId, profissionalId,
                pedido.inicio(), pedido.fim(), ignorarAgendamentoId)) {
            throw new ConflitoException("HORARIO_OCUPADO",
                    "Ja existe um agendamento neste horario");
        }
        if (horariosLivresService.temBloqueio(tenantId, profissionalId, pedido)) {
            throw new ConflitoException("HORARIO_BLOQUEADO",
                    "O profissional tem um compromisso neste horario");
        }
    }

    /**
     * CLIENTE: e ele mesmo, sempre. PROFISSIONAL e ADMIN: o cliente informado,
     * que tem de existir neste estabelecimento com papel de CLIENTE.
     */
    private Usuario resolverCliente(UUID tenantId, UUID clienteIdPedido) {
        if (usuarioLogado.temRole(Role.CLIENTE)) {
            return usuarioRepository.buscarVivo(tenantId, usuarioLogado.usuarioId())
                    .orElseThrow(() -> RecursoNaoEncontradoException.de(
                            "Cliente", usuarioLogado.usuarioId()));
        }
        if (clienteIdPedido == null) {
            throw new RegraNegocioException("CLIENTE_OBRIGATORIO",
                    "Informe clienteId para marcar em nome de um cliente");
        }
        return usuarioRepository.buscarVivoComRole(tenantId, clienteIdPedido, Role.CLIENTE)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Cliente", clienteIdPedido));
    }

    /** PROFISSIONAL so marca na propria agenda; ADMIN e CLIENTE em qualquer uma. */
    private void exigirPodeUsarAgenda(Profissional profissional) {
        if (usuarioLogado.temRole(Role.PROFISSIONAL)
                && !profissional.getUsuario().getId().equals(usuarioLogado.usuarioId())) {
            throw new AcessoNegadoException("Voce so marca horarios na sua propria agenda");
        }
    }

    /** Agendamentos do proprio cliente logado. */
    @Transactional
    public List<AgendamentoResponse> meus(UUID tenantId, UUID clienteId, Periodo periodo) {
        List<Agendamento> encontrados = switch (periodo) {
            case TODOS -> agendamentoRepository.doCliente(tenantId, clienteId);
            case FUTUROS -> agendamentoRepository.doClientePorPeriodo(tenantId, clienteId,
                    OffsetDateTime.now(ZoneOffset.UTC), true);
            case HISTORICO -> agendamentoRepository.doClientePorPeriodo(tenantId, clienteId,
                    OffsetDateTime.now(ZoneOffset.UTC), false);
        };
        return AgendamentoMapper.paraResponses(encontrados);
    }

    /**
     * Agenda de um dia, com TODOS os status -- inclusive cancelados. Quem esta
     * atendendo precisa enxergar o que caiu da agenda, nao so o que sobrou.
     *
     * <p>O PROFISSIONAL ve a propria agenda e ignora {@code profissionalIdParam};
     * o ADMIN nao tem agenda propria, entao para ele o parametro e obrigatorio.
     */
    @Transactional
    public List<AgendamentoResponse> agendaDoDia(UUID tenantId, LocalDate data,
            UUID profissionalIdParam) {
        UUID alvo = resolverProfissionalDaAgenda(tenantId, profissionalIdParam);

        // O dia comeca e termina no relogio do estabelecimento, nao em UTC: um
        // atendimento as 22:00 de Palmas e 01:00Z do dia seguinte e pertence a
        // agenda de ontem, nao a de hoje.
        ZoneId fuso = horariosLivresService.fusoDo(tenantId);
        OffsetDateTime inicioDoDia = data.atStartOfDay(fuso).toOffsetDateTime();
        OffsetDateTime fimDoDia = data.plusDays(1).atStartOfDay(fuso).toOffsetDateTime();

        return AgendamentoMapper.paraResponses(
                agendamentoRepository.doProfissionalNaJanela(tenantId, alvo, inicioDoDia, fimDoDia));
    }

    /**
     * Concluir, cancelar ou marcar falta.
     *
     * <p>So agendamento em {@code AGENDADO} muda de estado: os outros tres sao
     * finais. Sem isso daria para "concluir" um atendimento cancelado e o slot
     * voltaria a ocupar a agenda retroativamente.
     */
    @Transactional
    public AgendamentoResponse atualizarStatus(UUID tenantId, UUID agendamentoId,
            StatusAgendamento novo) {
        Agendamento agendamento = agendamentoRepository.buscarVivo(tenantId, agendamentoId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Agendamento", agendamentoId));

        exigirPermissaoDeStatus(agendamento, novo);

        if (agendamento.getStatus().isFinal()) {
            throw new ConflitoException("STATUS_FINAL",
                    "Este agendamento ja esta " + agendamento.getStatus()
                            + " e nao pode mais mudar de status");
        }
        if (novo == StatusAgendamento.AGENDADO) {
            throw new RegraNegocioException("STATUS_INVALIDO",
                    "Um agendamento nao pode voltar para AGENDADO");
        }

        agendamento.setStatus(novo);
        agendamentoRepository.persistAndFlush(agendamento);
        if (novo == StatusAgendamento.CANCELADO) {
            eventos.fire(new AgendamentoEvento(agendamento.getId(), AgendamentoEvento.Tipo.CANCELADO));
        }
        return AgendamentoMapper.paraResponse(agendamento);
    }

    /**
     * Quem pode mexer no status: o cliente dono (so para cancelar), o
     * profissional que vai atender e o ADMIN.
     */
    private void exigirPermissaoDeStatus(Agendamento agendamento, StatusAgendamento novo) {
        exigirPermissaoSobre(agendamento);
        if (usuarioLogado.temRole(Role.CLIENTE) && novo != StatusAgendamento.CANCELADO) {
            throw new AcessoNegadoException("Cliente so pode cancelar o proprio agendamento");
        }
    }

    /**
     * Quem pode mexer num agendamento (status ou horario): o cliente dono, o
     * profissional que vai atender e o ADMIN.
     */
    private void exigirPermissaoSobre(Agendamento agendamento) {
        if (usuarioLogado.isAdmin()) {
            return;
        }
        UUID usuarioId = usuarioLogado.usuarioId();

        if (usuarioLogado.temRole(Role.CLIENTE)) {
            if (!agendamento.getCliente().getId().equals(usuarioId)) {
                throw new AcessoNegadoException("Este agendamento nao e seu");
            }
            return;
        }

        // PROFISSIONAL: so mexe no que ele mesmo vai atender.
        if (!agendamento.getProfissional().getUsuario().getId().equals(usuarioId)) {
            throw new AcessoNegadoException("Este agendamento e da agenda de outro profissional");
        }
    }

    private UUID resolverProfissionalDaAgenda(UUID tenantId, UUID profissionalIdParam) {
        if (usuarioLogado.isAdmin()) {
            if (profissionalIdParam == null) {
                // Dono que tambem atende: sem parametro, ve a propria agenda.
                return profissionalRepository.porUsuario(tenantId, usuarioLogado.usuarioId())
                        .map(Profissional::getId)
                        .orElseThrow(() -> new RegraNegocioException("PROFISSIONAL_OBRIGATORIO",
                                "Como ADMIN sem ficha de profissional, informe profissionalId "
                                        + "para ver a agenda de alguem"));
            }
            return profissionalRepository.buscarVivo(tenantId, profissionalIdParam)
                    .orElseThrow(() -> RecursoNaoEncontradoException.de(
                            "Profissional", profissionalIdParam))
                    .getId();
        }

        // Usuario com role PROFISSIONAL mas sem ficha cadastrada nao tem agenda:
        // e 403, nao 404 -- a rota existe, quem nao pode e ele.
        return profissionalRepository.porUsuario(tenantId, usuarioLogado.usuarioId())
                .orElseThrow(() -> new AcessoNegadoException(
                        "Voce nao tem uma ficha de profissional neste estabelecimento"))
                .getId();
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
