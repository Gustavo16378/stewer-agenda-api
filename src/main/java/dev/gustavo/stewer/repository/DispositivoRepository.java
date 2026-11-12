package dev.gustavo.stewer.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.Dispositivo;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class DispositivoRepository implements TenantScopedRepository<Dispositivo> {

    /** Token em qualquer estabelecimento: o aparelho e um so. */
    public Optional<Dispositivo> porToken(String token) {
        return find("token = :token and deletedAt is null", Parameters.with("token", token))
                .firstResultOptional();
    }

    public List<String> tokensDe(UUID tenantId, Collection<UUID> usuarioIds) {
        if (usuarioIds.isEmpty()) {
            return List.of();
        }
        return vivos(tenantId, "usuario.id in :ids", Parameters.with("ids", usuarioIds))
                .stream().map(Dispositivo::getToken).toList();
    }
}
