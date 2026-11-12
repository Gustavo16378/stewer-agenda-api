package dev.gustavo.stewer.dto.auditoria;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "EventoAuditoria", description = "Quem fez o que e quando")
public record EventoAuditoriaResponse(
        UUID id,
        OffsetDateTime criadoEm,
        @Schema(description = "Nome de quem fez; nulo se foi o sistema") String autorNome,
        String autorRole,
        @Schema(examples = "CANCELOU") String acao,
        @Schema(examples = "AGENDAMENTO") String entidade,
        UUID entidadeId,
        UUID clienteId,
        @Schema(examples = "Cancelou Corte + Visagismo de Joana Ribeiro, sex 13/11 15:00") String resumo) {
}
