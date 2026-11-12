package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;

import java.util.HashMap;
import java.util.Map;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A propria conta (dados e senha) e a busca de pessoas pelo admin. */
@QuarkusTest
@DisplayName("/auth/eu e /usuarios -- dados da conta, senha e busca de pessoas")
class ContaResourceTest {

    @Inject
    DadosDeTeste dados;

    private String tokenJoana;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);
        dados.usuario(tenant.getId(), "Ana Administradora", "ana@stewer.dev", Role.ADMIN);
        tokenJoana = login("joana@exemplo.dev", DadosDeTeste.SENHA);
        tokenAdmin = login("ana@stewer.dev", DadosDeTeste.SENHA);
    }

    @Test
    @DisplayName("GET /auth/eu traz a data de criacao da conta")
    void euTrazCriadoEm() {
        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/auth/eu")
                .then().statusCode(200)
                .body("criadoEm", notNullValue());
    }

    @Test
    @DisplayName("PUT /auth/eu corrige os dados e devolve token novo com o nome novo")
    void atualizaPerfil() {
        String tokenNovo = putPerfil(tokenJoana, "Joana R. Souza", "JOANA.SOUZA@exemplo.dev", "+55 63 99999-0000")
                .statusCode(200)
                .body("usuario.nome", equalTo("Joana R. Souza"))
                .body("usuario.email", equalTo("joana.souza@exemplo.dev"))
                .body("usuario.telefone", equalTo("+55 63 99999-0000"))
                .extract().path("token");

        // O login passa a ser com o e-mail novo.
        login("joana.souza@exemplo.dev", DadosDeTeste.SENHA);
        given().header("Authorization", "Bearer " + tokenNovo)
                .when().get("/auth/eu")
                .then().statusCode(200).body("nome", equalTo("Joana R. Souza"));
    }

    @Test
    @DisplayName("PUT /auth/eu com e-mail de outra conta devolve 409 EMAIL_EM_USO")
    void emailDeOutraConta() {
        putPerfil(tokenJoana, "Joana", "marcos@exemplo.dev", null)
                .statusCode(409)
                .body("codigo", equalTo("EMAIL_EM_USO"));
    }

    @Test
    @DisplayName("trocar senha exige a atual e a nova passa a valer no login")
    void trocaSenha() {
        putSenha(tokenJoana, "errada123", "novaSenha123")
                .statusCode(422)
                .body("codigo", equalTo("SENHA_ATUAL_INCORRETA"));

        putSenha(tokenJoana, DadosDeTeste.SENHA, "novaSenha123").statusCode(204);
        login("joana@exemplo.dev", "novaSenha123");
    }

    @Test
    @DisplayName("admin busca pessoas por nome; cliente nao tem acesso")
    void buscaPessoas() {
        given().header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("busca", "marc")
                .when().get("/usuarios")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].nome", equalTo("Marcos Lima"))
                .body("[0].role", equalTo("CLIENTE"));

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/usuarios")
                .then().statusCode(200)
                .body("nome", hasItem("Ana Administradora"));

        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/usuarios")
                .then().statusCode(403);
    }

    private ValidatableResponse putPerfil(String token, String nome, String email, String telefone) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("email", email);
        corpo.put("telefone", telefone);
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().put("/auth/eu")
                .then();
    }

    private ValidatableResponse putSenha(String token, String atual, String nova) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("senhaAtual", atual, "novaSenha", nova))
                .when().put("/auth/eu/senha")
                .then();
    }

    private String login(String email, String senha) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", senha))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
