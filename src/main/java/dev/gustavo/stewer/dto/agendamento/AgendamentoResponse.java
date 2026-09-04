package dev.gustavo.stewer.dto.agendamento;

import java.time.OffsetDateTime;
import java.util.UUID;

import dev.gustavo.stewer.domain.StatusAgendamento;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Agendamento pronto para o front.
 *
 * <p>Os nomes vem desnormalizados de proposito: a tela de agenda e a de
 * historico do cliente precisam exibir "quem" e "o que" sem uma segunda
 * chamada, e nenhuma delas quer o objeto inteiro de servico ou profissional.
 */
@Schema(name = "Agendamento")
public record AgendamentoResponse(
        UUID id,
        UUID clienteId,
        String clienteNome,
        UUID profissionalId,
        String profissionalNome,
        UUID servicoId,
        String servicoNome,
        int duracaoMinutos,
        long precoCentavos,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        StatusAgendamento status,
        String observacao,
        OffsetDateTime createdAt) {
}
