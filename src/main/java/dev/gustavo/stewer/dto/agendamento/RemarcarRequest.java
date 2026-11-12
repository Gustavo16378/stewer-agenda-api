package dev.gustavo.stewer.dto.agendamento;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * Novo horario para um agendamento que ainda vai acontecer.
 *
 * <p>Remarcar e uma operacao so, e nao cancelar + criar: entre as duas chamadas
 * outro cliente poderia ficar com o horario antigo e o novo, e o cliente sairia
 * sem nenhum dos dois.
 */
@Schema(name = "RemarcarRequest", description = "Novo horario do agendamento")
public record RemarcarRequest(

        @NotNull(message = "inicio e obrigatorio")
        @Schema(description = "Novo inicio em ISO-8601 com offset", example = "2026-03-02T10:00:00Z")
        OffsetDateTime inicio,

        @Schema(description = "Outro profissional para atender; omitido, continua o mesmo")
        UUID profissionalId) {
}
