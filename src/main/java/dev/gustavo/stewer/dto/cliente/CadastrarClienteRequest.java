package dev.gustavo.stewer.dto.cliente;

import java.time.LocalDate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

/**
 * A equipe cadastrando um cliente que pediu horario por fora do app (WhatsApp,
 * telefone, balcao). Nome e telefone bastam; o e-mail e opcional, e com ele o
 * cliente pode criar a conta depois e assumir este cadastro.
 */
@Schema(name = "CadastrarClienteRequest")
public record CadastrarClienteRequest(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 120, message = "nome deve ter no maximo 120 caracteres")
        @Schema(example = "Joana Ribeiro")
        String nome,

        @NotBlank(message = "telefone e obrigatorio")
        @Size(max = 30, message = "telefone deve ter no maximo 30 caracteres")
        @Schema(example = "(63) 99999-0003")
        String telefone,

        @Email(message = "email invalido")
        @Size(max = 180, message = "email deve ter no maximo 180 caracteres")
        @Schema(description = "Opcional. Com ele, o cliente cria a conta depois e herda o historico")
        String email,

        @Past(message = "dataNascimento tem de ser no passado")
        @Schema(description = "Opcional. Vai para a ficha (aniversariantes)")
        LocalDate dataNascimento) {
}
