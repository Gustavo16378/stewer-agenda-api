package dev.gustavo.stewer.mapper;

import java.time.LocalDate;

import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;

/** Mapeamento manual entidade -> DTO. Sem framework, sem surpresa. */
public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse paraResponse(Usuario usuario) {
        return paraResponse(usuario, null);
    }

    /** Com o aniversario, que mora na ficha do cliente e nao no usuario. */
    public static UsuarioResponse paraResponse(Usuario usuario, LocalDate dataNascimento) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getTenantId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getRole(),
                usuario.getCreatedAt(),
                usuario.isLembreteEmail(),
                usuario.getFotoAtualizadaEm(),
                dataNascimento);
    }
}
