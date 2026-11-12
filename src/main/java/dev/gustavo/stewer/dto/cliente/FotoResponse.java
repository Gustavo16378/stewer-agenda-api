package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import dev.gustavo.stewer.domain.FotoCliente;

/** Dados de uma foto, sem a imagem (que vem por GET /fotos/{id}). */
@Schema(name = "Foto")
public record FotoResponse(
        UUID id,
        FotoCliente.Momento momento,
        String legenda,
        UUID agendamentoId,
        String tipoConteudo,
        int tamanhoBytes,
        String autorNome,
        OffsetDateTime createdAt) {
}
