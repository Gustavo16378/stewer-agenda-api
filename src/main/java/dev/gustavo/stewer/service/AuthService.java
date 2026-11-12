package dev.gustavo.stewer.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.AnotacaoCliente;
import dev.gustavo.stewer.domain.CodigoRedefinicaoSenha;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.auth.EsqueciSenhaRequest;
import dev.gustavo.stewer.dto.auth.LoginRequest;
import dev.gustavo.stewer.dto.auth.RedefinirSenhaRequest;
import dev.gustavo.stewer.dto.auth.RegistrarRequest;
import dev.gustavo.stewer.dto.auth.TokenResponse;
import dev.gustavo.stewer.dto.usuario.AtualizarPerfilRequest;
import dev.gustavo.stewer.dto.usuario.ExcluirContaRequest;
import dev.gustavo.stewer.dto.usuario.PreferenciasRequest;
import dev.gustavo.stewer.dto.usuario.TrocarSenhaRequest;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;
import dev.gustavo.stewer.email.EnvioDeEmail;
import dev.gustavo.stewer.email.Modelos;
import dev.gustavo.stewer.exception.AutenticacaoException;
import dev.gustavo.stewer.exception.ConflitoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.UsuarioMapper;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.AnotacaoClienteRepository;
import dev.gustavo.stewer.repository.CodigoRedefinicaoSenhaRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;
import dev.gustavo.stewer.security.FreioDeTentativas;
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

    @Inject
    FreioDeTentativas freio;

    @Inject
    CodigoRedefinicaoSenhaRepository codigoRepository;

    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    AnotacaoClienteRepository anotacaoRepository;

    @Inject
    EnvioDeEmail envioDeEmail;

    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final int VALIDADE_CODIGO_MINUTOS = 30;
    private static final int MAX_TENTATIVAS_CODIGO = 5;

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

        // A checagem acima resolve o caso comum sem ir ao banco. Sob corrida as
        // duas transacoes passam por ela, e quem barra e ux_usuario_tenant_email.
        // ViolacaoDeBanco traduz a violacao no mesmo 409 -- no mapper, porque o
        // INSERT pode sair so no commit e escapar de um try daqui.
        usuarioRepository.persist(usuario);

        return montarToken(usuario);
    }

    public TokenResponse login(LoginRequest dto) {
        Tenant tenant = tenantService.resolver(dto.tenantSlug());
        String chaveDoFreio = "login:" + tenant.getId() + ":" + normalizarEmail(dto.email());
        freio.exigirLiberado(chaveDoFreio);

        // Mensagem unica para e-mail inexistente e senha errada: nao entregamos
        // ao atacante a informacao de quais e-mails existem.
        Usuario usuario = usuarioRepository.porEmail(tenant.getId(), dto.email()).orElse(null);
        if (usuario == null || !BcryptUtil.matches(dto.senha(), usuario.getSenhaHash())) {
            freio.registrarFalha(chaveDoFreio);
            throw new AutenticacaoException("E-mail ou senha invalidos");
        }

        freio.zerar(chaveDoFreio);
        return montarToken(usuario);
    }

    /**
     * Manda um codigo de 6 digitos para o e-mail, se a conta existir.
     *
     * <p>Responde igual com conta ou sem conta, para nao virar um jeito de
     * descobrir quem e cliente do estudio. Pedir de novo invalida o anterior;
     * mais de 3 pedidos em 15 minutos para a mesma conta sao ignorados.
     */
    @Transactional
    public void esqueciSenha(EsqueciSenhaRequest dto) {
        Tenant tenant = tenantService.resolver(dto.tenantSlug());
        Usuario usuario = usuarioRepository.porEmail(tenant.getId(), dto.email()).orElse(null);
        if (usuario == null) {
            return;
        }
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (codigoRepository.pedidosDesde(tenant.getId(), usuario.getId(), agora.minusMinutes(15)) >= 3) {
            return;
        }

        String codigo = String.format("%06d", ALEATORIO.nextInt(1_000_000));
        CodigoRedefinicaoSenha registro = new CodigoRedefinicaoSenha();
        registro.setTenantId(tenant.getId());
        registro.setUsuario(usuario);
        registro.setCodigoHash(BcryptUtil.bcryptHash(codigo));
        registro.setExpiraEm(agora.plusMinutes(VALIDADE_CODIGO_MINUTOS));
        codigoRepository.persist(registro);

        Modelos.Email email = Modelos.codigoDeRedefinicao(tenant.getNome(), usuario.getNome(),
                codigo, VALIDADE_CODIGO_MINUTOS);
        envioDeEmail.enviar(usuario.getEmail(), email.assunto(), email.texto(), email.html());
    }

    /**
     * Troca a senha com o codigo do e-mail e ja devolve a sessao aberta. So o
     * codigo mais recente vale, uma vez, dentro do prazo e com ate 5 tentativas.
     */
    @Transactional(dontRollbackOn = RegraNegocioException.class)
    public TokenResponse redefinirSenha(RedefinirSenhaRequest dto) {
        Tenant tenant = tenantService.resolver(dto.tenantSlug());
        Usuario usuario = usuarioRepository.porEmail(tenant.getId(), dto.email())
                .orElseThrow(AuthService::codigoInvalido);
        CodigoRedefinicaoSenha registro = codigoRepository.maisRecente(tenant.getId(), usuario.getId())
                .orElseThrow(AuthService::codigoInvalido);

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (registro.getUsadoEm() != null || registro.getExpiraEm().isBefore(agora)
                || registro.getTentativas() >= MAX_TENTATIVAS_CODIGO) {
            throw codigoInvalido();
        }
        if (!BcryptUtil.matches(dto.codigo(), registro.getCodigoHash())) {
            registro.setTentativas(registro.getTentativas() + 1);
            codigoRepository.persist(registro);
            throw codigoInvalido();
        }

        registro.setUsadoEm(agora);
        codigoRepository.persist(registro);
        usuario.setSenhaHash(BcryptUtil.bcryptHash(dto.novaSenha()));
        usuarioRepository.persist(usuario);
        freio.zerar("login:" + tenant.getId() + ":" + usuario.getEmail());
        return montarToken(usuario);
    }

    @Transactional
    public UsuarioResponse atualizarPreferencias(UUID tenantId, UUID usuarioId, PreferenciasRequest dto) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        usuario.setLembreteEmail(dto.lembreteEmail());
        usuarioRepository.persistAndFlush(usuario);
        return UsuarioMapper.paraResponse(usuario);
    }

    /**
     * O cliente apaga a propria conta (exigencia das lojas e da LGPD).
     *
     * <p>Os horarios futuros sao cancelados para liberar a agenda, as anotacoes
     * de visagismo sobre ele sao apagadas, e os dados pessoais saem da linha do
     * usuario. O que fica e o historico anonimo de atendimentos passados, para a
     * receita do estudio nao mudar de tras para frente.
     *
     * <p>Conta da equipe nao se apaga por aqui: o admin remove o profissional
     * da equipe antes, para a agenda nao ficar orfa.
     */
    @Transactional
    public void excluirConta(UUID tenantId, UUID usuarioId, ExcluirContaRequest dto) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        if (usuario.getRole() != Role.CLIENTE) {
            throw new RegraNegocioException("CONTA_DA_EQUIPE",
                    "Contas da equipe sao removidas pelo administrador, em Equipe");
        }
        if (!BcryptUtil.matches(dto.senha(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("SENHA_ATUAL_INCORRETA", "A senha nao confere");
        }

        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        for (Agendamento a : agendamentoRepository.doClientePorPeriodo(tenantId, usuarioId, agora, true)) {
            if (a.getStatus() == StatusAgendamento.AGENDADO) {
                a.setStatus(StatusAgendamento.CANCELADO);
                agendamentoRepository.persist(a);
            }
        }
        for (AnotacaoCliente anotacao : anotacaoRepository.doCliente(tenantId, usuarioId)) {
            anotacaoRepository.softDelete(anotacao);
        }

        usuario.setNome("Conta excluída");
        usuario.setEmail("excluido-" + usuario.getId() + "@removido.invalid");
        usuario.setTelefone(null);
        usuario.setLembreteEmail(false);
        usuario.setSenhaHash(BcryptUtil.bcryptHash(java.util.UUID.randomUUID().toString()));
        usuarioRepository.softDelete(usuario);
    }

    /** Usuario do token atual, ja no escopo do tenant da sessao. */
    public UsuarioResponse porId(UUID tenantId, UUID usuarioId) {
        return usuarioRepository.buscarVivo(tenantId, usuarioId)
                .map(UsuarioMapper::paraResponse)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));
    }

    /**
     * Corrige nome, e-mail e telefone. Devolve um token novo porque nome e
     * e-mail viajam no token atual e a tela leria os antigos ate o proximo login.
     */
    @Transactional
    public TokenResponse atualizarPerfil(UUID tenantId, UUID usuarioId, AtualizarPerfilRequest dto) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);

        String email = normalizarEmail(dto.email());
        if (!email.equals(usuario.getEmail())
                && usuarioRepository.emailEmUso(tenantId, email)) {
            throw new ConflitoException("EMAIL_EM_USO", "Ja existe uma conta com este e-mail");
        }

        usuario.setNome(dto.nome().trim());
        usuario.setEmail(email);
        usuario.setTelefone(normalizarTelefone(dto.telefone()));
        usuarioRepository.persistAndFlush(usuario);
        return montarToken(usuario);
    }

    @Transactional
    public void trocarSenha(UUID tenantId, UUID usuarioId, TrocarSenhaRequest dto) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        if (!BcryptUtil.matches(dto.senhaAtual(), usuario.getSenhaHash())) {
            throw new RegraNegocioException("SENHA_ATUAL_INCORRETA", "A senha atual nao confere");
        }
        usuario.setSenhaHash(BcryptUtil.bcryptHash(dto.novaSenha()));
        usuarioRepository.persist(usuario);
    }

    /** Pessoas do estabelecimento, para o admin escolher quem vira profissional. */
    public List<UsuarioResponse> buscarPessoas(UUID tenantId, String busca, int pagina, int tamanho) {
        return usuarioRepository.buscarTodos(tenantId, busca, pagina, tamanho).stream()
                .map(UsuarioMapper::paraResponse)
                .toList();
    }

    /** Uma mensagem so para codigo errado, vencido, ja usado ou de conta inexistente. */
    private static RegraNegocioException codigoInvalido() {
        return new RegraNegocioException("CODIGO_INVALIDO",
                "Codigo invalido ou vencido. Peca um codigo novo");
    }

    private Usuario exigirVivo(UUID tenantId, UUID usuarioId) {
        return usuarioRepository.buscarVivo(tenantId, usuarioId)
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
