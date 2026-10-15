package dev.gustavo.stewer.service;

import java.util.UUID;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.auth.LoginRequest;
import dev.gustavo.stewer.dto.auth.RegistrarRequest;
import dev.gustavo.stewer.dto.auth.TokenResponse;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;
import dev.gustavo.stewer.exception.AutenticacaoException;
import dev.gustavo.stewer.exception.ConflitoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.UsuarioMapper;
import dev.gustavo.stewer.repository.UsuarioRepository;
import dev.gustavo.stewer.security.TokenService;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/** Cadastro e autenticacao. */
@ApplicationScoped
public class AuthService {

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    TenantService tenantService;

    @Inject
    TokenService tokenService;

    /** Auto-cadastro: sempre nasce como CLIENTE. Promocao de papel e coisa de ADMIN. */
    @Transactional
    public TokenResponse registrar(RegistrarRequest dto) {
        Tenant tenant = tenantService.resolver(dto.tenantSlug());
        if (!tenant.isAtivo()) {
            throw new RegraNegocioException("Estabelecimento inativo");
        }

        String email = normalizarEmail(dto.email());
        if (usuarioRepository.emailEmUso(tenant.getId(), email)) {
            throw new ConflitoException("EMAIL_EM_USO", "Ja existe uma conta com este e-mail");
        }

        Usuario usuario = new Usuario();
        usuario.setTenantId(tenant.getId());
        usuario.setNome(dto.nome().trim());
        usuario.setEmail(email);
        usuario.setTelefone(normalizarTelefone(dto.telefone()));
        usuario.setSenhaHash(BcryptUtil.bcryptHash(dto.senha()));
        usuario.setRole(Role.CLIENTE);

        // A checagem acima resolve o caso comum sem ir ao banco. Sob corrida,
        // duas transacoes passam as duas por ela e quem barra e o indice ux_usuario_tenant_email;
        // ViolacaoDeBanco traduz essa violacao no mesmo 409, no mapper -- aqui
        // nao da para capturar, porque o INSERT pode sair so no commit.
        usuarioRepository.persist(usuario);

        return montarToken(usuario);
    }

    public TokenResponse login(LoginRequest dto) {
        Tenant tenant = tenantService.resolver(dto.tenantSlug());
        Usuario usuario = usuarioRepository.porEmail(tenant.getId(), dto.email())
                // Mensagem unica para e-mail inexistente e senha errada: nao entregamos
                // ao atacante a informacao de quais e-mails existem.
                .orElseThrow(() -> new AutenticacaoException("E-mail ou senha invalidos"));

        if (!BcryptUtil.matches(dto.senha(), usuario.getSenhaHash())) {
            throw new AutenticacaoException("E-mail ou senha invalidos");
        }

        return montarToken(usuario);
    }

    /** Usuario do token atual, ja no escopo do tenant da sessao. */
    public UsuarioResponse porId(UUID tenantId, UUID usuarioId) {
        return usuarioRepository.buscarVivo(tenantId, usuarioId)
                .map(UsuarioMapper::paraResponse)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));
    }

    private TokenResponse montarToken(Usuario usuario) {
        return TokenResponse.de(
                tokenService.gerar(usuario),
                tokenService.validadeSegundos(),
                UsuarioMapper.paraResponse(usuario));
    }

    static String normalizarEmail(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }

    static String normalizarTelefone(String telefone) {
        if (telefone == null) {
            return null;
        }
        String limpo = telefone.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
