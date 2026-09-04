package dev.gustavo.stewer.service;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.profissional.ProfissionalRequest;
import dev.gustavo.stewer.dto.profissional.ProfissionalResponse;
import dev.gustavo.stewer.exception.ConflitoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.ProfissionalMapper;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Regras da equipe que atende.
 *
 * <p>Ate a leitura roda em transacao: o DTO carrega nome/e-mail/telefone do
 * {@code Usuario}, que e {@code LAZY} no {@code Profissional}. Montar a
 * resposta fora da transacao levantaria LazyInitializationException.
 */
@ApplicationScoped
public class ProfissionalService {

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    /**
     * Equipe do estabelecimento, ordenada pelo nome do usuario.
     *
     * @param incluirInativos ja deve chegar decidido pelo resource: quem nao e
     *                        ADMIN so enxerga profissional ativo.
     */
    @Transactional
    public List<ProfissionalResponse> listar(UUID tenantId, boolean incluirInativos) {
        return ProfissionalMapper.paraResponses(
                profissionalRepository.listar(tenantId, !incluirInativos));
    }

    @Transactional
    public ProfissionalResponse porId(UUID tenantId, UUID id) {
        return ProfissionalMapper.paraResponse(buscar(tenantId, id));
    }

    /** Promove um usuario existente a profissional. So ADMIN chega aqui. */
    @Transactional
    public ProfissionalResponse criar(UUID tenantId, ProfissionalRequest dto) {
        Usuario usuario = usuarioRepository.buscarVivo(tenantId, dto.usuarioId())
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", dto.usuarioId()));

        if (profissionalRepository.porUsuario(tenantId, usuario.getId()).isPresent()) {
            throw new ConflitoException("JA_E_PROFISSIONAL",
                    "Este usuario ja tem ficha de profissional");
        }

        // A permissao de mexer na propria agenda vem da role do token, nao da
        // existencia da ficha. Sem promover aqui, o recem-cadastrado continuaria
        // com acesso de CLIENTE ate alguem lembrar de trocar a role na mao.
        // ADMIN fica como esta: rebaixa-lo a PROFISSIONAL tiraria acesso dele.
        if (usuario.getRole() == Role.CLIENTE) {
            usuario.setRole(Role.PROFISSIONAL);
            usuarioRepository.persist(usuario);
        }

        Profissional profissional = new Profissional();
        profissional.setTenantId(tenantId);
        profissional.setUsuario(usuario);
        profissional.setEspecialidade(normalizar(dto.especialidade()));
        profissional.setBio(normalizar(dto.bio()));
        profissional.setFotoUrl(normalizar(dto.fotoUrl()));
        profissional.setAtivo(dto.ativo() == null || dto.ativo());
        profissionalRepository.persist(profissional);

        return ProfissionalMapper.paraResponse(profissional);
    }

    @Transactional
    public ProfissionalResponse atualizar(UUID tenantId, UUID id, ProfissionalRequest dto) {
        Profissional profissional = buscar(tenantId, id);

        // Trocar o usuario dono da ficha faria outra pessoa herdar agenda, bloqueios
        // e anotacoes de quem estava ali antes. Recusamos em vez de ignorar o campo
        // calado: um PUT que devolve 200 sem aplicar o que foi mandado esconde o erro
        // do front. Para trocar de pessoa, apague a ficha e crie outra.
        if (!profissional.getUsuario().getId().equals(dto.usuarioId())) {
            throw new RegraNegocioException("USUARIO_NAO_ALTERAVEL",
                    "Nao e possivel trocar o usuario de um profissional ja cadastrado");
        }

        profissional.setEspecialidade(normalizar(dto.especialidade()));
        profissional.setBio(normalizar(dto.bio()));
        profissional.setFotoUrl(normalizar(dto.fotoUrl()));
        if (dto.ativo() != null) {
            profissional.setAtivo(dto.ativo());
        }
        profissionalRepository.persist(profissional);

        return ProfissionalMapper.paraResponse(profissional);
    }

    /**
     * Soft delete. A role do usuario e mantida de proposito: ele pode continuar
     * precisando enxergar o historico de atendimentos que ja fez.
     */
    @Transactional
    public void remover(UUID tenantId, UUID id) {
        profissionalRepository.softDelete(buscar(tenantId, id));
    }

    private Profissional buscar(UUID tenantId, UUID id) {
        return profissionalRepository.buscarVivo(tenantId, id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Profissional", id));
    }

    /** Texto opcional em branco vira null: evita string vazia no banco. */
    private static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
