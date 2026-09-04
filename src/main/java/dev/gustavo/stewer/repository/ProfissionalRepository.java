package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.Profissional;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProfissionalRepository implements TenantScopedRepository<Profissional> {

    public List<Profissional> listar(UUID tenantId, boolean apenasAtivos) {
        Sort ordem = Sort.by("usuario.nome").ascending();
        if (!apenasAtivos) {
            return vivos(tenantId, ordem).list();
        }
        return vivos(tenantId, "ativo = :ativo", ordem, Parameters.with("ativo", true)).list();
    }

    /** Usado para saber se o usuario logado e o dono da agenda que esta editando. */
    public Optional<Profissional> porUsuario(UUID tenantId, UUID usuarioId) {
        return vivos(tenantId, "usuario.id = :usuarioId", Parameters.with("usuarioId", usuarioId))
                .firstResultOptional();
    }
}
