package dev.gustavo.stewer.dto.cliente;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "Ficha", description = "Dados e perfil de visagismo do cliente; vazia se ninguem preencheu")
public record FichaResponse(
        LocalDate dataNascimento,
        String origem,
        List<String> etiquetas,
        String formatoRosto,
        String cabelo,
        String barba,
        String estilo,
        String manter,
        String evitar,
        @Schema(description = "Ultima vez que alguem salvou; nulo se nunca foi preenchida")
        OffsetDateTime atualizadaEm) {

    public static FichaResponse vazia() {
        return new FichaResponse(null, null, List.of(), null, null, null, null, null, null, null);
    }
}
