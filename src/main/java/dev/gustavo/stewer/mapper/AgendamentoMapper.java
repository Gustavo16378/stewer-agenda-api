package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.dto.agendamento.AgendamentoResponse;

public final class AgendamentoMapper {

    private AgendamentoMapper() {
    }

    public static AgendamentoResponse paraResponse(Agendamento agendamento) {
        return paraResponse(agendamento, null);
    }

    public static AgendamentoResponse paraResponse(Agendamento agendamento, Boolean primeiraVisita) {
        if (agendamento == null) {
            return null;
        }
        return new AgendamentoResponse(
                agendamento.getId(),
                agendamento.getCliente().getId(),
                agendamento.getCliente().getNome(),
                agendamento.getProfissional().getId(),
                agendamento.getProfissional().getUsuario().getNome(),
                agendamento.getServico().getId(),
                agendamento.nomeDosServicos(),
                (int) agendamento.duracao().toMinutes(),
                agendamento.precoEfetivo(),
                agendamento.getInicio(),
                agendamento.getFim(),
                agendamento.getStatus(),
                agendamento.getObservacao(),
                agendamento.getCreatedAt(),
                primeiraVisita,
                itens(agendamento));
    }

    /**
     * A lista de servicos. Agendamento sem lista (so possivel antes da V12
     * rodar) vira um item com o servico principal.
     */
    private static List<AgendamentoResponse.Item> itens(Agendamento agendamento) {
        if (agendamento.getServicos().isEmpty()) {
            return List.of(new AgendamentoResponse.Item(agendamento.getServico().getId(),
                    agendamento.getServico().getNome(), (int) agendamento.duracao().toMinutes(),
                    agendamento.precoEfetivo()));
        }
        return agendamento.getServicos().stream()
                .map(i -> new AgendamentoResponse.Item(i.getServico().getId(), i.getServico().getNome(),
                        i.getDuracaoMinutos(), i.getPrecoCentavos()))
                .toList();
    }

    public static List<AgendamentoResponse> paraResponses(List<Agendamento> agendamentos) {
        return agendamentos.stream().map(AgendamentoMapper::paraResponse).toList();
    }
}
