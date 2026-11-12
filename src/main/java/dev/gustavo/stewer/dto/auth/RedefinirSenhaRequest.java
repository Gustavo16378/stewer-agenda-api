package dev.gustavo.stewer.dto.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RedefinirSenhaRequest")
public record RedefinirSenhaRequest(
        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email invalido")
        String email,

        @NotBlank(message = "codigo e obrigatorio")
        @Pattern(regexp = "\\d{6}", message = "codigo tem 6 digitos")
        String codigo,

        @NotBlank(message = "novaSenha e obrigatoria")
        @Size(min = 8, max = 72, message = "novaSenha deve ter entre 8 e 72 caracteres")
        String novaSenha,

        @Schema(description = "Slug do estabelecimento. Omitido, usa o tenant padrao.")
        String tenantSlug) {
}
