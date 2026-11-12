package dev.gustavo.stewer.dto.usuario;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** O proprio usuario corrigindo os dados de contato. Papel e senha nao passam por aqui. */
@Schema(name = "AtualizarPerfilRequest")
public record AtualizarPerfilRequest(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres")
        String nome,

        @NotBlank(message = "email e obrigatorio")
        @Email(message = "email invalido")
        @Size(max = 180, message = "email deve ter no maximo 180 caracteres")
        String email,

        @Size(max = 30, message = "telefone deve ter no maximo 30 caracteres")
        String telefone) {
}
