package dev.gustavo.stewer.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.StatusAgendamento;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AgendamentoRepository implements TenantScopedRepository<Agendamento> {

    /** Sobreposicao de intervalos meio-abertos: a.inicio < fim && a.fim > inicio. */
    private static final String SOBREPOE =
            "profissional.id = :prof and status = :status and inicio < :fim and fim > :inicio";

    /**
     * Agendamentos que ocupam a agenda do profissional dentro da janela.
     * Somente status AGENDADO ocupa: cancelado e nao-comparecimento liberam o slot.
     */
    public List<Agendamento> ativosNaJanela(UUID tenantId, UUID profissionalId,
            OffsetDateTime inicio, OffsetDateTime fim) {
        return vivos(tenantId, SOBREPOE, Sort.by("inicio").ascending(),
                Parameters.with("prof", profissionalId)
                        .and("status", StatusAgendamento.AGENDADO)
                        .and("inicio", inicio)
                        .and("fim", fim))
                .list();
    }

    /** Existe algum agendamento ativo colidindo? Base da regra de nao-sobreposicao. */
    public boolean existeSobreposicao(UUID tenantId, UUID profissionalId,
            OffsetDateTime inicio, OffsetDateTime fim, UUID ignorarId) {
        String filtro = SOBREPOE + (ignorarId == null ? "" : " and id <> :ignorar");
        Parameters params = Parameters.with("prof", profissionalId)
                .and("status", StatusAgendamento.AGENDADO)
                .and("inicio", inicio)
                .and("fim", fim);
        if (ignorarId != null) {
            params = params.and("ignorar", ignorarId);
        }
        return vivos(tenantId, filtro, params).count() > 0;
    }

    /** Agenda do profissional em uma janela (todos os status, para a visao do dia). */
    public List<Agendamento> doProfissionalNaJanela(UUID tenantId, UUID profissionalId,
            OffsetDateTime inicio, OffsetDateTime fim) {
        return vivos(tenantId,
                "profissional.id = :prof and inicio < :fim and fim > :inicio",
                Sort.by("inicio").ascending(),
                Parameters.with("prof", profissionalId).and("inicio", inicio).and("fim", fim))
                .list();
    }

    public List<Agendamento> doCliente(UUID tenantId, UUID clienteId) {
        return vivos(tenantId, "cliente.id = :cli", Sort.by("inicio").descending(),
                Parameters.with("cli", clienteId))
                .list();
    }

    /** Futuros (inicio >= agora) ou historico (inicio < agora) do cliente. */
    public List<Agendamento> doClientePorPeriodo(UUID tenantId, UUID clienteId,
            OffsetDateTime referencia, boolean futuros) {
        String comparador = futuros ? "inicio >= :ref" : "inicio < :ref";
        Sort ordem = futuros ? Sort.by("inicio").ascending() : Sort.by("inicio").descending();
        return vivos(tenantId, "cliente.id = :cli and " + comparador, ordem,
                Parameters.with("cli", clienteId).and("ref", referencia))
                .list();
    }
}
