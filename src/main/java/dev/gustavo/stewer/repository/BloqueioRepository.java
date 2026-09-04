package dev.gustavo.stewer.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Bloqueio;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class BloqueioRepository implements TenantScopedRepository<Bloqueio> {

    /**
     * Bloqueios que tocam a janela [inicio, fim). Sobreposicao aberta:
     * {@code bloqueio.inicio < fim and bloqueio.fim > inicio}.
     */
    public List<Bloqueio> naJanela(UUID tenantId, UUID profissionalId,
            OffsetDateTime inicio, OffsetDateTime fim) {
        return vivos(tenantId,
                "profissional.id = :prof and inicio < :fim and fim > :inicio",
                Sort.by("inicio").ascending(),
                Parameters.with("prof", profissionalId).and("inicio", inicio).and("fim", fim))
                .list();
    }

    public List<Bloqueio> doProfissional(UUID tenantId, UUID profissionalId) {
        return vivos(tenantId, "profissional.id = :prof",
                Sort.by("inicio").descending(),
                Parameters.with("prof", profissionalId))
                .list();
    }
}
