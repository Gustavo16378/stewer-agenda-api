package dev.gustavo.stewer.repository;

import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.Tenant;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TenantRepository implements PanacheRepositoryBase<Tenant, UUID> {

    public Optional<Tenant> porSlug(String slug) {
        return find("slug = ?1 and deletedAt is null", slug).firstResultOptional();
    }

    public Optional<Tenant> vivoPorId(UUID id) {
        return find("id = ?1 and deletedAt is null", id).firstResultOptional();
    }
}
