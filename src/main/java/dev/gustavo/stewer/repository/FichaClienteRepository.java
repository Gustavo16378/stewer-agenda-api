package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.FichaCliente;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FichaClienteRepository implements TenantScopedRepository<FichaCliente> {

    public Optional<FichaCliente> doCliente(UUID tenantId, UUID clienteId) {
        return vivos(tenantId, "cliente.id = :cliente", Parameters.with("cliente", clienteId))
                .firstResultOptional();
    }

    /** Fichas de clientes vivos que fazem aniversario no mes (1 a 12). */
    public List<FichaCliente> aniversariantes(UUID tenantId, int mes) {
        return vivos(tenantId,
                "dataNascimento is not null and month(dataNascimento) = :mes and cliente.deletedAt is null",
                Parameters.with("mes", mes))
                .list();
    }
}
