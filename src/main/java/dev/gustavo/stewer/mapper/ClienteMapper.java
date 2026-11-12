package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.domain.FichaCliente;
import dev.gustavo.stewer.dto.cliente.ClienteDetalheResponse;
import dev.gustavo.stewer.dto.cliente.FichaResponse;
import dev.gustavo.stewer.dto.cliente.MetricasClienteResponse;
import dev.gustavo.stewer.dto.cliente.ClienteResumoResponse;

/** Mapeamento manual da visao que o profissional tem do cliente. */
public final class ClienteMapper {

    private ClienteMapper() {
    }

    public static ClienteResumoResponse paraResumo(Usuario cliente, long visitas,
            java.time.OffsetDateTime ultimaVisita) {
        if (cliente == null) {
            return null;
        }
        return new ClienteResumoResponse(
                cliente.getId(),
                cliente.getNome(),
                cliente.getEmail(),
                cliente.getTelefone(),
                cliente.getCreatedAt(),
                visitas,
                ultimaVisita);
    }

    /** {@code visitas}: id do cliente -> [quantidade (Long), ultima (OffsetDateTime)]. */
    public static List<ClienteResumoResponse> paraResumos(List<Usuario> clientes,
            java.util.Map<java.util.UUID, Object[]> visitas) {
        return clientes.stream().map(c -> {
            Object[] v = visitas.get(c.getId());
            return paraResumo(c, v == null ? 0 : (Long) v[0],
                    v == null ? null : (java.time.OffsetDateTime) v[1]);
        }).toList();
    }

    /**
     * Monta a ficha completa. O historico chega pronto do service porque quem
     * decide a ordem (e o recorte) e a regra de negocio, nao o mapper.
     */
    public static ClienteDetalheResponse paraDetalhe(Usuario cliente, List<Agendamento> agendamentos,
            FichaResponse ficha, MetricasClienteResponse metricas) {
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
                AgendamentoMapper.paraResponses(agendamentos),
                ficha,
                metricas);
    }

    public static FichaResponse paraFicha(FichaCliente f) {
        if (f == null) {
            return FichaResponse.vazia();
        }
        return new FichaResponse(f.getDataNascimento(), f.getOrigem(), List.copyOf(f.getEtiquetas()),
                f.getFormatoRosto(), f.getCabelo(), f.getBarba(), f.getEstilo(), f.getManter(),
                f.getEvitar(), f.getUpdatedAt());
    }
}
