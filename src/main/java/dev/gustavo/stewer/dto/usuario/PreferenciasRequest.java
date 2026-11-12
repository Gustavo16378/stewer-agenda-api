package dev.gustavo.stewer.dto.usuario;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

@Schema(name = "PreferenciasRequest")
public record PreferenciasRequest(
        @NotNull(message = "lembreteEmail e obrigatorio")
        @Schema(description = "Receber lembrete do horario por e-mail")
        Boolean lembreteEmail) {
}
