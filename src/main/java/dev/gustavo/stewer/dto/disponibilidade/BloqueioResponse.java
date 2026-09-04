package dev.gustavo.stewer.dto.disponibilidade;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Bloqueio ja gravado, como a agenda do profissional o exibe. */
@Schema(name = "Bloqueio", description = "Janela em que o profissional nao atende")
public record BloqueioResponse(
        UUID id,
        UUID profissionalId,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        String motivo,
        OffsetDateTime createdAt) {
}
