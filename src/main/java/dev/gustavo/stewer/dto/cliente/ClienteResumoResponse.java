package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Linha da lista de clientes do profissional.
 *
 * <p>De proposito nao carrega totais de agendamento: a listagem e paginada e
 * calcular isso por linha custaria uma consulta por cliente. Quando o
 * profissional quer numeros, ele abre o detalhe.
 */
@Schema(name = "ClienteResumo")
public record ClienteResumoResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        OffsetDateTime createdAt) {
}
