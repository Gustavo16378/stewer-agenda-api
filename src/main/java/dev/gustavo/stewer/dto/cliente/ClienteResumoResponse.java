package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Linha da lista de clientes do profissional.
 *
 * <p>Visitas e ultima visita saem de UMA consulta agregada para a pagina
 * inteira (nao uma por cliente).
 */
@Schema(name = "ClienteResumo")
public record ClienteResumoResponse(
        UUID id,
        String nome,
        String email,
        String telefone,
        OffsetDateTime createdAt,
        @Schema(description = "Atendimentos concluidos")
        long visitas,
        @Schema(description = "Inicio do ultimo atendimento concluido; nulo se nunca veio")
        OffsetDateTime ultimaVisita) {
}
