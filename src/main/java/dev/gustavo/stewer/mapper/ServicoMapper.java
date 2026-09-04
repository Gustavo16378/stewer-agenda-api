package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.dto.servico.ServicoResponse;

/** Mapeamento manual entidade -> DTO. Sem framework, sem surpresa. */
public final class ServicoMapper {

    private ServicoMapper() {
    }

    public static ServicoResponse paraResponse(Servico servico) {
        if (servico == null) {
            return null;
        }
        return new ServicoResponse(
                servico.getId(),
                servico.getTenantId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getDuracaoMinutos(),
                servico.getPrecoCentavos(),
                servico.isAtivo(),
                servico.getCreatedAt(),
                servico.getUpdatedAt());
    }

    public static List<ServicoResponse> paraResponses(List<Servico> servicos) {
        return servicos.stream().map(ServicoMapper::paraResponse).toList();
    }
}
