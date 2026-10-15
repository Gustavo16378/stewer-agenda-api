package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.util.DadosDeTeste;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Corpo ausente ou {@code null} tem de virar 400, nao 500.
 *
 * <p>Nenhum parametro de corpo tinha {@code @NotNull} -- so {@code @Valid}, que
 * em objeto nulo nao dispara (a cascata pula null). O leitor Jackson devolve
 * {@code null} tanto para corpo vazio quanto para o JSON {@code null}, e o
 * service desreferenciava o DTO: NullPointerException, 500 ERRO_INTERNO e um
 * stack trace no log a cada requisicao.
 *
 * <p>Em {@code /auth/login} e {@code /auth/registrar}, que sao publicos, isso e
 * amplificacao de log sem autenticacao nenhuma: qualquer scanner que faca POST
 * sem corpo enche o log de producao.
 *
 * <p>Erro de TIPO e JSON quebrado ja respondiam 400 antes desta correcao; os
 * casos estao aqui para que continuem assim.
 */
@QuarkusTest
@DisplayName("Corpo ausente ou nulo responde 400")
class CorpoAusenteTest {

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Profissional profissional;
    private Servico servico;
    private Usuario cliente;
    private String tokenCliente;
    private String tokenProfissional;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        Usuario usuarioProfissional = dados.usuario(tenant.getId(), "Stewer Gabriel",
                "stewer@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), usuarioProfissional.getId(), "Visagismo");
        servico = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 18000);

        dados.usuario(tenant.getId(), "Gabriel Admin", "admin@stewer.dev", Role.ADMIN);
        cliente = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);

        tokenCliente = login("joana@exemplo.dev");
        tokenProfissional = login("stewer@stewer.dev");
        tokenAdmin = login("admin@stewer.dev");
    }

    // ------------------------------------------------------------------
    // Endpoints publicos: o caso mais barato de abusar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /auth/login com corpo nulo, vazio ou ausente")
    void loginSemCorpo() {
        semCorpo().post("/auth/login").then().statusCode(400);
        corpoNulo().post("/auth/login").then().statusCode(400).body("codigo", equalTo("VALIDACAO"));
        corpoVazio().post("/auth/login").then().statusCode(400);
    }

    @Test
    @DisplayName("POST /auth/registrar com corpo nulo ou ausente")
    void registrarSemCorpo() {
        semCorpo().post("/auth/registrar").then().statusCode(400);
        corpoNulo().post("/auth/registrar").then().statusCode(400);
    }

    // ------------------------------------------------------------------
    // Endpoints autenticados
    // ------------------------------------------------------------------

    @Test
    @DisplayName("POST /agendamentos com corpo nulo")
    void agendamentoSemCorpo() {
        autenticado(tokenCliente).post("/agendamentos").then().statusCode(400);
        comToken(tokenCliente).body("null").post("/agendamentos").then().statusCode(400);
    }

    @Test
    @DisplayName("PATCH /agendamentos/{id}/status com corpo nulo")
    void statusSemCorpo() {
        UUID qualquer = UUID.randomUUID();
        autenticado(tokenProfissional)
                .patch("/agendamentos/{id}/status", qualquer).then().statusCode(400);
        comToken(tokenProfissional).body("null")
                .patch("/agendamentos/{id}/status", qualquer).then().statusCode(400);
    }

    @Test
    @DisplayName("POST e PUT /servicos com corpo nulo")
    void servicoSemCorpo() {
        autenticado(tokenAdmin).post("/servicos").then().statusCode(400);
        comToken(tokenAdmin).body("null").post("/servicos").then().statusCode(400);
        comToken(tokenAdmin).body("null")
                .put("/servicos/{id}", servico.getId()).then().statusCode(400);
    }

    @Test
    @DisplayName("POST e PUT /profissionais com corpo nulo")
    void profissionalSemCorpo() {
        autenticado(tokenAdmin).post("/profissionais").then().statusCode(400);
        comToken(tokenAdmin).body("null")
                .put("/profissionais/{id}", profissional.getId()).then().statusCode(400);
    }

    @Test
    @DisplayName("PUT /disponibilidade e POST /bloqueios com corpo nulo")
    void agendaDoProfissionalSemCorpo() {
        autenticado(tokenProfissional)
                .put("/profissionais/{id}/disponibilidade", profissional.getId())
                .then().statusCode(400);
        comToken(tokenProfissional).body("null")
                .post("/profissionais/{id}/bloqueios", profissional.getId())
                .then().statusCode(400);
    }

    @Test
    @DisplayName("POST /clientes/{id}/anotacoes com corpo nulo")
    void anotacaoSemCorpo() {
        autenticado(tokenProfissional)
                .post("/clientes/{id}/anotacoes", cliente.getId())
                .then().statusCode(400);
    }

    // ------------------------------------------------------------------
    // O que ja funcionava e precisa continuar funcionando
    // ------------------------------------------------------------------

    @Test
    @DisplayName("corpo vazio {} continua apontando os campos obrigatorios")
    void corpoVazioAindaListaViolacoes() {
        given().contentType(ContentType.JSON).body(Map.of())
                .when().post("/auth/login")
                .then().statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", equalTo(2));
    }

    @Test
    @DisplayName("JSON quebrado e tipo errado continuam em 400")
    void jsonQuebradoETipoErrado() {
        given().contentType(ContentType.JSON).body("{oops")
                .when().post("/auth/login").then().statusCode(400);

        given().contentType(ContentType.JSON).body("\"texto-solto\"")
                .when().post("/auth/login").then().statusCode(400);

        given().contentType(ContentType.JSON).body("[]")
                .when().post("/auth/login").then().statusCode(400);
    }

    // ------------------------------------------------------------------

    private static RequestSpecification semCorpo() {
        return given().contentType(ContentType.JSON).when();
    }

    private static RequestSpecification corpoNulo() {
        return given().contentType(ContentType.JSON).body("null").when();
    }

    private static RequestSpecification corpoVazio() {
        return given().contentType(ContentType.JSON).body("").when();
    }

    private static RequestSpecification autenticado(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token)
                .when();
    }

    private static RequestSpecification comToken(String token) {
        return given().contentType(ContentType.JSON).header("Authorization", "Bearer " + token);
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
