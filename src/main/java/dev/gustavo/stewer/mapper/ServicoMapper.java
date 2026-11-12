package dev.gustavo.stewer.mapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.dto.servico.ServicoResponse;

/** Mapeamento manual entidade -> DTO. Sem framework, sem surpresa. */
public final class ServicoMapper {

    private ServicoMapper() {
    }

    public static ServicoResponse paraResponse(Servico servico) {
        return paraResponse(servico, List.of());
    }

    public static ServicoResponse paraResponse(Servico servico, List<UUID> fotos) {
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
                servico.getUpdatedAt(),
                fotos == null ? List.of() : fotos);
    }

    public static List<ServicoResponse> paraResponses(List<Servico> servicos) {
        return servicos.stream().map(ServicoMapper::paraResponse).toList();
    }

    public static List<ServicoResponse> paraResponses(List<Servico> servicos, Map<UUID, List<UUID>> fotos) {
        return servicos.stream().map(s -> paraResponse(s, fotos.get(s.getId()))).toList();
    }
}
