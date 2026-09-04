package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.dto.agendamento.AgendamentoResponse;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Ficha do cliente com o historico completo de atendimentos, dos mais
 * recentes para os mais antigos. E a tela que o profissional abre antes de
 * atender: quem e a pessoa e o que ja foi feito nela.
 */
@Schema(name = "ClienteDetalhe")
public record ClienteDetalheResponse(
        UUID id,
        UUID tenantId,
        String nome,
        String email,
        String telefone,
        OffsetDateTime createdAt,
        List<AgendamentoResponse> agendamentos) {
}
