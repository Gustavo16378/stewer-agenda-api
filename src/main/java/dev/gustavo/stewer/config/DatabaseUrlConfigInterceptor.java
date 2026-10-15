package dev.gustavo.stewer.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Priorities;
import jakarta.annotation.Priority;

/**
 * Deixa {@code DATABASE_URL} ser colado exatamente como o painel do banco mostra.
 *
 * <p>Neon, Supabase, Railway e companhia entregam uma linha so, no formato do
 * {@code libpq}:
 *
 * <pre>postgresql://usuario:senha@host/banco?sslmode=require</pre>
 *
 * <p>O driver JDBC nao entende isso: ele quer {@code jdbc:postgresql://host/banco}
 * e as credenciais em propriedades separadas. Sem este interceptor, a primeira
 * tarefa de quem faz o deploy seria picar a string em tres pedacos na mao --
 * exatamente o tipo de passo onde a senha acaba no lugar errado.
 *
 * <p>Tres formatos aceitos:
 * <ul>
 *   <li>{@code postgresql://...} ou {@code postgres://...}: quebrado aqui nas
 *       tres propriedades que o Quarkus espera;</li>
 *   <li>{@code jdbc:postgresql://...}: repassado como esta. Quem usa este
 *       formato informa usuario e senha por variavel separada, como antes;</li>
 *   <li>vazio: ninguem mexe em nada. E o caso de dev e de teste, que configuram
 *       o datasource por outro caminho.</li>
 * </ul>
 *
 * <p>A query string e preservada: e la que vem o {@code sslmode=require} que o
 * Neon exige. Usuario e senha saem decodificados, porque a URL os traz
 * percent-encoded e o driver espera o valor literal.
 */
@Priority(Priorities.APPLICATION)
public class DatabaseUrlConfigInterceptor implements ConfigSourceInterceptor {

    /**
     * A variavel de ambiente, lida direto.
     *
     * <p>Nao da para apontar para uma propriedade do {@code application.properties}
     * aqui: a expansao de {@code ${...}} acontece mais perto de quem chama do
     * que este interceptor, entao o que chegaria seria o texto
     * {@code "${DATABASE_URL:}"} e nao o valor. Lendo o nome da variavel, o
     * SmallRye resolve pela fonte de ambiente, sem expressao no meio.
     */
    static final String ORIGEM = "DATABASE_URL";

    static final String PROP_URL = "quarkus.datasource.jdbc.url";
    static final String PROP_USUARIO = "quarkus.datasource.username";
    static final String PROP_SENHA = "quarkus.datasource.password";

    private static final Set<String> DERIVADAS = Set.of(PROP_URL, PROP_USUARIO, PROP_SENHA);

    private static final String PREFIXO_JDBC = "jdbc:";

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        if (!DERIVADAS.contains(name)) {
            return context.proceed(name);
        }

        ConfigValue origem = context.proceed(ORIGEM);
        String bruto = origem == null ? null : origem.getValue();
        // "${...}" por aqui significa expressao que ninguem expandiu: trata como
        // ausente em vez de tentar parsear o literal.
        if (bruto == null || bruto.isBlank() || bruto.contains("${")) {
            return context.proceed(name);
        }

        String valor = bruto.trim();
        if (valor.startsWith(PREFIXO_JDBC)) {
            // Ja esta no formato do driver: so a URL vem daqui; usuario e senha
            // continuam vindo das variaveis proprias.
            return name.equals(PROP_URL) ? origem.withName(name).withValue(valor)
                    : context.proceed(name);
        }

        Conexao conexao = Conexao.de(valor);
        String derivado = switch (name) {
            case PROP_URL -> conexao.jdbcUrl();
            case PROP_USUARIO -> conexao.usuario();
            case PROP_SENHA -> conexao.senha();
            default -> null;
        };

        // Sem usuario ou senha na URL, deixa a variavel propria responder.
        if (derivado == null || derivado.isEmpty()) {
            return context.proceed(name);
        }
        return origem.withName(name).withValue(derivado);
    }

    /** As tres partes que o Quarkus precisa, extraidas de uma URL do libpq. */
    record Conexao(String jdbcUrl, String usuario, String senha) {

        static Conexao de(String url) {
            URI uri = URI.create(url);

            StringBuilder jdbc = new StringBuilder(PREFIXO_JDBC).append("postgresql://");
            if (uri.getHost() != null) {
                jdbc.append(uri.getHost());
            }
            if (uri.getPort() > 0) {
                jdbc.append(':').append(uri.getPort());
            }
            if (uri.getRawPath() != null) {
                jdbc.append(uri.getRawPath());
            }
            // sslmode, channel_binding e afins vivem aqui e nao podem se perder:
            // sem sslmode=require o Neon recusa a conexao.
            if (uri.getRawQuery() != null && !uri.getRawQuery().isBlank()) {
                jdbc.append('?').append(uri.getRawQuery());
            }

            String usuario = "";
            String senha = "";
            String credenciais = uri.getRawUserInfo();
            if (credenciais != null && !credenciais.isEmpty()) {
                int divisor = credenciais.indexOf(':');
                if (divisor < 0) {
                    usuario = decodificar(credenciais);
                } else {
                    usuario = decodificar(credenciais.substring(0, divisor));
                    senha = decodificar(credenciais.substring(divisor + 1));
                }
            }

            return new Conexao(jdbc.toString(), usuario, senha);
        }

        /** A senha vem percent-encoded na URL; o driver espera o valor literal. */
        private static String decodificar(String trecho) {
            return URLDecoder.decode(trecho, StandardCharsets.UTF_8);
        }
    }
}
