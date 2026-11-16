package dev.gustavo.stewer.dto.agendamento;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.StatusAgendamento;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Agendamento pronto para o front.
 *
 * <p>Os nomes vem desnormalizados de proposito: a tela de agenda e a de
 * historico do cliente precisam exibir "quem" e "o que" sem uma segunda
 * chamada, e nenhuma delas quer o objeto inteiro de servico ou profissional.
 *
 * <p>Com mais de um servico: {@code servicoId} e o primeiro, {@code servicoNome}
 * junta todos ("Corte + Barba"), {@code duracaoMinutos} e {@code precoCentavos}
 * sao os totais e {@code servicos} traz cada um.
 */
@Schema(name = "Agendamento")
public record AgendamentoResponse(
        UUID id,
        UUID clienteId,
        String clienteNome,
        UUID profissionalId,
        String profissionalNome,
        @Schema(description = "O primeiro servico do atendimento")
        UUID servicoId,
        @Schema(description = "Todos os servicos, juntos com \" + \"", example = "Corte + Barba")
        String servicoNome,
        @Schema(description = "Tempo total reservado na agenda")
        int duracaoMinutos,
        long precoCentavos,
        OffsetDateTime inicio,
        OffsetDateTime fim,
        StatusAgendamento status,
        String observacao,
        OffsetDateTime createdAt,
        @Schema(description = "Na agenda do dia: o cliente ainda nao teve nenhum atendimento "
                + "concluido antes deste. Nulo fora da agenda.")
        Boolean primeiraVisita,
        @Schema(description = "Cada servico do atendimento, na ordem")
        List<Item> servicos) {

    @Schema(name = "ServicoDoAgendamento")
    public record Item(UUID servicoId, String nome, int duracaoMinutos, long precoCentavos) {
    }
}
