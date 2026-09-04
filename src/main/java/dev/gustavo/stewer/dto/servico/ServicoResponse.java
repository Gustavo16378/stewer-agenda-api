package dev.gustavo.stewer.dto.servico;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Servico do catalogo pronto para o front. */
@Schema(name = "Servico")
public record ServicoResponse(
        UUID id,
        UUID tenantId,
        String nome,
        String descricao,
        @Schema(description = "Duracao em minutos", example = "60")
        int duracaoMinutos,
        @Schema(description = "Preco em centavos", example = "25000")
        long precoCentavos,
        boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
