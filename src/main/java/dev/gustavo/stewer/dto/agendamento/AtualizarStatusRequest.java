package dev.gustavo.stewer.dto.agendamento;

import dev.gustavo.stewer.domain.StatusAgendamento;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;

/**
 * Mudanca de status de um agendamento.
 *
 * <p>Quem pode ir para qual status depende do papel de quem chama: o cliente
 * so cancela, o profissional dono da agenda (e o ADMIN) tambem concluem ou
 * marcam falta. A regra vive no service, nao aqui.
 */
@Schema(name = "AtualizarStatusRequest", description = "Novo status do agendamento")
public record AtualizarStatusRequest(

        @NotNull(message = "status e obrigatorio")
        @Schema(description = "Situacao final desejada", example = "CANCELADO")
        StatusAgendamento status) {
}
