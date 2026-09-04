package dev.gustavo.stewer.mapper;

import java.util.List;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.profissional.ProfissionalResponse;

/**
 * Mapeamento manual entidade -> DTO.
 *
 * <p>Toca em {@code profissional.getUsuario()}, que e LAZY: chame sempre de
 * dentro de um metodo {@code @Transactional} do service, nunca do resource.
 */
public final class ProfissionalMapper {

    private ProfissionalMapper() {
    }

    public static ProfissionalResponse paraResponse(Profissional profissional) {
        if (profissional == null) {
            return null;
        }
        Usuario usuario = profissional.getUsuario();
        return new ProfissionalResponse(
                profissional.getId(),
                profissional.getTenantId(),
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                profissional.getEspecialidade(),
                profissional.getBio(),
                profissional.getFotoUrl(),
                profissional.isAtivo(),
                profissional.getCreatedAt(),
                profissional.getUpdatedAt());
    }

    public static List<ProfissionalResponse> paraResponses(List<Profissional> profissionais) {
        return profissionais.stream().map(ProfissionalMapper::paraResponse).toList();
    }
}
