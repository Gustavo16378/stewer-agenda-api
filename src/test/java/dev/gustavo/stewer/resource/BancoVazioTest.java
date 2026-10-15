package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Um deploy novo precisa conseguir cadastrar e entrar.
 *
 * <p>Em producao o Flyway so aplica {@code db/migration}, que e DDL puro: nao ha
 * um unico INSERT. O estabelecimento nascia apenas no seed de dev
 * ({@code db/seed/V2}), que nunca vai para producao. Resultado: a API subia
 * verde e o primeiro usuario recebia
 * {@code 404 "Estabelecimento nao encontrado: stewer-gabriel"} tanto no
 * cadastro quanto no login -- o produto no ar e inutilizavel.
 *
 * <p>Este teste roda num profile proprio, apontado para um slug que nenhuma
 * migration cria. Se a aplicacao souber se inicializar sozinha a partir da
 * configuracao, o estabelecimento existe quando o primeiro cliente chega.
 */
@QuarkusTest
@TestProfile(BancoVazioTest.EstabelecimentoNovo.class)
@DisplayName("Banco sem seed: o primeiro usuario consegue entrar")
class BancoVazioTest {

    /** Um estabelecimento que nenhuma migration cria, como seria em producao. */
    public static class EstabelecimentoNovo implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "stewer.tenant.slug-padrao", "estudio-recem-criado",
                    "stewer.bootstrap.habilitado", "true",
                    "stewer.bootstrap.tenant.nome", "Estudio Recem Criado",
                    "stewer.bootstrap.tenant.fuso", "America/Araguaina");
        }
    }

    private static final String EMAIL = "primeiro.cliente@exemplo.dev";
    private static final String SENHA = "primeira-senha-123";

    @Test
    @DisplayName("cadastro e login funcionam sem nenhum seed ter rodado")
    void cadastroELoginFuncionamSemSeed() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Primeiro Cliente", "email", EMAIL, "senha", SENHA))
                .when().post("/auth/registrar")
                .then()
                .statusCode(201)
                .body("token", notNullValue())
                .body("usuario.role", equalTo("CLIENTE"));

        String token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", EMAIL, "senha", SENHA))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .extract().path("token");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/auth/eu")
                .then()
                .statusCode(200)
                .body("email", equalTo(EMAIL));
    }

    @Test
    @DisplayName("o estabelecimento nasce com o fuso configurado, nao em UTC")
    void estabelecimentoNasceComOFusoConfigurado() {
        // A grade e lida no fuso do tenant; se o bootstrap criasse o
        // estabelecimento sem fuso, voltaria o bug dos horarios deslocados.
        String token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Outro Cliente", "email", "outro@exemplo.dev",
                        "senha", SENHA))
                .when().post("/auth/registrar")
                .then().statusCode(201)
                .extract().path("token");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/servicos")
                .then()
                .statusCode(200);
    }
}
