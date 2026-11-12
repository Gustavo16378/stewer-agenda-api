package dev.gustavo.stewer.dto.usuario;

import java.time.LocalDate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
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
        String telefone,

        @Past(message = "dataNascimento tem de ser no passado")
        @Schema(description = "Opcional; nulo mantem o que ja estava. Vai para a ficha: o Stewer ve o aniversario no painel.",
                example = "1994-11-20")
        LocalDate dataNascimento) {
}
