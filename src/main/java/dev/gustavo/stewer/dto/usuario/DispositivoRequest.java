package dev.gustavo.stewer.dto.usuario;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(name = "DispositivoRequest")
public record DispositivoRequest(
        @NotBlank(message = "token e obrigatorio")
        @Size(max = 255, message = "token deve ter no maximo 255 caracteres")
        @Schema(description = "Token do Expo Push (ExponentPushToken[...])")
        String token) {
}
