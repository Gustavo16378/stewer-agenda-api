package dev.gustavo.stewer.dto.cliente;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Nova anotacao de visagismo sobre um cliente.
 *
 * <p>O cliente e o autor nao vem no corpo: o primeiro esta na rota e o
 * segundo sai do token. Amarrar a anotacao a um atendimento e opcional --
 * boa parte do que o profissional anota e observacao avulsa.
 */
@Schema(name = "AnotacaoRequest")
public record AnotacaoRequest(

        @NotBlank(message = "Informe o texto da anotacao")
        @Size(max = 8000, message = "A anotacao deve ter no maximo 8000 caracteres")
        String texto,

        @Schema(description = "Atendimento a que a anotacao se refere; opcional")
        UUID agendamentoId) {
}
