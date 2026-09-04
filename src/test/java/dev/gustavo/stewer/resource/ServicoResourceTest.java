package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.util.DadosDeTeste;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@QuarkusTest
@DisplayName("/servicos -- catalogo do estabelecimento")
class ServicoResourceTest {

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void preparar() {
        dados.limpar();
        // O slug bate com stewer.tenant.slug-padrao para que o login dispense o tenantSlug.
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Gabriel Stewer", "admin@stewer.dev", Role.ADMIN);
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        tokenAdmin = login("admin@stewer.dev");
        tokenCliente = login("joana@exemplo.dev");
    }

    @Test
    @DisplayName("cliente so enxerga servicos ativos, mesmo pedindo os inativos")
    void clienteSoVeAtivos() {
        dados.servico(tenant.getId(), "Barba terapeutica", 30, 5000);
        criarComoAdmin(corpoInativo("Corte premium", 45, 9000));

        given()
                .header("Authorization", bearer(tokenCliente))
                .queryParam("incluirInativos", true)
                .when().get("/servicos")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("nome[0]", equalTo("Barba terapeutica"))
                .body("ativo[0]", equalTo(true));
    }

    @Test
    @DisplayName("admin traz os inativos quando pede, ordenados por nome")
    void adminVeInativosQuandoPede() {
        dados.servico(tenant.getId(), "Barba terapeutica", 30, 5000);
        criarComoAdmin(corpoInativo("Corte premium", 45, 9000));

        given()
                .header("Authorization", bearer(tokenAdmin))
                .queryParam("incluirInativos", true)
                .when().get("/servicos")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("nome[0]", equalTo("Barba terapeutica"))
                .body("nome[1]", equalTo("Corte premium"))
                .body("ativo[1]", equalTo(false));

        // Sem o parametro, nem o admin ve o desativado.
        given()
                .header("Authorization", bearer(tokenAdmin))
                .when().get("/servicos")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("nome[0]", equalTo("Barba terapeutica"));
    }

    @Test
    @DisplayName("admin cadastra servico com 201, header Location e nome sem espacos sobrando")
    void adminCriaServico() {
        given()
                .header("Authorization", bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "nome", "  Consultoria de imagem  ",
                        "descricao", "Analise de visagismo com proposta de corte",
                        "duracaoMinutos", 90,
                        "precoCentavos", 25000))
                .when().post("/servicos")
                .then()
                .statusCode(201)
                .header("Location", containsString("/servicos/"))
                .body("id", notNullValue())
                .body("tenantId", equalTo(tenant.getId().toString()))
                .body("nome", equalTo("Consultoria de imagem"))
                .body("duracaoMinutos", equalTo(90))
                .body("precoCentavos", equalTo(25000))
                // ativo omitido no corpo nasce como true
                .body("ativo", equalTo(true));
    }

    @Test
    @DisplayName("cliente nao gerencia o catalogo: 403 ao tentar criar")
    void clienteNaoCria() {
        given()
                .header("Authorization", bearer(tokenCliente))
                .contentType(ContentType.JSON)
                .body(corpo("Corte premium", 45, 9000))
                .when().post("/servicos")
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("sem token o catalogo responde 401")
    void semTokenDevolve401() {
        given().when().get("/servicos").then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body(corpo("Corte premium", 45, 9000))
                .when().post("/servicos")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("nome repetido (ignorando caixa) devolve 409 NOME_EM_USO")
    void nomeDuplicadoDevolve409() {
        dados.servico(tenant.getId(), "Corte premium", 45, 9000);

        given()
                .header("Authorization", bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(corpo("corte PREMIUM", 60, 11000))
                .when().post("/servicos")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("NOME_EM_USO"));
    }

    @Test
    @DisplayName("corpo invalido devolve 400 com a lista de campos")
    void corpoInvalidoDevolve400() {
        given()
                .header("Authorization", bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "", "duracaoMinutos", 2, "precoCentavos", -1))
                .when().post("/servicos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", equalTo(3));
    }

    @Test
    @DisplayName("PUT altera os dados e o GET seguinte confirma")
    void putAlteraServico() {
        Servico servico = dados.servico(tenant.getId(), "Corte premium", 45, 9000);

        given()
                .header("Authorization", bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "nome", "Corte premium plus",
                        "descricao", "Com finalizacao",
                        "duracaoMinutos", 60,
                        "precoCentavos", 12000,
                        "ativo", false))
                .when().put("/servicos/" + servico.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(servico.getId().toString()))
                .body("nome", equalTo("Corte premium plus"))
                .body("descricao", equalTo("Com finalizacao"))
                .body("duracaoMinutos", equalTo(60))
                .body("precoCentavos", equalTo(12000))
                .body("ativo", equalTo(false));

        given()
                .header("Authorization", bearer(tokenAdmin))
                .when().get("/servicos/" + servico.getId())
                .then()
                .statusCode(200)
                .body("nome", equalTo("Corte premium plus"))
                .body("ativo", equalTo(false));
    }

    @Test
    @DisplayName("DELETE e soft delete: some do catalogo e o GET vira 404")
    void deleteFazSoftDelete() {
        Servico servico = dados.servico(tenant.getId(), "Corte premium", 45, 9000);

        given()
                .header("Authorization", bearer(tokenAdmin))
                .when().delete("/servicos/" + servico.getId())
                .then()
                .statusCode(204);

        given()
                .header("Authorization", bearer(tokenAdmin))
                .when().get("/servicos/" + servico.getId())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));

        // Nem com incluirInativos: soft-deletado nao e o mesmo que desativado.
        given()
                .header("Authorization", bearer(tokenAdmin))
                .queryParam("incluirInativos", true)
                .when().get("/servicos")
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("cliente nao pode remover servico: 403")
    void clienteNaoRemove() {
        Servico servico = dados.servico(tenant.getId(), "Corte premium", 45, 9000);

        given()
                .header("Authorization", bearer(tokenCliente))
                .when().delete("/servicos/" + servico.getId())
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("id inexistente devolve 404")
    void idInexistenteDevolve404() {
        given()
                .header("Authorization", bearer(tokenAdmin))
                .when().get("/servicos/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private void criarComoAdmin(Map<String, Object> corpo) {
        given()
                .header("Authorization", bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post("/servicos")
                .then().statusCode(201);
    }

    private static Map<String, Object> corpo(String nome, int duracaoMinutos, long precoCentavos) {
        return Map.of("nome", nome,
                "duracaoMinutos", duracaoMinutos,
                "precoCentavos", precoCentavos);
    }

    private static Map<String, Object> corpoInativo(String nome, int duracaoMinutos,
            long precoCentavos) {
        return Map.of("nome", nome,
                "duracaoMinutos", duracaoMinutos,
                "precoCentavos", precoCentavos,
                "ativo", false);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
