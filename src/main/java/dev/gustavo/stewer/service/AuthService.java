package dev.gustavo.stewer.service;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.AnotacaoCliente;
import dev.gustavo.stewer.domain.CodigoRedefinicaoSenha;
import dev.gustavo.stewer.domain.Dispositivo;
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
import dev.gustavo.stewer.repository.DispositivoRepository;
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
    dev.gustavo.stewer.security.UsuarioLogado usuarioLogado;

    @Inject
    dev.gustavo.stewer.repository.EventoAuditoriaRepository eventoAuditoriaRepository;

    @Inject
    dev.gustavo.stewer.repository.FotoClienteRepository fotoRepository;

    @Inject
    dev.gustavo.stewer.repository.FichaClienteRepository fichaRepository;

    @Inject
    Auditoria auditoria;

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

    @Inject
    DispositivoRepository dispositivoRepository;

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
        guardarNascimento(tenant.getId(), usuario, dto.dataNascimento());
        auditoria.registrarComo(tenant.getId(), usuario.getId(), usuario.getNome(), Role.CLIENTE.name(),
                Auditoria.Acao.CADASTROU, Auditoria.Entidade.CLIENTE, usuario.getId(), usuario.getId(),
                usuario.getNome() + " criou a conta pelo app");

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

        dispositivoRepository.vivos(tenantId, "usuario.id = :u",
                io.quarkus.panache.common.Parameters.with("u", usuarioId))
                .list().forEach(dispositivoRepository::softDelete);

        // LGPD: com a conta vai embora o que a equipe guardou sobre a pessoa
        // (ficha e fotos), e o nome dela sai das linhas da auditoria.
        fichaRepository.doCliente(tenantId, usuarioId).ifPresent(fichaRepository::softDelete);
        fotoRepository.doCliente(tenantId, usuarioId).forEach(fotoRepository::softDelete);
        eventoAuditoriaRepository.anonimizarCliente(tenantId, usuarioId, usuario.getNome());
        auditoria.registrarComo(tenantId, null, null, Role.CLIENTE.name(), Auditoria.Acao.EXCLUIU_CONTA,
                Auditoria.Entidade.CLIENTE, usuarioId, usuarioId, "Um cliente excluiu a própria conta");

        usuario.setNome("Conta excluída");
        usuario.setEmail("excluido-" + usuario.getId() + "@removido.invalid");
        usuario.setTelefone(null);
        usuario.setLembreteEmail(false);
        usuario.setSenhaHash(BcryptUtil.bcryptHash(java.util.UUID.randomUUID().toString()));
        usuarioRepository.softDelete(usuario);
    }

    /** Usuario do token atual, ja no escopo do tenant da sessao. */
    public UsuarioResponse porId(UUID tenantId, UUID usuarioId) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        return UsuarioMapper.paraResponse(usuario, nascimentoDe(usuario));
    }

    /** O aniversario mora na ficha (que a equipe tambem edita). So cliente tem ficha. */
    private java.time.LocalDate nascimentoDe(Usuario usuario) {
        if (usuario.getRole() != Role.CLIENTE) {
            return null;
        }
        return fichaRepository.doCliente(usuario.getTenantId(), usuario.getId())
                .map(dev.gustavo.stewer.domain.FichaCliente::getDataNascimento)
                .orElse(null);
    }

    /** Nulo nao mexe: o campo e opcional e quem nao informa nao apaga o que a equipe pos. */
    private void guardarNascimento(UUID tenantId, Usuario usuario, java.time.LocalDate data) {
        if (data == null || usuario.getRole() != Role.CLIENTE) {
            return;
        }
        if (data.getYear() < 1900) {
            throw new RegraNegocioException("NASCIMENTO_INVALIDO", "Confira o ano de nascimento");
        }
        dev.gustavo.stewer.domain.FichaCliente ficha = fichaRepository.doCliente(tenantId, usuario.getId())
                .orElseGet(() -> {
                    dev.gustavo.stewer.domain.FichaCliente nova = new dev.gustavo.stewer.domain.FichaCliente();
                    nova.setTenantId(tenantId);
                    nova.setCliente(usuario);
                    return nova;
                });
        ficha.setDataNascimento(data);
        fichaRepository.persist(ficha);
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
        guardarNascimento(tenantId, usuario, dto.dataNascimento());
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

    /** Liga o celular ao usuario logado; se o token era de outra pessoa, passa a ser deste. */
    @Transactional
    public void registrarDispositivo(UUID tenantId, UUID usuarioId, String token) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        Dispositivo d = dispositivoRepository.porToken(token).orElseGet(Dispositivo::new);
        if (d.getId() != null && !d.getTenantId().equals(tenantId)) {
            dispositivoRepository.softDelete(d);
            d = new Dispositivo();
        }
        d.setTenantId(tenantId);
        d.setUsuario(usuario);
        d.setToken(token);
        dispositivoRepository.persist(d);
    }

    /** Ao sair da conta: aquele celular para de receber avisos dela. */
    @Transactional
    public void removerDispositivo(UUID tenantId, UUID usuarioId, String token) {
        dispositivoRepository.porToken(token)
                .filter(d -> d.getTenantId().equals(tenantId) && d.getUsuario().getId().equals(usuarioId))
                .ifPresent(dispositivoRepository::softDelete);
    }

    private Usuario exigirVivo(UUID tenantId, UUID usuarioId) {
        return usuarioRepository.buscarVivo(tenantId, usuarioId)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Usuario", usuarioId));
    }

    private TokenResponse montarToken(Usuario usuario) {
        return TokenResponse.de(
                tokenService.gerar(usuario),
                tokenService.validadeSegundos(),
                UsuarioMapper.paraResponse(usuario, nascimentoDe(usuario)));
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

    /** Teto da foto de perfil ja decodificada. */
    static final int TAMANHO_MAXIMO_FOTO_PERFIL = 600_000;

    @Transactional
    public UsuarioResponse trocarFoto(UUID tenantId, UUID usuarioId,
            dev.gustavo.stewer.dto.usuario.FotoPerfilRequest dto) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        ImagemRecebida.Imagem imagem = ImagemRecebida.decodificar(dto.tipoConteudo(), dto.conteudoBase64(),
                TAMANHO_MAXIMO_FOTO_PERFIL, "600 KB");
        usuario.setFoto(imagem.bytes());
        usuario.setFotoTipo(imagem.tipo());
        usuario.setFotoAtualizadaEm(OffsetDateTime.now(ZoneOffset.UTC));
        usuarioRepository.persistAndFlush(usuario);
        return UsuarioMapper.paraResponse(usuario);
    }

    @Transactional
    public UsuarioResponse removerFoto(UUID tenantId, UUID usuarioId) {
        Usuario usuario = exigirVivo(tenantId, usuarioId);
        usuario.setFoto(null);
        usuario.setFotoTipo(null);
        usuario.setFotoAtualizadaEm(null);
        usuarioRepository.persistAndFlush(usuario);
        return UsuarioMapper.paraResponse(usuario);
    }

    /**
     * A foto de alguem do estabelecimento. A equipe ve qualquer uma; o cliente ve a
     * propria e a de quem atende (para escolher com quem marcar), nunca a de outro
     * cliente. Sem foto, ou sem permissao, e 404: nao confirma nada.
     */
    @Transactional
    public dev.gustavo.stewer.dto.cliente.FotoConteudoResponse fotoDe(UUID tenantId, UUID usuarioId) {
        Usuario alvo = usuarioRepository.buscarVivo(tenantId, usuarioId)
                .filter(u -> u.getFotoAtualizadaEm() != null)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Foto", usuarioId));
        boolean clientePedindo = usuarioLogado.role() == Role.CLIENTE;
        if (clientePedindo && alvo.getRole() == Role.CLIENTE && !alvo.getId().equals(usuarioLogado.usuarioId())) {
            throw RecursoNaoEncontradoException.de("Foto", usuarioId);
        }
        return new dev.gustavo.stewer.dto.cliente.FotoConteudoResponse(alvo.getId(),
                ImagemRecebida.dataUrl(alvo.getFotoTipo(), alvo.getFoto()));
    }
}
