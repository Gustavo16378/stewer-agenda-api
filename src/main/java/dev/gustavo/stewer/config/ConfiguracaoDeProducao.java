package dev.gustavo.stewer.config;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.runtime.LaunchMode;
import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Recusa subir em producao com configuracao de banco pela metade.
 *
 * <p>Sem isto, {@code DATABASE_URL} faltando vira um erro do pool na primeira
 * requisicao -- longe de quem configurou, com uma mensagem sobre driver e
 * conexao que nao diz qual variavel esquecer. O deploy fica verde e o produto
 * quebrado.
 *
 * <p>A validacao do segredo JWT mora em {@code TokenService} pelo mesmo motivo.
 * As duas correm na subida: ou a API sobe inteira, ou nao sobe.
 */
@Startup
@ApplicationScoped
public class ConfiguracaoDeProducao {

    private static final String COMO_OBTER =
            " Cole em DATABASE_URL a connection string do painel do banco, no formato "
                    + "postgresql://usuario:senha@host/banco?sslmode=require (a direta, nao a "
                    + "pooled). Veja DEPLOY.md.";

    public ConfiguracaoDeProducao(
            @ConfigProperty(name = "quarkus.datasource.jdbc.url", defaultValue = "") String url) {
        if (LaunchMode.current() != LaunchMode.NORMAL) {
            // Em dev o datasource vem do docker-compose; em teste, dos Dev Services.
            return;
        }
        validar(url);
    }

    /** Visivel ao pacote para o teste exercitar os ramos sem subir a API. */
    static void validar(String url) {
        String valor = url == null ? "" : url.trim();

        if (valor.isEmpty()) {
            throw new IllegalStateException(
                    "DATABASE_URL nao esta definida. A API nao sobe sem banco." + COMO_OBTER);
        }

        if (!valor.startsWith("jdbc:postgresql:")) {
            // Chegar aqui significa que o interceptor nao reconheceu o formato:
            // provavelmente um esquema diferente (mysql://, um host solto) ou a
            // string veio truncada de um copiar e colar.
            throw new IllegalStateException(
                    "DATABASE_URL nao virou uma URL JDBC de PostgreSQL (resultou em '" + valor
                            + "')." + COMO_OBTER);
        }
    }
}
