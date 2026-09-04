package dev.gustavo.stewer.mapper;

import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;

/** Mapeamento manual entidade -> DTO. Sem framework, sem surpresa. */
public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse paraResponse(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getTenantId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getRole());
    }
}
