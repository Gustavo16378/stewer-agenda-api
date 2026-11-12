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

    /** Agendamentos ainda por acontecer que o cliente tem marcados. */
    public long contarFuturosDoCliente(UUID tenantId, UUID clienteId, OffsetDateTime agora) {
        return vivos(tenantId, "cliente.id = :cli and status = :status and inicio > :agora",
                Parameters.with("cli", clienteId)
                        .and("status", StatusAgendamento.AGENDADO)
                        .and("agora", agora))
                .count();
    }

    /**
     * Para cada cliente da lista: quantos atendimentos concluidos e quando foi o
     * ultimo. Uma consulta para a pagina inteira, nao uma por cliente.
     */
    public java.util.Map<UUID, Object[]> visitasPorCliente(UUID tenantId, java.util.Collection<UUID> clienteIds) {
        if (clienteIds.isEmpty()) {
            return java.util.Map.of();
        }
        List<Object[]> linhas = getEntityManager().createQuery("""
                select a.cliente.id, count(a), max(a.inicio) from Agendamento a
                 where a.tenantId = :tenant and a.deletedAt is null
                   and a.status = :concluido and a.cliente.id in :ids
                 group by a.cliente.id
                """, Object[].class)
                .setParameter("tenant", tenantId)
                .setParameter("concluido", StatusAgendamento.CONCLUIDO)
                .setParameter("ids", clienteIds)
                .getResultList();
        java.util.Map<UUID, Object[]> mapa = new java.util.HashMap<>();
        for (Object[] l : linhas) {
            mapa.put((UUID) l[0], new Object[] {l[1], l[2]});
        }
        return mapa;
    }

    /** Clientes que tem algum atendimento concluido antes de {@code antes}. */
    public java.util.Set<UUID> clientesComVisitaAntes(UUID tenantId, java.util.Collection<UUID> clienteIds,
            OffsetDateTime antes) {
        if (clienteIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(getEntityManager().createQuery("""
                select distinct a.cliente.id from Agendamento a
                 where a.tenantId = :tenant and a.deletedAt is null
                   and a.status = :concluido and a.cliente.id in :ids and a.inicio < :antes
                """, UUID.class)
                .setParameter("tenant", tenantId)
                .setParameter("concluido", StatusAgendamento.CONCLUIDO)
                .setParameter("ids", clienteIds)
                .setParameter("antes", antes)
                .getResultList());
    }

    /**
     * Ritmo de cada cliente com atendimento concluido: [id, visitas, primeira,
     * ultima, Usuario]. Base da lista de clientes sumidos.
     */
    public List<Object[]> ritmoDeVisitas(UUID tenantId) {
        return getEntityManager().createQuery("""
                select a.cliente.id, count(a), min(a.inicio), max(a.inicio), a.cliente
                  from Agendamento a
                 where a.tenantId = :tenant and a.deletedAt is null and a.status = :concluido
                   and a.cliente.deletedAt is null
                 group by a.cliente.id, a.cliente
                """, Object[].class)
                .setParameter("tenant", tenantId)
                .setParameter("concluido", StatusAgendamento.CONCLUIDO)
                .getResultList();
    }

    /** Clientes com algum horario ainda por vir. */
    public java.util.Set<UUID> clientesComHorarioFuturo(UUID tenantId, OffsetDateTime agora) {
        return new java.util.HashSet<>(getEntityManager().createQuery("""
                select distinct a.cliente.id from Agendamento a
                 where a.tenantId = :tenant and a.deletedAt is null
                   and a.status = :agendado and a.inicio > :agora
                """, UUID.class)
                .setParameter("tenant", tenantId)
                .setParameter("agendado", StatusAgendamento.AGENDADO)
                .setParameter("agora", agora)
                .getResultList());
    }
}
