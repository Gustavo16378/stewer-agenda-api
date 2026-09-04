package dev.gustavo.stewer.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.gustavo.stewer.config.JwtSecretConfigInterceptor;
import dev.gustavo.stewer.domain.Usuario;

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
        validarSegredo(secret);
    }

    /**
     * HS256 exige chave de 256 bits. Com um segredo menor o jose4j recusa
     * assinar -- e o sintoma seria um 500 na primeira tentativa de login, em
     * producao, longe de quem configurou a variavel. Melhor derrubar o boot
     * com uma mensagem que diz exatamente o que fazer.
     */
    private static void validarSegredo(String secret) {
        int bytes = secret == null ? 0 : secret.getBytes(StandardCharsets.UTF_8).length;
        if (bytes < JwtSecretConfigInterceptor.MINIMO_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET precisa ter no minimo " + JwtSecretConfigInterceptor.MINIMO_BYTES
                            + " bytes para HS256 (o atual tem " + bytes
                            + "). Gere um com: openssl rand -base64 48");
        }
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
