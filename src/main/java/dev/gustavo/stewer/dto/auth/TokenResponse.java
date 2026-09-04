package dev.gustavo.stewer.dto.auth;

import dev.gustavo.stewer.dto.usuario.UsuarioResponse;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "TokenResponse")
public record TokenResponse(
        @Schema(description = "JWT assinado; mande em Authorization: Bearer <token>")
        String token,
        @Schema(example = "Bearer")
        String tipo,
        @Schema(description = "Validade em segundos", example = "28800")
        long expiraEmSegundos,
        UsuarioResponse usuario) {

    public static TokenResponse de(String token, long expiraEmSegundos, UsuarioResponse usuario) {
        return new TokenResponse(token, "Bearer", expiraEmSegundos, usuario);
    }
}
