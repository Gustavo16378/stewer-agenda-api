package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Profissional;
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

@QuarkusTest
@DisplayName("/profissionais")
class ProfissionalResourceTest {

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Usuario admin;
    private Usuario cliente;
    private Usuario futuroProfissional;
    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        admin = dados.usuario(tenant.getId(), "Gabriel Stewer", "admin@stewer.dev", Role.ADMIN);
        cliente = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        futuroProfissional = dados.usuario(tenant.getId(), "Marcos Vidal", "marcos@stewer.dev",
                Role.CLIENTE);
        tokenAdmin = login(admin.getEmail());
        tokenCliente = login(cliente.getEmail());
    }

    @Test
    @DisplayName("ADMIN cadastra profissional com 201 e promove o usuario a PROFISSIONAL")
    void adminCadastraEPromove() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "usuarioId", futuroProfissional.getId().toString(),
                        "especialidade", "Visagismo",
                        "bio", "Especialista em colorimetria."))
                .when().post("/profissionais")
                .then()
                .statusCode(201)
                .header("Location", containsString("/profissionais/"))
                .body("id", notNullValue())
                .body("tenantId", equalTo(tenant.getId().toString()))
                .body("usuarioId", equalTo(futuroProfissional.getId().toString()))
                .body("nome", equalTo("Marcos Vidal"))
                .body("email", equalTo("marcos@stewer.dev"))
                .body("especialidade", equalTo("Visagismo"))
                .body("ativo", equalTo(true));

        // A promocao e o ponto da regra: o token novo do Marcos ja tem que sair
        // com a role trocada, senao ele nao enxerga a propria agenda.
        given()
                .header("Authorization", "Bearer " + login("marcos@stewer.dev"))
                .when().get("/auth/eu")
                .then()
                .statusCode(200)
                .body("role", equalTo("PROFISSIONAL"));
    }

    @Test
    @DisplayName("cadastrar um ADMIN como profissional nao rebaixa a role dele")
    void cadastroDeAdminMantemRole() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", admin.getId().toString(), "especialidade", "Dono"))
                .when().post("/profissionais")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + login(admin.getEmail()))
                .when().get("/auth/eu")
                .then()
                .statusCode(200)
                .body("role", equalTo("ADMIN"));
    }

    @Test
    @DisplayName("cadastrar o mesmo usuario duas vezes devolve 409 JA_E_PROFISSIONAL")
    void recusaProfissionalDuplicado() {
        dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", futuroProfissional.getId().toString()))
                .when().post("/profissionais")
                .then()
                .statusCode(409)
                .body("codigo", equalTo("JA_E_PROFISSIONAL"));
    }

    @Test
    @DisplayName("usuarioId que nao existe no tenant devolve 404")
    void recusaUsuarioInexistente() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", UUID.randomUUID().toString()))
                .when().post("/profissionais")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("CLIENTE nao pode cadastrar profissional: 403")
    void clienteNaoCadastra() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", futuroProfissional.getId().toString()))
                .when().post("/profissionais")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("sem token: 401 na listagem e no cadastro")
    void semTokenDevolve401() {
        given().when().get("/profissionais").then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", futuroProfissional.getId().toString()))
                .when().post("/profissionais")
                .then().statusCode(401);
    }

    @Test
    @DisplayName("lista os profissionais vivos do tenant")
    void listaProfissionais() {
        Usuario ana = dados.usuario(tenant.getId(), "Ana Souza", "ana@stewer.dev",
                Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), ana.getId(), "Maquiagem");
        dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when().get("/profissionais")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                // ordenado pelo nome do usuario
                .body("[0].nome", equalTo("Ana Souza"))
                .body("[1].nome", equalTo("Marcos Vidal"));
    }

    @Test
    @DisplayName("inativo so aparece para ADMIN e so com incluirInativos=true")
    void inativoSoApareceParaAdmin() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "usuarioId", futuroProfissional.getId().toString(),
                        "especialidade", "Visagismo",
                        "ativo", false))
                .when().post("/profissionais")
                .then().statusCode(201).body("ativo", equalTo(false));

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/profissionais")
                .then().statusCode(200).body("size()", equalTo(0));

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("incluirInativos", true)
                .when().get("/profissionais")
                .then().statusCode(200).body("size()", equalTo(1));

        // Cliente pedindo a flag continua sem ver quem esta fora da agenda.
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .queryParam("incluirInativos", true)
                .when().get("/profissionais")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    @Test
    @DisplayName("busca por id devolve o profissional e 404 quando nao existe")
    void buscaPorId() {
        Profissional profissional =
                dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when().get("/profissionais/" + profissional.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(profissional.getId().toString()))
                .body("nome", equalTo("Marcos Vidal"));

        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when().get("/profissionais/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("ADMIN altera a especialidade")
    void atualizaEspecialidade() {
        Profissional profissional =
                dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "usuarioId", futuroProfissional.getId().toString(),
                        "especialidade", "Colorimetria"))
                .when().put("/profissionais/" + profissional.getId())
                .then()
                .statusCode(200)
                .body("especialidade", equalTo("Colorimetria"));

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/profissionais/" + profissional.getId())
                .then()
                .statusCode(200)
                .body("especialidade", equalTo("Colorimetria"));
    }

    @Test
    @DisplayName("PUT recusa a troca do usuario dono da ficha com 422")
    void recusaTrocaDeUsuario() {
        Profissional profissional =
                dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", cliente.getId().toString()))
                .when().put("/profissionais/" + profissional.getId())
                .then()
                .statusCode(422)
                .body("codigo", equalTo("USUARIO_NAO_ALTERAVEL"));
    }

    @Test
    @DisplayName("DELETE soft-deleta: 204, some da lista e vira 404")
    void removeSoftDeletando() {
        Profissional profissional =
                dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .when().delete("/profissionais/" + profissional.getId())
                .then().statusCode(204);

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("incluirInativos", true)
                .when().get("/profissionais")
                .then().statusCode(200).body("size()", equalTo(0));

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/profissionais/" + profissional.getId())
                .then().statusCode(404);
    }

    @Test
    @DisplayName("CLIENTE nao pode remover profissional: 403")
    void clienteNaoRemove() {
        Profissional profissional =
                dados.profissional(tenant.getId(), futuroProfissional.getId(), "Visagismo");

        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when().delete("/profissionais/" + profissional.getId())
                .then().statusCode(403);
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
