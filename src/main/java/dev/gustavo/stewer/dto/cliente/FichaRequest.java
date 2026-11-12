package dev.gustavo.stewer.dto.cliente;

import java.time.LocalDate;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

/**
 * Tudo o que a equipe guarda sobre o cliente. Campo ausente ou vazio apaga o
 * valor: a tela sempre manda a ficha inteira.
 */
@Schema(name = "FichaRequest")
public record FichaRequest(
        @PastOrPresent(message = "a data de nascimento nao pode estar no futuro")
        LocalDate dataNascimento,
        @Size(max = 40, message = "origem com no maximo 40 caracteres")
        @Schema(examples = "Instagram")
        String origem,
        @Size(max = 12, message = "no maximo 12 etiquetas")
        List<@Size(max = 40, message = "etiqueta com no maximo 40 caracteres") String> etiquetas,
        @Size(max = 40, message = "formato do rosto com no maximo 40 caracteres")
        @Schema(examples = "Oval")
        String formatoRosto,
        @Size(max = 80, message = "cabelo com no maximo 80 caracteres")
        String cabelo,
        @Size(max = 80, message = "barba com no maximo 80 caracteres")
        String barba,
        @Size(max = 120, message = "estilo com no maximo 120 caracteres")
        String estilo,
        @Size(max = 1000, message = "o que manter com no maximo 1000 caracteres")
        String manter,
        @Size(max = 1000, message = "o que evitar com no maximo 1000 caracteres")
        String evitar) {
}
