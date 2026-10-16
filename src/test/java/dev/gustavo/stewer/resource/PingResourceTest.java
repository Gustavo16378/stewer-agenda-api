package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O sinal de vida que mantem a instancia gratuita acordada sem acordar o banco.
 *
 * <p>A separacao importa em dinheiro: o ping externo bate de 10 em 10 minutos
 * para o Render nao dormir. Se ele consultasse o banco, manteria tambem o
 * compute do Neon de pe 24h por dia e as 100 CU-hours do mes acabariam na
 * primeira quinzena.
 *
 * <p>Que o endpoint realmente sobrevive ao banco fora do ar esta provado fora
 * daqui, com o Postgres parado e o container de producao no ar. Aqui travamos o que da para travar em teste: ele e
 * publico, nao depende de token e o grupo de liveness nao carrega o datasource.
 */
@QuarkusTest
@DisplayName("Sinal de vida sem banco")
class PingResourceTest {

    @Test
    @DisplayName("GET /ping responde 200 sem token")
    void pingEhPublico() {
        given()
                .when().get("/ping")
                .then()
                .statusCode(200)
                .body(equalTo("pong"));
    }

    @Test
    @DisplayName("liveness nao inclui o check do banco")
    void livenessNaoOlhaOBanco() {
        given()
                .when().get("/q/health/live")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                // O check do datasource e de READINESS. Se ele aparecer aqui, o
                // health check do Render passa a acordar o Neon a cada 10 min.
                .body("checks.name", not(hasItem("Database connections health check")));
    }

    @Test
    @DisplayName("readiness inclui o banco: e o endpoint para olhar na mao")
    void readinessOlhaOBanco() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("status", equalTo("UP"))
                .body("checks.name", hasItem("Database connections health check"))
                .body("checks", not(empty()));
    }
}
