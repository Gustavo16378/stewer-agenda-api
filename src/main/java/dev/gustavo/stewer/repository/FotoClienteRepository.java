package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.FotoCliente;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FotoClienteRepository implements TenantScopedRepository<FotoCliente> {

    public List<FotoCliente> doCliente(UUID tenantId, UUID clienteId) {
        return vivos(tenantId, "cliente.id = :cliente", Sort.descending("createdAt"),
                Parameters.with("cliente", clienteId))
                .list();
    }
}
