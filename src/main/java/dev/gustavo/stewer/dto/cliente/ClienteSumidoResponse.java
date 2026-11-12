package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Cliente que passou do intervalo normal dele sem voltar e sem horario marcado. */
@Schema(name = "ClienteSumido")
public record ClienteSumidoResponse(
        UUID id,
        String nome,
        String telefone,
        long visitas,
        OffsetDateTime ultimaVisita,
        @Schema(description = "Dias desde a ultima visita") int diasSemVir,
        @Schema(description = "Intervalo medio entre visitas; nulo com uma visita so")
        Integer intervaloHabitualDias) {
}
