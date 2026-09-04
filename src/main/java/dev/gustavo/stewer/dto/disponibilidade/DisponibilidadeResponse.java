package dev.gustavo.stewer.dto.disponibilidade;

import java.time.LocalTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Uma faixa da grade semanal pronta para o front.
 *
 * <p>Nao carrega {@code tenantId}: a grade so e devolvida dentro do escopo do
 * token, entao repetir o tenant em cada faixa seria ruido.
 */
@Schema(name = "Disponibilidade", description = "Faixa de atendimento recorrente")
public record DisponibilidadeResponse(
        UUID id,
        UUID profissionalId,
        @Schema(description = "0 = domingo ... 6 = sabado", example = "1")
        short diaSemana,
        @Schema(type = SchemaType.STRING, format = "time", example = "09:00")
        LocalTime horaInicio,
        @Schema(type = SchemaType.STRING, format = "time", example = "18:00")
        LocalTime horaFim) {
}
