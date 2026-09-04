package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.AnotacaoCliente;
import dev.gustavo.stewer.dto.cliente.AnotacaoResponse;

/**
 * Mapeamento manual das anotacoes de visagismo.
 *
 * <p>Navega associacoes LAZY (cliente, profissional e o usuario do
 * profissional), entao so chame com uma sessao aberta -- na pratica, de
 * dentro de um metodo {@code @Transactional} do service.
 */
public final class AnotacaoMapper {

    private AnotacaoMapper() {
    }

    public static AnotacaoResponse paraResponse(AnotacaoCliente anotacao) {
        if (anotacao == null) {
            return null;
        }
        Agendamento agendamento = anotacao.getAgendamento();
        return new AnotacaoResponse(
                anotacao.getId(),
                anotacao.getCliente().getId(),
                anotacao.getProfissional().getId(),
                anotacao.getProfissional().getUsuario().getNome(),
                agendamento == null ? null : agendamento.getId(),
                anotacao.getTexto(),
                anotacao.getCreatedAt(),
                anotacao.getUpdatedAt());
    }

    public static List<AnotacaoResponse> paraResponses(List<AnotacaoCliente> anotacoes) {
        return anotacoes.stream().map(AnotacaoMapper::paraResponse).toList();
    }
}
