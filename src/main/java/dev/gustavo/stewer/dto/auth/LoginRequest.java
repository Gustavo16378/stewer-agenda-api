package dev.gustavo.stewer.dto.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest")
public record LoginRequest(
        @NotBlank(message = "email e obrigatorio")
        @Schema(example = "stewer@stewer.dev")
        String email,

        @NotBlank(message = "senha e obrigatoria")
        @Schema(example = "dev12345")
        String senha,

        @Schema(description = "Slug do estabelecimento. Omitido, usa o tenant padrao.",
                example = "stewer-gabriel")
        String tenantSlug) {
}
