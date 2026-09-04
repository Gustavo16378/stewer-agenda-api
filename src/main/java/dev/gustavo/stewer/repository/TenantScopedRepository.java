package dev.gustavo.stewer.repository;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.TenantOwnedEntity;

import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;

/**
 * Contrato comum dos repositorios de entidades multi-tenant.
 *
 * <p>Toda leitura passa por aqui ja filtrada por {@code tenantId} e por
 * {@code deletedAt is null}. Isso torna dificil escrever, por descuido, uma
 * query que enxergue outro estabelecimento ou registros removidos.
 */
public interface TenantScopedRepository<T extends TenantOwnedEntity>
        extends PanacheRepositoryBase<T, UUID> {

    String VIVOS = "tenantId = :tenantId and deletedAt is null";

    default PanacheQuery<T> vivos(UUID tenantId) {
        return find(VIVOS, Parameters.with("tenantId", tenantId));
    }

    default PanacheQuery<T> vivos(UUID tenantId, Sort sort) {
        return find(VIVOS, sort, Parameters.with("tenantId", tenantId));
    }

    default PanacheQuery<T> vivos(UUID tenantId, String filtroExtra, Parameters params) {
        return find(VIVOS + " and (" + filtroExtra + ")", params.and("tenantId", tenantId));
    }

    default PanacheQuery<T> vivos(UUID tenantId, String filtroExtra, Sort sort, Parameters params) {
        return find(VIVOS + " and (" + filtroExtra + ")", sort, params.and("tenantId", tenantId));
    }

    default Optional<T> buscarVivo(UUID tenantId, UUID id) {
        return find(VIVOS + " and id = :id",
                Parameters.with("tenantId", tenantId).and("id", id))
                .firstResultOptional();
    }

    /** Marca o registro como removido. Nada e apagado fisicamente. */
    default void softDelete(T entidade) {
        entidade.setDeletedAt(OffsetDateTime.now(ZoneOffset.UTC));
        persist(entidade);
    }
}
