package dev.gustavo.stewer.dto.disponibilidade;

import java.time.OffsetDateTime;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Indisponibilidade pontual do profissional: folga, curso, compromisso pessoal.
 *
 * <p>O bloqueio vale para um instante no tempo (nao para um horario recorrente),
 * por isso {@link OffsetDateTime} com fuso -- o servidor guarda tudo em UTC.
 * A regra {@code fim > inicio} vive no service: e regra de dominio, e o 422 dela
 * e mais informativo que um 400 de campo.
 */
@Schema(name = "BloqueioRequest", description = "Janela em que o profissional nao atende")
public record BloqueioRequest(

        @NotNull(message = "inicio e obrigatorio")
        @Schema(type = SchemaType.STRING, format = "date-time", example = "2026-03-10T13:00:00Z")
        OffsetDateTime inicio,

        @NotNull(message = "fim e obrigatorio")
        @Schema(type = SchemaType.STRING, format = "date-time", example = "2026-03-10T15:00:00Z")
        OffsetDateTime fim,

        @Size(max = 240, message = "motivo deve ter no maximo 240 caracteres")
        @Schema(example = "Consulta medica")
        String motivo) {
}
