package dev.gustavo.stewer.dto.usuario;

import java.util.UUID;

import dev.gustavo.stewer.domain.Role;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Usuario para consumo do front. Nunca carrega o hash da senha. */
@Schema(name = "Usuario")
public record UsuarioResponse(
        UUID id,
        UUID tenantId,
        String nome,
        String email,
        String telefone,
        Role role) {
}
