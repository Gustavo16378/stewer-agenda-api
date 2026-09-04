package dev.gustavo.stewer.config;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import jakarta.annotation.Priority;

/**
 * Deixa {@code JWT_SECRET} ser uma string comum.
 *
 * <p>O SmallRye JWT nao aceita segredo em texto puro para HS256: o valor de
 * {@code smallrye.jwt.verify.secretkey} (e de {@code smallrye.jwt.sign.key})
 * precisa ser um JWK -- ele tenta ler o conteudo como PEM, depois como JWK, e
 * desiste. Exigir isso da variavel de ambiente seria um pe na canela de quem
 * so quer rodar {@code openssl rand -base64 48} e colar no Railway.
 *
 * <p>Entao este interceptor monta o JWK na leitura da configuracao: pega
 * {@code stewer.jwt.secret} (onde {@code JWT_SECRET} cai) e devolve
 * <code>{"kty":"oct","k":"&lt;base64url dos bytes UTF-8 do segredo&gt;"}</code>.
 * E exatamente a mesma chave que o {@code TokenService} usa para assinar via
 * {@code signWithSecret}, que por baixo faz {@code secret.getBytes(UTF_8)}.
 *
 * <p>Cobrir tambem {@code smallrye.jwt.sign.key} nao e enfeite: em dev e em
 * teste o {@code SmallryeJwtDevModeProcessor} gera um par RSA e sobrescreve as
 * chaves quando NENHUMA das propriedades de chave conhecidas esta presente --
 * e {@code smallrye.jwt.verify.secretkey} nao entra nessa lista. Manter
 * {@code smallrye.jwt.sign.key} configurado (e valido) e o que impede o
 * Quarkus de trocar nosso HMAC por um RSA gerado na hora.
 */
@Priority(Priorities.APPLICATION)
public class JwtSecretConfigInterceptor implements ConfigSourceInterceptor {

    /** Propriedades cujo valor e derivado do segredo em texto puro. */
    private static final Set<String> DERIVADAS = Set.of(
            "smallrye.jwt.verify.secretkey",
            "smallrye.jwt.sign.key");

    private static final String SEGREDO = "stewer.jwt.secret";

    /** HS256 exige chave de 256 bits; abaixo disso o jose4j recusa assinar. */
    public static final int MINIMO_BYTES = 32;

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        if (!DERIVADAS.contains(name)) {
            return context.proceed(name);
        }

        ConfigValue segredo = context.proceed(SEGREDO);
        if (segredo == null || segredo.getValue() == null || segredo.getValue().isBlank()) {
            // Sem segredo configurado, deixa o SmallRye seguir o caminho normal
            // (e reclamar por conta propria).
            return context.proceed(name);
        }

        return segredo.withName(name).withValue(paraJwkOct(segredo.getValue()));
    }

    static String paraJwkOct(String segredo) {
        String material = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(segredo.getBytes(StandardCharsets.UTF_8));
        return "{\"kty\":\"oct\",\"k\":\"" + material + "\"}";
    }
}
