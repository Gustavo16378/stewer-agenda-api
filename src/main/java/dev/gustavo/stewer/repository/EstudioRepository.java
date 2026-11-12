package dev.gustavo.stewer.repository;

import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.Estudio;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EstudioRepository implements TenantScopedRepository<Estudio> {

    public Optional<Estudio> doTenant(UUID tenantId) {
        return vivos(tenantId).firstResultOptional();
    }
}
