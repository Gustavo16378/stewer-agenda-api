package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.cliente.ClienteDetalheResponse;
import dev.gustavo.stewer.dto.cliente.ClienteResumoResponse;

/** Mapeamento manual da visao que o profissional tem do cliente. */
public final class ClienteMapper {

    private ClienteMapper() {
    }

    public static ClienteResumoResponse paraResumo(Usuario cliente) {
        if (cliente == null) {
            return null;
        }
        return new ClienteResumoResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getCreatedAt());
    }

    public static List<ClienteResumoResponse> paraResumos(List<Usuario> clientes) {
        return clientes.stream().map(ClienteMapper::paraResumo).toList();
    }

    /**
     * Monta a ficha completa. O historico chega pronto do service porque quem
     * decide a ordem (e o recorte) e a regra de negocio, nao o mapper.
     */
    public static ClienteDetalheResponse paraDetalhe(Usuario cliente, List<Agendamento> agendamentos) {
        if (cliente == null) {
            return null;
        }
        return new ClienteDetalheResponse(
                cliente.getId(),
                cliente.getTenantId(),
                cliente.getNome(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getCreatedAt(),
                AgendamentoMapper.paraResponses(agendamentos));
    }
}
