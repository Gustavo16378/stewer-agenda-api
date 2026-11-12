package dev.gustavo.stewer.dto.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "EsqueciSenhaRequest")
public record EsqueciSenhaRequest(
        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email invalido")
        String email,

        @Schema(description = "Slug do estabelecimento. Omitido, usa o tenant padrao.")
        String tenantSlug) {
}
