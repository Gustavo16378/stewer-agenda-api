package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Map;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.util.DadosDeTeste;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
@DisplayName("POST /auth/registrar e /auth/login")
class AuthResourceTest {

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;

    @BeforeEach
    void preparar() {
        dados.limpar();
        // O slug precisa bater com stewer.tenant.slug-padrao para que as
        // requisicoes sem tenantSlug caiam neste estabelecimento.
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
    }

    @Test
    @DisplayName("registra um cliente novo e devolve token + usuario sem senha")
    void registraClienteNovo() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "nome", "Joana Ribeiro",
                        "email", "Joana@Exemplo.dev",
                        "telefone", "+5511999990003",
                        "senha", "dev12345"))
                .when().post("/auth/registrar")
                .then()
                .statusCode(201)
                .body("token", notNullValue())
                .body("tipo", equalTo("Bearer"))
                .body("usuario.role", equalTo("CLIENTE"))
                // e-mail e normalizado para minusculas
                .body("usuario.email", equalTo("joana@exemplo.dev"))
                .body("usuario.tenantId", equalTo(tenant.getId().toString()))
                .body("usuario.senhaHash", nullValue());
    }

    @Test
    @DisplayName("recusa e-mail ja cadastrado no mesmo tenant com 409")
    void recusaEmailDuplicado() {
        dados.usuario(tenant.getId(), "Joana", "joana@exemplo.dev", Role.CLIENTE);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Outra Joana", "email", "joana@exemplo.dev", "senha", "dev12345"))
                .when().post("/auth/registrar")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("EMAIL_EM_USO"));
    }

    @Test
    @DisplayName("valida o corpo da requisicao com 400 e lista de campos")
    void validaCorpo() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "", "email", "nao-e-email", "senha", "123"))
                .when().post("/auth/registrar")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", equalTo(3));
    }

    @Test
    @DisplayName("autentica com a senha correta")
    void loginComSenhaCorreta() {
        dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.PROFISSIONAL);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "stewer@stewer.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .body("usuario.role", equalTo("PROFISSIONAL"));
    }

    @Test
    @DisplayName("recusa senha errada com 401 e mensagem generica")
    void recusaSenhaErrada() {
        dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.PROFISSIONAL);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "stewer@stewer.dev", "senha", "senha-errada"))
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("codigo", equalTo("NAO_AUTENTICADO"))
                .body("mensagem", equalTo("E-mail ou senha invalidos"));
    }

    @Test
    @DisplayName("e-mail inexistente devolve o mesmo 401 de senha errada")
    void recusaEmailInexistente() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "ninguem@exemplo.dev", "senha", "dev12345"))
                .when().post("/auth/login")
                .then()
                .statusCode(401)
                .body("mensagem", equalTo("E-mail ou senha invalidos"));
    }

    @Test
    @DisplayName("o token emitido e aceito por um endpoint protegido")
    void tokenEmitidoEhAceito() {
        dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.PROFISSIONAL);

        String token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "stewer@stewer.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");

        given()
                .header("Authorization", "Bearer " + token)
                .when().get("/auth/eu")
                .then()
                .statusCode(200)
                .body("email", equalTo("stewer@stewer.dev"))
                .body("role", equalTo("PROFISSIONAL"))
                .body("tenantId", equalTo(tenant.getId().toString()));
    }

    @Test
    @DisplayName("endpoint protegido sem token devolve 401")
    void endpointProtegidoSemToken() {
        given().when().get("/auth/eu").then().statusCode(401);
    }

    @Test
    @DisplayName("token adulterado devolve 401")
    void tokenAdulterado() {
        given()
                .header("Authorization", "Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJ4In0.assinatura-falsa")
                .when().get("/auth/eu")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("tenant inexistente devolve 404")
    void tenantInexistente() {
        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "joana@exemplo.dev", "senha", "dev12345",
                        "tenantSlug", "estudio-que-nao-existe"))
                .when().post("/auth/login")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }
}
