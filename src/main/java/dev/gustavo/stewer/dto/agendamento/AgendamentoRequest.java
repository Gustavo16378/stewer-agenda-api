package dev.gustavo.stewer.dto.agendamento;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Pedido de agendamento.
 *
 * <p>Quando quem chama e CLIENTE, o dono do agendamento e sempre o usuario do
 * token e {@code clienteId} e ignorado: aceitar o id pelo corpo deixaria um
 * cliente reservar em nome de outra pessoa. Profissional e ADMIN marcam para o
 * cliente que ligou ou mandou mensagem, e ai {@code clienteId} e obrigatorio.
 *
 * <p>O {@code fim} tambem nao vem do cliente: quem manda nele e a soma das
 * duracoes cadastradas nos servicos.
 *
 * <p>Servicos: {@code servicoIds} leva a lista, na ordem em que serao feitos.
 * {@code servicoId} sozinho continua aceito (app antigo); se vierem os dois, o
 * {@code servicoId} entra primeiro. Repetidos contam uma vez so.
 */
@ServicoInformado
@Schema(name = "AgendamentoRequest", description = "Dados para reservar um horario")
public record AgendamentoRequest(

        @NotNull(message = "profissionalId e obrigatorio")
        @Schema(description = "Profissional que vai atender")
        UUID profissionalId,

        @Schema(description = "Um servico so. Use servicoIds para marcar mais de um")
        UUID servicoId,

        @NotNull(message = "inicio e obrigatorio")
        @Schema(description = "Inicio do atendimento em ISO-8601 com offset",
                example = "2026-03-02T09:00:00Z")
        OffsetDateTime inicio,

        @Size(max = 2000, message = "observacao deve ter no maximo 2000 caracteres")
        @Schema(description = "Recado do cliente para o profissional")
        String observacao,

        @Schema(description = "Cliente do agendamento. Obrigatorio para PROFISSIONAL e ADMIN; "
                + "ignorado quando quem chama e CLIENTE")
        UUID clienteId,

        @Size(max = AgendamentoRequest.MAXIMO_DE_SERVICOS,
                message = "Marque no maximo " + AgendamentoRequest.MAXIMO_DE_SERVICOS + " servicos por vez")
        @Schema(description = "Servicos do atendimento, na ordem; a soma das duracoes define o fim")
        List<UUID> servicoIds) {

    public static final int MAXIMO_DE_SERVICOS = 6;

    /** {@code servicoId} e {@code servicoIds} juntos, sem nulo e sem repetido. */
    public List<UUID> servicosPedidos() {
        LinkedHashSet<UUID> ids = new LinkedHashSet<>();
        if (servicoId != null) {
            ids.add(servicoId);
        }
        if (servicoIds != null) {
            servicoIds.stream().filter(java.util.Objects::nonNull).forEach(ids::add);
        }
        return new ArrayList<>(ids);
    }
}
