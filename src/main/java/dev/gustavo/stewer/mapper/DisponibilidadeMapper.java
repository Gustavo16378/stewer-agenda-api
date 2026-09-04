package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Bloqueio;
import dev.gustavo.stewer.domain.Disponibilidade;
import dev.gustavo.stewer.dto.disponibilidade.BloqueioResponse;
import dev.gustavo.stewer.dto.disponibilidade.DisponibilidadeResponse;

/**
 * Mapeamento manual das duas faces da agenda: a grade semanal recorrente e os
 * bloqueios pontuais.
 *
 * <p>Os dois lados tocam {@code getProfissional()}, que e LAZY: chame sempre de
 * dentro de um metodo {@code @Transactional} do service, nunca do resource.
 *
 * <p>Os metodos de bloqueio tem nome proprio em vez de sobrecarregar
 * {@code paraResponse}/{@code paraResponses}: as versoes de lista colidiriam por
 * apagamento de generico, e meia sobrecarga so confundiria quem le.
 */
public final class DisponibilidadeMapper {

    private DisponibilidadeMapper() {
    }

    public static DisponibilidadeResponse paraResponse(Disponibilidade disponibilidade) {
        if (disponibilidade == null) {
            return null;
        }
        return new DisponibilidadeResponse(
                disponibilidade.getId(),
                disponibilidade.getProfissional().getId(),
                disponibilidade.getDiaSemana(),
                disponibilidade.getHoraInicio(),
                disponibilidade.getHoraFim());
    }

    public static List<DisponibilidadeResponse> paraResponses(List<Disponibilidade> grade) {
        return grade.stream().map(DisponibilidadeMapper::paraResponse).toList();
    }

    public static BloqueioResponse paraBloqueioResponse(Bloqueio bloqueio) {
        if (bloqueio == null) {
            return null;
        }
        return new BloqueioResponse(
                bloqueio.getId(),
                bloqueio.getProfissional().getId(),
                bloqueio.getInicio(),
                bloqueio.getFim(),
                bloqueio.getMotivo(),
                bloqueio.getCreatedAt());
    }

    public static List<BloqueioResponse> paraBloqueioResponses(List<Bloqueio> bloqueios) {
        return bloqueios.stream().map(DisponibilidadeMapper::paraBloqueioResponse).toList();
    }
}
