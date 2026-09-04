package dev.gustavo.stewer.dto.agendamento;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de agendamento feito pelo cliente.
 *
 * <p>Nao existe campo {@code clienteId} de proposito: o dono do agendamento e
 * sempre o usuario do token. Aceitar o id pelo corpo deixaria qualquer cliente
 * reservar horario em nome de outra pessoa.
 *
 * <p>O {@code fim} tambem nao vem do cliente: quem manda nele e a duracao
 * cadastrada no servico.
 */
@Schema(name = "AgendamentoRequest", description = "Dados para reservar um horario")
public record AgendamentoRequest(

        @NotNull(message = "profissionalId e obrigatorio")
        @Schema(description = "Profissional que vai atender")
        UUID profissionalId,

        @NotNull(message = "servicoId e obrigatorio")
        @Schema(description = "Servico desejado; a duracao dele define o fim do atendimento")
        UUID servicoId,

        @NotNull(message = "inicio e obrigatorio")
        @Schema(description = "Inicio do atendimento em ISO-8601 com offset",
                example = "2026-03-02T09:00:00Z")
        OffsetDateTime inicio,

        @Size(max = 2000, message = "observacao deve ter no maximo 2000 caracteres")
        @Schema(description = "Recado do cliente para o profissional")
        String observacao) {
}
