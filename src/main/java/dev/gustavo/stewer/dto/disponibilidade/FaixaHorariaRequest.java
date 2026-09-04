package dev.gustavo.stewer.dto.disponibilidade;

import java.time.LocalTime;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Uma faixa de atendimento dentro de um dia da semana.
 *
 * <p>A hora e documentada com {@code @Schema(type = STRING, format = "time")} e
 * NAO com {@code @JsonFormat(pattern = "HH:mm")}: o padrao fixo faria o Jackson
 * recusar "09:00:00" -- justamente o formato que outro cliente (ou um round-trip
 * da propria API) pode mandar de volta. Com o desserializador ISO padrao os dois
 * formatos entram, e o Swagger continua exibindo o exemplo curto.
 */
@Schema(name = "FaixaHoraria", description = "Faixa de atendimento de um dia da semana")
public record FaixaHorariaRequest(

        @NotNull(message = "diaSemana e obrigatorio")
        @Min(value = 0, message = "diaSemana vai de 0 (domingo) a 6 (sabado)")
        @Max(value = 6, message = "diaSemana vai de 0 (domingo) a 6 (sabado)")
        @Schema(description = "0 = domingo ... 6 = sabado", example = "1")
        Integer diaSemana,

        @NotNull(message = "horaInicio e obrigatorio")
        @Schema(type = SchemaType.STRING, format = "time", example = "09:00")
        LocalTime horaInicio,

        @NotNull(message = "horaFim e obrigatorio")
        @Schema(type = SchemaType.STRING, format = "time", example = "18:00")
        LocalTime horaFim) {
}
