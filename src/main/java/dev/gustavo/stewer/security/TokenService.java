package dev.gustavo.stewer.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.gustavo.stewer.config.JwtSecretConfigInterceptor;
import dev.gustavo.stewer.domain.Usuario;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Emite os JWTs da API.
 *
 * <p>Assinatura HMAC (HS256) com segredo compartilhado: em prod ele vem da
 * variavel JWT_SECRET. E o formato que o Railway e afins tornam trivial de
 * configurar, sem par de chaves para distribuir.
 */
@Startup
@ApplicationScoped
public class TokenService {

    private final String issuer;
    private final String secret;
    private final Duration validade;

    public TokenService(
            @ConfigProperty(name = "stewer.jwt.issuer") String issuer,
            @ConfigProperty(name = "stewer.jwt.secret") String secret,
            @ConfigProperty(name = "stewer.jwt.expiracao-segundos") long expiracaoSegundos) {
        this.issuer = issuer;
        this.secret = secret;
        this.validade = Duration.ofSeconds(expiracaoSegundos);
        validarSegredo(secret, emProducao());
    }

    /**
     * Segredos que este repositorio ja publicou. Como o repositorio e aberto,
     * qualquer um deles vale tanto quanto uma senha escrita no mural: servem
     * para assinar token contra um banco local descartavel e nada mais.
     *
     * <p>O primeiro vazou junto com o codigo e esta banido ate em dev -- quem
     * tiver um token velho assinado com ele nao pode conseguir entrar de novo.
     * Os outros dois sao os defaults atuais de {@code %dev} e {@code %test};
     * valem naqueles profiles e so neles.
     */
    private static final String SEGREDO_BANIDO =
            "dev-secret-stewer-agenda-api-nao-use-isto-em-producao";

    private static final Set<String> SEGREDOS_PUBLICOS = Set.of(
            "D6w42/Ajia/d6expDVc90K3JNR9PiMDBf1WFPfPQBFN+pKCqGNGybRArbc1YpELg",
            "AEI426bRjR0XKMn782pfDDpO7k+VhYZ73WkAW/KPZgDW/dwYMYkOUdTCPWM3nO+b",
            "troque-este-segredo-por-um-valor-aleatorio-de-no-minimo-32-chars");

    private static final String COMO_GERAR =
            " Gere um com: openssl rand -base64 48 -- e injete em JWT_SECRET.";

    /**
     * Derruba o boot quando o segredo nao serve, com a mensagem que resolve.
     *
     * <p>Tres recusas, em ordem de gravidade:
     * <ol>
     *   <li>vazio: em producao nao existe default, entao a variavel faltou;</li>
     *   <li>curto demais: HS256 exige 256 bits. O jose4j recusaria assinar e o
     *       sintoma seria um 500 na primeira tentativa de login, em producao,
     *       longe de quem configurou a variavel;</li>
     *   <li>publico: um segredo que esta no GitHub nao protege nada. O banido
     *       cai em qualquer profile; os defaults de dev e test so caem quando a
     *       aplicacao esta rodando de verdade.</li>
     * </ol>
     *
     * <p>Visivel ao pacote para o teste exercitar cada ramo sem subir a API.
     */
    static void validarSegredo(String secret, boolean producao) {
        String valor = secret == null ? "" : secret.trim();

        if (valor.isEmpty()) {
            throw new IllegalStateException(
                    "JWT_SECRET nao esta definida. A API nao sobe sem ela: sem segredo nao ha "
                            + "como assinar nem validar token." + COMO_GERAR);
        }

        int bytes = valor.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < JwtSecretConfigInterceptor.MINIMO_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ter no minimo " + JwtSecretConfigInterceptor.MINIMO_BYTES
                            + " bytes para HS256 (o atual tem " + bytes + ")." + COMO_GERAR);
        }

        if (SEGREDO_BANIDO.equals(valor)) {
            throw new IllegalStateException(
                    "Este JWT_SECRET vazou no historico publico do repositorio e nao pode ser "
                            + "usado em profile nenhum." + COMO_GERAR);
        }

        if (producao && SEGREDOS_PUBLICOS.contains(valor)) {
            throw new IllegalStateException(
                    "Este JWT_SECRET e um valor de exemplo que esta no repositorio publico; "
                            + "em producao ele deixaria qualquer um forjar token de ADMIN."
                            + COMO_GERAR);
        }
    }

    /** Producao = aplicacao rodando de verdade, fora do dev mode e dos testes. */
    private static boolean emProducao() {
        return LaunchMode.current() == LaunchMode.NORMAL;
    }

    public String gerar(Usuario usuario) {
        Instant agora = Instant.now();
        return Jwt.issuer(issuer)
                .subject(usuario.getId().toString())
                .upn(usuario.getEmail())
                .groups(Set.of(usuario.getRole().name()))
                .claim(ClaimsStewer.TENANT_ID, usuario.getTenantId().toString())
                .claim(ClaimsStewer.ROLE, usuario.getRole().name())
                .claim(ClaimsStewer.NOME, usuario.getNome())
                .issuedAt(agora)
                .expiresAt(agora.plus(validade))
                .signWithSecret(secret);
    }

    public long validadeSegundos() {
        return validade.toSeconds();
    }
}
