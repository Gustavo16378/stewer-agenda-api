package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.dto.agendamento.AgendamentoResponse;

public final class AgendamentoMapper {

    private AgendamentoMapper() {
    }

    public static AgendamentoResponse paraResponse(Agendamento agendamento) {
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
                agendamento.getServico().getNome(),
                agendamento.getServico().getDuracaoMinutos(),
                agendamento.getServico().getPrecoCentavos(),
                agendamento.getInicio(),
                agendamento.getFim(),
                agendamento.getStatus(),
                agendamento.getObservacao(),
                agendamento.getCreatedAt());
    }

    public static List<AgendamentoResponse> paraResponses(List<Agendamento> agendamentos) {
        return agendamentos.stream().map(AgendamentoMapper::paraResponse).toList();
    }
}
