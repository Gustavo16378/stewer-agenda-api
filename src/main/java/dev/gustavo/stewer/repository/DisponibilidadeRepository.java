package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Disponibilidade;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DisponibilidadeRepository implements TenantScopedRepository<Disponibilidade> {

    public List<Disponibilidade> daGrade(UUID tenantId, UUID profissionalId) {
        return vivos(tenantId, "profissional.id = :prof",
                Sort.by("diaSemana").and("horaInicio"),
                Parameters.with("prof", profissionalId))
                .list();
    }

    /** Faixas de um dia da semana especifico (0 = domingo ... 6 = sabado). */
    public List<Disponibilidade> doDia(UUID tenantId, UUID profissionalId, short diaSemana) {
        return vivos(tenantId, "profissional.id = :prof and diaSemana = :dia",
                Sort.by("horaInicio").ascending(),
                Parameters.with("prof", profissionalId).and("dia", diaSemana))
                .list();
    }
}
