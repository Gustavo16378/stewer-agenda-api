package dev.gustavo.stewer.service;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.UsuarioMapper;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Quem administra o estabelecimento. O admin da acesso de admin a outra pessoa e tira,
 * pelo app, sem ninguem precisar mexer no banco.
 *
 * <p>Duas travas para ninguem ficar trancado do lado de fora: ninguem tira o proprio
 * acesso, e o ultimo admin nao pode ser rebaixado.
 *
 * <p>O papel vai dentro do token. Quem ganha ou perde o acesso passa a valer no proximo
 * login (o app percebe a mudanca ao abrir e pede para entrar de novo); um token antigo
 * vence sozinho em ate 8 horas.
 */
@ApplicationScoped
public class AcessoService {

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    Auditoria auditoria;

    public List<UsuarioResponse> administradores(UUID tenantId) {
        return usuarioRepository.administradores(tenantId).stream()
                .map(UsuarioMapper::paraResponse)
                .toList();
    }

    @Transactional
    public UsuarioResponse darAcesso(UUID tenantId, UUID usuarioId) {
        Usuario pessoa = exigirVivo(tenantId, usuarioId);
        if (pessoa.getRole() == Role.ADMIN) {
            return UsuarioMapper.paraResponse(pessoa);
        }
        pessoa.setRole(Role.ADMIN);
        usuarioRepository.persistAndFlush(pessoa);
        auditoria.registrar(tenantId, Auditoria.Acao.DEU_ACESSO, Auditoria.Entidade.ACESSO,
                pessoa.getId(), null, "Deu acesso de admin para " + pessoa.getNome());
        return UsuarioMapper.paraResponse(pessoa);
    }

    /**
     * Volta a ser o que era sem o admin: profissional, se ainda tem ficha de quem atende,
     * ou cliente.
     */
    @Transactional
    public UsuarioResponse tirarAcesso(UUID tenantId, UUID quemPede, UUID usuarioId) {
        Usuario pessoa = exigirVivo(tenantId, usuarioId);
        if (pessoa.getRole() != Role.ADMIN) {
            return UsuarioMapper.paraResponse(pessoa);
        }
        if (pessoa.getId().equals(quemPede)) {
            throw new RegraNegocioException("PROPRIO_ACESSO",
                    "Você não pode tirar o seu próprio acesso. Peça para outro admin.");
        }
        if (usuarioRepository.contarAdministradores(tenantId) <= 1) {
            throw new RegraNegocioException("ULTIMO_ADMIN",
                    "Precisa ficar pelo menos um admin.");
        }
        boolean atende = profissionalRepository.porUsuario(tenantId, pessoa.getId())
                .filter(p -> p.isAtivo())
                .isPresent();
        pessoa.setRole(atende ? Role.PROFISSIONAL : Role.CLIENTE);
        usuarioRepository.persistAndFlush(pessoa);
        auditoria.registrar(tenantId, Auditoria.Acao.TIROU_ACESSO, Auditoria.Entidade.ACESSO,
                pessoa.getId(), null, "Tirou o acesso de admin de " + pessoa.getNome());
        return UsuarioMapper.paraResponse(pessoa);
    }

    private Usuario exigirVivo(UUID tenantId, UUID usuarioId) {
        return usuarioRepository.buscarVivo(tenantId, usuarioId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));
    }
}
