package dev.gustavo.stewer.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

/**
 * Entidade que pertence a um tenant.
 *
 * <p>O tenant e um escopo transversal, entao ele e guardado como UUID puro
 * (e nao como associacao) para que todo filtro de query seja barato e
 * impossivel de esquecer.
 */
@MappedSuperclass
public abstract class TenantOwnedEntity extends BaseEntity {

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }
}
