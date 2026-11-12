package dev.gustavo.stewer.dto.usuario;

import java.time.OffsetDateTime;
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
        Role role,
        @Schema(description = "Quando a conta foi criada")
        OffsetDateTime criadoEm,
        @Schema(description = "Recebe lembrete do horario por e-mail")
        boolean lembreteEmail,
        @Schema(description = "Quando a foto de perfil mudou; nulo se nao tem foto")
        OffsetDateTime fotoVersao) {
}
