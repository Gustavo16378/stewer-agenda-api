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

    /**
     * O cliente logado reserva um horario para si.
     *
     * <p>A ordem das validacoes e deliberada: primeiro o que nao existe (404),
     * depois o que nao faz sentido (422) e por ultimo o que disputa recurso com
     * outra pessoa (409). Assim o front consegue distinguir "escolhi errado" de
     * "alguem chegou antes".
     */
    @Transactional
    public AgendamentoResponse criar(UUID tenantId, UUID clienteId, AgendamentoRequest dto) {
        Usuario cliente = usuarioRepository.buscarVivo(tenantId, clienteId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Cliente", clienteId));
        Profissional profissional =
                horariosLivresService.exigirProfissionalAtivo(tenantId, dto.profissionalId());
        Servico servico = horariosLivresService.exigirServicoAtivo(tenantId, dto.servicoId());

        OffsetDateTime inicio = dto.inicio().withOffsetSameInstant(ZoneOffset.UTC);
        OffsetDateTime fim = inicio.plus(servico.duracao());
        Janela pedido = new Janela(inicio, fim);

        if (inicio.isBefore(OffsetDateTime.now(ZoneOffset.UTC))) {
            throw new RegraNegocioException("HORARIO_NO_PASSADO",
                    "Nao da para agendar um horario que ja passou");
        }
        if (!horariosLivresService.cabeNaGrade(tenantId, profissional.getId(), pedido)) {
            throw new RegraNegocioException("FORA_DA_DISPONIBILIDADE",
                    "O profissional nao atende neste horario");
        }
        if (temBloqueio(tenantId, profissional.getId(), pedido)) {
            throw new ConflitoException("HORARIO_BLOQUEADO",
                    "O profissional tem um compromisso neste horario");
        }
        if (agendamentoRepository.existeSobreposicao(tenantId, profissional.getId(),
                inicio, fim, null)) {
            throw new ConflitoException("HORARIO_OCUPADO",
                    "Ja existe um agendamento neste horario");
        }

        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setCliente(cliente);
        agendamento.setProfissional(profissional);
        agendamento.setServico(servico);
        agendamento.setInicio(inicio);
        agendamento.setFim(fim);
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

        return AgendamentoMapper.paraResponse(agendamento);
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
        return AgendamentoMapper.paraResponse(agendamento);
    }

    /**
     * Quem pode mexer no status: o cliente dono (so para cancelar), o
     * profissional que vai atender e o ADMIN.
     */
    private void exigirPermissaoDeStatus(Agendamento agendamento, StatusAgendamento novo) {
        if (usuarioLogado.isAdmin()) {
            return;
        }
        UUID usuarioId = usuarioLogado.usuarioId();

        if (usuarioLogado.temRole(Role.CLIENTE)) {
            if (!agendamento.getCliente().getId().equals(usuarioId)) {
                throw new AcessoNegadoException("Este agendamento nao e seu");
            }
            if (novo != StatusAgendamento.CANCELADO) {
                throw new AcessoNegadoException("Cliente so pode cancelar o proprio agendamento");
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
                throw new RegraNegocioException("PROFISSIONAL_OBRIGATORIO",
                        "Como ADMIN, informe profissionalId para ver a agenda de alguem");
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

    private boolean temBloqueio(UUID tenantId, UUID profissionalId, Janela pedido) {
        // O dia das ocupacoes e o dia do calendario do estabelecimento.
        LocalDate dia = pedido.inicio()
                .atZoneSameInstant(horariosLivresService.fusoDo(tenantId))
                .toLocalDate();
        return horariosLivresService.ocupacoesDoDia(tenantId, profissionalId, dia).stream()
                .anyMatch(pedido::colideCom)
                // ocupacoesDoDia junta bloqueios e agendamentos; separar aqui daria
                // uma query a mais so para escolher a mensagem. Se ja existe
                // agendamento, o proximo teste responde com HORARIO_OCUPADO, que e
                // mais especifico -- entao so consideramos bloqueio quando NAO ha
                // agendamento sobreposto.
                && !agendamentoRepository.existeSobreposicao(tenantId, profissionalId,
                        pedido.inicio(), pedido.fim(), null);
    }

    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
