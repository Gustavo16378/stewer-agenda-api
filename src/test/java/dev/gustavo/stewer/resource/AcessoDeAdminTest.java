package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.util.Map;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** O admin da e tira acesso de admin pelo app, sem ninguem ficar trancado do lado de fora. */
@QuarkusTest
@DisplayName("acesso de admin -- dar, tirar e as travas")
class AcessoDeAdminTest {

    @Inject
    DadosDeTeste dados;

    private Usuario gustavo;
    private Usuario stewer;
    private Usuario joana;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        gustavo = dados.usuario(tenant.getId(), "Gustavo", "gustavo@stewer.dev", Role.ADMIN);
        stewer = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), stewer.getId(), "Visagismo");
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
    }

    @Test
    @DisplayName("da acesso, a pessoa entra como admin e volta a profissional ao perder")
    void daETira() {
        String admin = login("gustavo@stewer.dev");
        given().header("Authorization", "Bearer " + admin)
                .when().put("/usuarios/" + stewer.getId() + "/admin")
                .then().statusCode(200).body("role", equalTo("ADMIN"));

        given().contentType(ContentType.JSON)
                .body(Map.of("email", "stewer@stewer.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).body("usuario.role", equalTo("ADMIN"));

        given().header("Authorization", "Bearer " + admin)
                .when().get("/usuarios/admins")
                .then().statusCode(200).body("", hasSize(2));

        given().header("Authorization", "Bearer " + admin)
                .when().get("/auditoria?entidade=ACESSO")
                .then().statusCode(200)
                .body("resumo", hasItem("Deu acesso de admin para Stewer Gabriel"));

        given().header("Authorization", "Bearer " + admin)
                .when().delete("/usuarios/" + stewer.getId() + "/admin")
                .then().statusCode(200).body("role", equalTo("PROFISSIONAL"));
    }

    @Test
    @DisplayName("cliente que perde o acesso volta a cliente")
    void clienteVoltaACliente() {
        String admin = login("gustavo@stewer.dev");
        given().header("Authorization", "Bearer " + admin)
                .when().put("/usuarios/" + joana.getId() + "/admin").then().statusCode(200);
        given().header("Authorization", "Bearer " + admin)
                .when().delete("/usuarios/" + joana.getId() + "/admin")
                .then().statusCode(200).body("role", equalTo("CLIENTE"));
    }

    @Test
    @DisplayName("ninguem tira o proprio acesso, e o ultimo admin fica")
    void travas() {
        String admin = login("gustavo@stewer.dev");
        given().header("Authorization", "Bearer " + admin)
                .when().delete("/usuarios/" + gustavo.getId() + "/admin")
                .then().statusCode(422).body("codigo", equalTo("PROPRIO_ACESSO"));

        given().header("Authorization", "Bearer " + admin)
                .when().put("/usuarios/" + stewer.getId() + "/admin").then().statusCode(200);
        String novo = login("stewer@stewer.dev");
        given().header("Authorization", "Bearer " + novo)
                .when().delete("/usuarios/" + gustavo.getId() + "/admin")
                .then().statusCode(200);
        // agora o Stewer e o unico: ninguem consegue tirar o dele
        given().header("Authorization", "Bearer " + novo)
                .when().delete("/usuarios/" + stewer.getId() + "/admin")
                .then().statusCode(422).body("codigo", equalTo("PROPRIO_ACESSO"));
    }

    @Test
    @DisplayName("so admin mexe em acesso")
    void soAdmin() {
        given().header("Authorization", "Bearer " + login("stewer@stewer.dev"))
                .when().put("/usuarios/" + joana.getId() + "/admin")
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + login("joana@exemplo.dev"))
                .when().get("/usuarios/admins")
                .then().statusCode(403);
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
