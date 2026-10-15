package dev.gustavo.stewer.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Producao sem banco configurado nao pode subir "verde e quebrada".
 */
@DisplayName("Validacao do datasource em producao")
class ConfiguracaoDeProducaoTest {

    @Test
    @DisplayName("sem DATABASE_URL, derruba o boot dizendo qual variavel falta")
    void semUrl() {
        for (String vazio : new String[] { null, "", "   " }) {
            IllegalStateException e = assertThrows(IllegalStateException.class,
                    () -> ConfiguracaoDeProducao.validar(vazio));
            assertTrue(e.getMessage().contains("DATABASE_URL nao esta definida"), e.getMessage());
            assertTrue(e.getMessage().contains("DEPLOY.md"), e.getMessage());
        }
    }

    @Test
    @DisplayName("string que nao virou JDBC de PostgreSQL derruba o boot")
    void formatoInesperado() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> ConfiguracaoDeProducao.validar("mysql://u:s@host/banco"));
        assertTrue(e.getMessage().contains("nao virou uma URL JDBC"), e.getMessage());
    }

    @Test
    @DisplayName("a URL que o interceptor produz passa")
    void urlDerivadaPassa() {
        String jdbc = DatabaseUrlConfigInterceptor.Conexao
                .de("postgresql://u:s@ep-abc.us-east-1.aws.neon.tech/neondb?sslmode=require")
                .jdbcUrl();

        assertDoesNotThrow(() -> ConfiguracaoDeProducao.validar(jdbc));
    }
}
