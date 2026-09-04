package dev.gustavo.stewer.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Bloqueio;
import dev.gustavo.stewer.domain.Disponibilidade;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.dto.disponibilidade.BloqueioRequest;
import dev.gustavo.stewer.dto.disponibilidade.BloqueioResponse;
import dev.gustavo.stewer.dto.disponibilidade.DisponibilidadeResponse;
import dev.gustavo.stewer.dto.disponibilidade.FaixaHorariaRequest;
import dev.gustavo.stewer.dto.disponibilidade.GradeSemanalRequest;
import dev.gustavo.stewer.exception.AcessoNegadoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.DisponibilidadeMapper;
import dev.gustavo.stewer.repository.BloqueioRepository;
import dev.gustavo.stewer.repository.DisponibilidadeRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.security.UsuarioLogado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Agenda do profissional: a grade semanal recorrente e os bloqueios pontuais.
 *
 * <p>Ate as leituras rodam em transacao porque o mapper toca
 * {@code disponibilidade.getProfissional()}, que e LAZY -- montar a resposta
 * fora da transacao levantaria LazyInitializationException.
 *
 * <p>{@link UsuarioLogado} e injetado aqui, e nao recebido por parametro, para
 * que a checagem de dono fique junto da regra que ela protege: quem chamar
 * {@link #substituirGrade} de qualquer lugar herda a validacao de graca.
 */
@ApplicationScoped
public class DisponibilidadeService {

    /** Ordem canonica da grade, a mesma que o GET devolve. */
    private static final Comparator<FaixaHorariaRequest> POR_DIA_E_HORA =
            Comparator.comparing(FaixaHorariaRequest::diaSemana)
                    .thenComparing(FaixaHorariaRequest::horaInicio);

    /** Sem acento de proposito: mensagem de erro vai crua no JSON. */
    private static final String[] NOMES_DOS_DIAS = {
            "domingo", "segunda-feira", "terca-feira", "quarta-feira",
            "quinta-feira", "sexta-feira", "sabado"
    };

    @Inject
    DisponibilidadeRepository disponibilidadeRepository;

    @Inject
    BloqueioRepository bloqueioRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    UsuarioLogado usuarioLogado;

    /** Grade vigente, ordenada por dia da semana e hora de inicio. */
    @Transactional
    public List<DisponibilidadeResponse> grade(UUID tenantId, UUID profissionalId) {
        exigirProfissional(tenantId, profissionalId);
        return DisponibilidadeMapper.paraResponses(
                disponibilidadeRepository.daGrade(tenantId, profissionalId));
    }

    /**
     * Substitui a semana inteira: soft delete de tudo que esta valendo hoje e
     * insercao das faixas novas, na mesma transacao.
     *
     * <p>Apagar e recriar em vez de casar faixa a faixa e proposital: a grade e
     * pequena, e assim nao existe estado intermediario meio-antigo/meio-novo
     * caso a validacao estoure no meio do caminho.
     */
    @Transactional
    public List<DisponibilidadeResponse> substituirGrade(UUID tenantId, UUID profissionalId,
            GradeSemanalRequest dto) {
        Profissional profissional = exigirDonoOuAdmin(tenantId, profissionalId);
        validarFaixas(dto.faixas());

        for (Disponibilidade antiga : disponibilidadeRepository.daGrade(tenantId, profissionalId)) {
            disponibilidadeRepository.softDelete(antiga);
        }

        for (FaixaHorariaRequest faixa : dto.faixas()) {
            Disponibilidade nova = new Disponibilidade();
            nova.setTenantId(tenantId);
            nova.setProfissional(profissional);
            nova.setDiaSemana(faixa.diaSemana().shortValue());
            nova.setHoraInicio(faixa.horaInicio());
            nova.setHoraFim(faixa.horaFim());
            disponibilidadeRepository.persist(nova);
        }

        // Reler em vez de montar a resposta com a lista recem-criada garante que
        // o PUT devolva exatamente a mesma ordenacao do GET.
        return DisponibilidadeMapper.paraResponses(
                disponibilidadeRepository.daGrade(tenantId, profissionalId));
    }

    /** Bloqueios vivos do profissional, do mais recente para o mais antigo. */
    @Transactional
    public List<BloqueioResponse> listarBloqueios(UUID tenantId, UUID profissionalId) {
        exigirProfissional(tenantId, profissionalId);
        return DisponibilidadeMapper.paraBloqueioResponses(
                bloqueioRepository.doProfissional(tenantId, profissionalId));
    }

    @Transactional
    public BloqueioResponse criarBloqueio(UUID tenantId, UUID profissionalId, BloqueioRequest dto) {
        Profissional profissional = exigirDonoOuAdmin(tenantId, profissionalId);

        if (!dto.fim().isAfter(dto.inicio())) {
            throw new RegraNegocioException("BLOQUEIO_INVALIDO",
                    "O fim do bloqueio (" + dto.fim() + ") precisa ser posterior ao inicio ("
                            + dto.inicio() + ")");
        }

        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setTenantId(tenantId);
        bloqueio.setProfissional(profissional);
        bloqueio.setInicio(dto.inicio());
        bloqueio.setFim(dto.fim());
        bloqueio.setMotivo(normalizar(dto.motivo()));

        // Flush aqui para que id e createdAt gerados pelo Hibernate ja estejam
        // preenchidos quando o mapper montar a resposta e o Location do 201.
        bloqueioRepository.persistAndFlush(bloqueio);

        return DisponibilidadeMapper.paraBloqueioResponse(bloqueio);
    }

    /**
     * Remove um bloqueio da agenda (soft delete). Bloqueio de outro profissional
     * -- ou de outro tenant -- e tratado como inexistente: 404, nunca 403, para
     * nao confirmar a existencia de recurso alheio.
     */
    @Transactional
    public void removerBloqueio(UUID tenantId, UUID profissionalId, UUID bloqueioId) {
        exigirDonoOuAdmin(tenantId, profissionalId);

        Bloqueio bloqueio = bloqueioRepository.buscarVivo(tenantId, bloqueioId)
                .filter(b -> b.getProfissional().getId().equals(profissionalId))
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Bloqueio", bloqueioId));

        bloqueioRepository.softDelete(bloqueio);
    }

    /**
     * Valida a semana inteira antes de encostar no banco: cada faixa precisa
     * terminar depois de comecar, e duas faixas do mesmo dia nao podem se
     * sobrepor. Encostar (09:00-12:00 e 12:00-18:00) e permitido, porque o
     * intervalo e meio-aberto -- o mesmo criterio usado na sobreposicao de
     * agendamentos.
     */
    private void validarFaixas(List<FaixaHorariaRequest> faixas) {
        for (FaixaHorariaRequest faixa : faixas) {
            if (!faixa.horaFim().isAfter(faixa.horaInicio())) {
                throw new RegraNegocioException("FAIXA_INVALIDA",
                        "Faixa invalida em " + nomeDoDia(faixa.diaSemana()) + ": horaFim ("
                                + faixa.horaFim() + ") precisa ser maior que horaInicio ("
                                + faixa.horaInicio() + ")");
            }
        }

        List<FaixaHorariaRequest> ordenadas = new ArrayList<>(faixas);
        ordenadas.sort(POR_DIA_E_HORA);
        for (int i = 1; i < ordenadas.size(); i++) {
            FaixaHorariaRequest anterior = ordenadas.get(i - 1);
            FaixaHorariaRequest atual = ordenadas.get(i);
            if (atual.diaSemana().equals(anterior.diaSemana())
                    && atual.horaInicio().isBefore(anterior.horaFim())) {
                throw new RegraNegocioException("FAIXAS_SOBREPOSTAS",
                        "Faixas sobrepostas em " + nomeDoDia(atual.diaSemana()) + ": "
                                + anterior.horaInicio() + "-" + anterior.horaFim() + " e "
                                + atual.horaInicio() + "-" + atual.horaFim());
            }
        }
    }

    /**
     * O profissional precisa existir vivo NESTE tenant. Agenda de outro
     * estabelecimento simplesmente nao existe para quem chamou: 404.
     */
    private Profissional exigirProfissional(UUID tenantId, UUID profissionalId) {
        return profissionalRepository.buscarVivo(tenantId, profissionalId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Profissional", profissionalId));
    }

    /**
     * Quem escreve na agenda e o ADMIN (qualquer uma) ou o proprio profissional
     * (so a dele). A role sozinha nao basta: {@code @RolesAllowed} deixa passar
     * qualquer PROFISSIONAL do tenant, e sem esta checagem um deles editaria a
     * grade do colega.
     *
     * <p>A ordem importa: primeiro 404 (profissional inexistente ou de outro
     * tenant), so depois 403 -- assim ninguem descobre ids alheios pelo status.
     */
    private Profissional exigirDonoOuAdmin(UUID tenantId, UUID profissionalId) {
        Profissional profissional = exigirProfissional(tenantId, profissionalId);

        if (!usuarioLogado.isAdmin()
                && !profissional.getUsuario().getId().equals(usuarioLogado.usuarioId())) {
            throw new AcessoNegadoException("Voce so pode alterar a sua propria agenda");
        }
        return profissional;
    }

    private static String nomeDoDia(Integer diaSemana) {
        if (diaSemana == null || diaSemana < 0 || diaSemana >= NOMES_DOS_DIAS.length) {
            return "dia " + diaSemana;
        }
        return NOMES_DOS_DIAS[diaSemana];
    }

    /** Motivo em branco vira null: evita string vazia no banco. */
    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
