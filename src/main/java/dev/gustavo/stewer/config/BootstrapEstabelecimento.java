package dev.gustavo.stewer.config;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import dev.gustavo.stewer.domain.Disponibilidade;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.repository.DisponibilidadeRepository;
import dev.gustavo.stewer.repository.ServicoRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.TenantRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;

import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Deixa um banco recem-criado utilizavel.
 *
 * <p>Em producao o Flyway so aplica {@code db/migration}, que e DDL puro. O
 * estabelecimento nascia apenas no seed de dev, que nunca vai para producao:
 * a API subia verde e o primeiro usuario recebia 404 "Estabelecimento nao
 * encontrado" no cadastro e no login.
 *
 * <p><strong>Por que aqui e nao numa migration:</strong> migration nao e lugar
 * de credencial. O hash teria de estar escrito no arquivo versionado -- como
 * acontece no seed de dev, cuja senha todo mundo conhece. Aqui os valores vem
 * de variavel de ambiente, ficam fora do repositorio, e podem ser removidas
 * depois do primeiro boot sem quebrar nada.
 *
 * <p><strong>Idempotente:</strong> so cria o que nao existe, comparando por
 * slug e por e-mail. Rodar em toda subida e inofensivo, inclusive com varias
 * instancias (a unicidade final e garantida pelos indices do banco).
 *
 * <p>Nao sobrescreve nada: se o estabelecimento ja existe, o fuso e o nome
 * configurados sao ignorados. Mudar esses valores depois e operacao de banco,
 * nao efeito colateral de reiniciar a aplicacao.
 */
@ApplicationScoped
public class BootstrapEstabelecimento {

    private static final Logger LOG = Logger.getLogger(BootstrapEstabelecimento.class);

    /** Minimo que a API ja exige no cadastro; vale tambem para o admin inicial. */
    private static final int SENHA_MINIMA = 8;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    DisponibilidadeRepository disponibilidadeRepository;

    @Inject
    ServicoRepository servicoRepository;

    /**
     * Estudio novo nasce com o catalogo do Stewer, para dar para marcar desde o primeiro
     * minuto. So age se o estudio NUNCA teve servico (contando os removidos): apagar o
     * catalogo de proposito e reiniciar a API nao o traz de volta.
     */
    @ConfigProperty(name = "stewer.bootstrap.catalogo-inicial", defaultValue = "true")
    boolean catalogoInicial;

    /** nome, descricao, minutos, centavos */
    private static final List<Object[]> CATALOGO_INICIAL = List.of(
            new Object[] { "Consultoria de visagismo",
                    "Primeiro atendimento: leitura do rosto, do estilo e do objetivo, e o plano de corte "
                            + "e barba que vai guiar as manutenções.", 90, 25000L },
            new Object[] { "Corte (manutenção)",
                    "Manutenção do corte definido na consultoria.", 60, 9000L },
            new Object[] { "Manutenção de barba",
                    "Manutenção do desenho da barba.", 30, 7000L });

    @ConfigProperty(name = "stewer.bootstrap.habilitado", defaultValue = "true")
    boolean habilitado;

    /** Por padrao o mesmo slug que os endpoints publicos resolvem. */
    @ConfigProperty(name = "stewer.bootstrap.tenant.slug")
    Optional<String> slugConfigurado;

    @ConfigProperty(name = "stewer.tenant.slug-padrao")
    String slugPadrao;

    @ConfigProperty(name = "stewer.bootstrap.tenant.nome", defaultValue = "Estabelecimento")
    String nomeDoTenant;

    @ConfigProperty(name = "stewer.bootstrap.tenant.fuso")
    Optional<String> fusoConfigurado;

    @ConfigProperty(name = "stewer.bootstrap.admin.email")
    Optional<String> adminEmail;

    @ConfigProperty(name = "stewer.bootstrap.admin.nome", defaultValue = "Administrador")
    String adminNome;

    @ConfigProperty(name = "stewer.bootstrap.admin.senha")
    Optional<String> adminSenha;

    /**
     * O dono do estudio tambem atende. Sem ficha de profissional nenhum cliente
     * consegue agendar, e criar a ficha pelo app exigiria alguem que ja seja
     * profissional ou admin com acesso a lista de usuarios.
     */
    @ConfigProperty(name = "stewer.bootstrap.admin.profissional", defaultValue = "true")
    boolean adminTambemAtende;

    @ConfigProperty(name = "stewer.bootstrap.admin.especialidade")
    Optional<String> adminEspecialidade;

    /** Grade inicial do dono: segunda a sabado, 09:00-19:00. Ele ajusta pelo app. */
    private static final List<DayOfWeek> DIAS_PADRAO = List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY);
    private static final LocalTime ABRE = LocalTime.of(9, 0);
    private static final LocalTime FECHA = LocalTime.of(19, 0);

    void aoSubir(@Observes StartupEvent evento) {
        executar();
    }

    /**
     * Cria o que faltar. Visivel para que um teste possa exercitar a regra sem
     * depender da ordem de inicializacao.
     */
    @Transactional
    public void executar() {
        if (!habilitado) {
            return;
        }

        String slug = slugConfigurado.filter(s -> !s.isBlank()).orElse(slugPadrao);
        Tenant tenant = tenantRepository.porSlug(slug).orElseGet(() -> criarTenant(slug));
        if (catalogoInicial) {
            criarCatalogoSeNuncaTeve(tenant);
        }

        adminEmail
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .ifPresent(email -> {
                    criarAdminSeFaltar(tenant, email);
                    if (adminTambemAtende) {
                        criarFichaDoDonoSeFaltar(tenant, email);
                    }
                });
    }

    private void criarCatalogoSeNuncaTeve(Tenant tenant) {
        if (servicoRepository.count("tenantId", tenant.getId()) > 0) {
            return;
        }
        for (Object[] item : CATALOGO_INICIAL) {
            Servico servico = new Servico();
            servico.setTenantId(tenant.getId());
            servico.setNome((String) item[0]);
            servico.setDescricao((String) item[1]);
            servico.setDuracaoMinutos((Integer) item[2]);
            servico.setPrecoCentavos((Long) item[3]);
            servico.setAtivo(true);
            servicoRepository.persist(servico);
        }
        LOG.infof("Catalogo inicial criado com %d servicos", CATALOGO_INICIAL.size());
    }

    /**
     * So age num estudio sem nenhum profissional: se o dono removeu a propria
     * ficha de proposito, reiniciar a API nao a traz de volta.
     */
    private void criarFichaDoDonoSeFaltar(Tenant tenant, String email) {
        if (!profissionalRepository.listar(tenant.getId(), false).isEmpty()) {
            return;
        }
        Usuario dono = usuarioRepository.porEmail(tenant.getId(), email).orElse(null);
        if (dono == null) {
            return;
        }

        Profissional ficha = new Profissional();
        ficha.setTenantId(tenant.getId());
        ficha.setUsuario(dono);
        ficha.setEspecialidade(adminEspecialidade.map(String::trim).filter(e -> !e.isEmpty())
                .orElse(null));
        ficha.setAtivo(true);
        profissionalRepository.persist(ficha);

        for (DayOfWeek dia : DIAS_PADRAO) {
            Disponibilidade faixa = new Disponibilidade();
            faixa.setTenantId(tenant.getId());
            faixa.setProfissional(ficha);
            faixa.setDiaSemana(Disponibilidade.toDiaSemana(dia));
            faixa.setHoraInicio(ABRE);
            faixa.setHoraFim(FECHA);
            disponibilidadeRepository.persist(faixa);
        }

        LOG.infof("Ficha de profissional de '%s' criada, atendendo de segunda a sabado, 09:00-19:00.",
                email);
    }

    private Tenant criarTenant(String slug) {
        ZoneId fuso = resolverFuso();

        Tenant tenant = new Tenant();
        tenant.setNome(nomeDoTenant);
        tenant.setSlug(slug);
        tenant.setAtivo(true);
        tenant.setFuso(fuso.getId());
        tenantRepository.persist(tenant);

        LOG.infof("Estabelecimento '%s' (slug '%s', fuso %s) criado na inicializacao.",
                nomeDoTenant, slug, fuso);
        return tenant;
    }

    /**
     * O fuso errado reintroduz o bug dos horarios deslocados, entao um valor
     * invalido derruba o boot em vez de virar UTC silenciosamente.
     */
    private ZoneId resolverFuso() {
        String valor = fusoConfigurado.map(String::trim).filter(s -> !s.isEmpty()).orElse(null);
        if (valor == null) {
            return Tenant.FUSO_PADRAO;
        }
        try {
            return ZoneId.of(valor);
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "stewer.bootstrap.tenant.fuso='" + valor + "' nao e um fuso IANA valido. "
                            + "Use algo como America/Araguaina.", e);
        }
    }

    private void criarAdminSeFaltar(Tenant tenant, String email) {
        if (usuarioRepository.porEmail(tenant.getId(), email).isPresent()) {
            return;
        }

        String senha = adminSenha.map(String::trim).orElse("");
        if (senha.length() < SENHA_MINIMA) {
            // Metade da configuracao presente e pior do que nenhuma: alguem quis
            // criar o admin e nao conseguiu. Falhar no boot e mais barato do que
            // descobrir em producao que ninguem consegue administrar o estudio.
            throw new IllegalStateException(
                    "stewer.bootstrap.admin.email foi configurado, mas a senha nao: defina "
                            + "stewer.bootstrap.admin.senha com no minimo " + SENHA_MINIMA
                            + " caracteres (variavel BOOTSTRAP_ADMIN_SENHA).");
        }

        Usuario admin = new Usuario();
        admin.setTenantId(tenant.getId());
        admin.setNome(adminNome);
        admin.setEmail(email);
        admin.setSenhaHash(BcryptUtil.bcryptHash(senha));
        admin.setRole(Role.ADMIN);
        usuarioRepository.persist(admin);

        // O e-mail entra no log para que o operador confirme quem foi criado.
        // A senha NAO: ela veio de variavel de ambiente e nao volta por aqui.
        LOG.infof("Administrador inicial '%s' criado na inicializacao.", email);
    }
}
