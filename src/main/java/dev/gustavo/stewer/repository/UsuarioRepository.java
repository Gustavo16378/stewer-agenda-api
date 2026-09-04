package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Usuario;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UsuarioRepository implements TenantScopedRepository<Usuario> {

    /** Login: e-mail e case-insensitive e unico por tenant. */
    public Optional<Usuario> porEmail(UUID tenantId, String email) {
        return find(VIVOS + " and lower(email) = :email",
                Parameters.with("tenantId", tenantId)
                        .and("email", email == null ? null : email.trim().toLowerCase()))
                .firstResultOptional();
    }

    public boolean emailEmUso(UUID tenantId, String email) {
        return porEmail(tenantId, email).isPresent();
    }

    public Optional<Usuario> buscarVivoComRole(UUID tenantId, UUID id, Role role) {
        return find(VIVOS + " and id = :id and role = :role",
                Parameters.with("tenantId", tenantId).and("id", id).and("role", role))
                .firstResultOptional();
    }

    /**
     * Lista os clientes do tenant, opcionalmente filtrando por trecho de
     * nome, e-mail ou telefone.
     */
    public List<Usuario> buscarClientes(UUID tenantId, String busca, int pagina, int tamanho) {
        Sort ordem = Sort.by("nome").ascending();
        if (busca == null || busca.isBlank()) {
            return vivos(tenantId, "role = :role", ordem, Parameters.with("role", Role.CLIENTE))
                    .page(pagina, tamanho)
                    .list();
        }
        String termo = "%" + busca.trim().toLowerCase() + "%";
        return vivos(tenantId,
                "role = :role and (lower(nome) like :termo or lower(email) like :termo or telefone like :termo)",
                ordem,
                Parameters.with("role", Role.CLIENTE).and("termo", termo))
                .page(pagina, tamanho)
                .list();
    }
}
