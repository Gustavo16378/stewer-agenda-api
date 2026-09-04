package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Anotacao pronta para o front, com o nome do autor ja resolvido para a
 * timeline nao precisar de uma segunda chamada por linha.
 * {@code agendamentoId} e nulo quando a anotacao e avulsa.
 */
@Schema(name = "Anotacao")
public record AnotacaoResponse(
        UUID id,
        UUID clienteId,
        UUID profissionalId,
        String profissionalNome,
        UUID agendamentoId,
        String texto,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
