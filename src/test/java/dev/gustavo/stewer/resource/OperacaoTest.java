package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O minimo para a API poder ser operada: dizer que esta viva e deixar o
 * navegador falar com ela.
 *
 * <p><strong>Health:</strong> sem {@code /q/health} o orquestrador (Railway,
 * Kubernetes, qualquer um) nao tem como distinguir "subindo" de "morto". O
 * sintoma classico e o container sendo reiniciado em laco porque o deploy nunca
 * e considerado pronto -- ou, pior, trafego entrando antes de o banco estar
 * acessivel.
 *
 * <p><strong>CORS:</strong> o alvo principal e mobile, que nao precisa. Mas o
 * app exporta para web ({@code output: 'static'}) e o smoke so funciona com o
 * Chrome aberto em {@code --disable-web-security} -- uma verificacao que passa
 * num navegador adulterado nao prova nada sobre o navegador do usuario.
 */
@QuarkusTest
@TestProfile(OperacaoTest.ComCors.class)
@DisplayName("Operacao: health check e CORS")
class OperacaoTest {

    static final String ORIGEM_PERMITIDA = "https://app.stewer.dev";

    public static class ComCors implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.cors.enabled", "true",
                    "quarkus.http.cors.origins", ORIGEM_PERMITIDA);
        }
    }

    @Test
    @DisplayName("/q/health responde UP sem exigir autenticacao")
    void healthResponde() {
        given()
                .when().get("/q/health")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"));
    }

    @Test
    @DisplayName("/q/health/ready cobre o banco: trafego so entra com datasource de pe")
    void readinessOlhaOBanco() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", notNullValue());
    }

    @Test
    @DisplayName("preflight de origem permitida volta com Access-Control-Allow-Origin")
    void preflightDaOrigemPermitida() {
        given()
                .header("Origin", ORIGEM_PERMITIDA)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "authorization,content-type")
                .when().options("/auth/login")
                .then()
                .statusCode(200)
                .header("Access-Control-Allow-Origin", equalTo(ORIGEM_PERMITIDA));
    }

    @Test
    @DisplayName("origem desconhecida NAO recebe o cabecalho")
    void origemDesconhecidaNaoPassa() {
        given()
                .header("Origin", "https://site-aleatorio.exemplo")
                .header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login")
                .then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    /**
     * Os dois casos acima provam que o mecanismo funciona quando ligado — eles
     * ligam CORS pelo profile de teste. O que faltava era a aplicacao embarcar
     * essa configuracao, e por variavel de ambiente: cada deploy tem a sua lista
     * de origens, e nenhuma delas pode estar escrita no codigo.
     */
    @Test
    @DisplayName("application.properties liga CORS por variavel de ambiente")
    void corsConfiguravelPorAmbiente() throws java.io.IOException {
        String properties = java.nio.file.Files.readString(
                java.nio.file.Path.of("src", "main", "resources", "application.properties"),
                java.nio.charset.StandardCharsets.UTF_8);

        java.util.function.Predicate<String> declara = chave -> properties.lines()
                .map(String::trim)
                .filter(l -> !l.startsWith("#"))
                .anyMatch(l -> l.replace(" ", "").startsWith(chave + "="));

        org.junit.jupiter.api.Assertions.assertTrue(declara.test("quarkus.http.cors.enabled"),
                "application.properties precisa declarar quarkus.http.cors.enabled");
        org.junit.jupiter.api.Assertions.assertTrue(declara.test("quarkus.http.cors.origins"),
                "application.properties precisa declarar quarkus.http.cors.origins");
        org.junit.jupiter.api.Assertions.assertTrue(properties.contains("CORS_ORIGINS"),
                "as origens precisam vir de variavel de ambiente (CORS_ORIGINS)");
    }
}
