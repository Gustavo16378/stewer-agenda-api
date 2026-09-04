package dev.gustavo.stewer.dto.auth;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Auto-cadastro de cliente. */
@Schema(name = "RegistrarRequest")
public record RegistrarRequest(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres")
        @Schema(example = "Joana Ribeiro")
        String nome,

        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email invalido")
        @Size(max = 180, message = "email deve ter no maximo 180 caracteres")
        @Schema(example = "joana@exemplo.dev")
        String email,

        @Size(max = 30, message = "telefone deve ter no maximo 30 caracteres")
        @Schema(example = "+5511999990003")
        String telefone,

        @NotBlank(message = "senha e obrigatoria")
        @Size(min = 8, max = 72, message = "senha deve ter entre 8 e 72 caracteres")
        @Schema(example = "dev12345")
        String senha,

        @Schema(description = "Slug do estabelecimento. Omitido, usa o tenant padrao.",
                example = "stewer-gabriel")
        String tenantSlug) {
}
