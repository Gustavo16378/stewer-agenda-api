package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.AnotacaoCliente;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class AnotacaoClienteRepository implements TenantScopedRepository<AnotacaoCliente> {

    public List<AnotacaoCliente> doCliente(UUID tenantId, UUID clienteId) {
        return vivos(tenantId, "cliente.id = :cli", Sort.by("createdAt").descending(),
                Parameters.with("cli", clienteId))
                .list();
    }
}
