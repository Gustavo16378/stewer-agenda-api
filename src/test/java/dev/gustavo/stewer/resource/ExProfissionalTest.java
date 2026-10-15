package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

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

/**
 * Desligar um profissional precisa tirar o acesso dele.
 *
 * <p>A remocao era soft delete so da FICHA: a role {@code PROFISSIONAL} ficava
 * no usuario e o login continuava funcionando. Como {@code /clientes} exige
 * apenas a role, um ex-funcionario seguia lendo nome, e-mail e telefone de toda
 * a clientela e as anotacoes de visagismo de todos os profissionais -- e o
 * ADMIN nao tinha endpoint nenhum para impedir.
 *
 * <p>Duas frentes, porque uma so nao resolve: rebaixar a role fecha os logins
 * futuros, e exigir ficha viva nos endpoints de cliente fecha o token de oito
 * horas que o ex-funcionario ja tinha no bolso.
 */
@QuarkusTest
@DisplayName("Profissional removido perde o acesso")
class ExProfissionalTest {

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Profissional ficha;
    private Usuario exFuncionario;
    private Usuario cliente;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        dados.usuario(tenant.getId(), "Gabriel Admin", "admin@stewer.dev", Role.ADMIN);
        tokenAdmin = login("admin@stewer.dev");

        exFuncionario = dados.usuario(tenant.getId(), "Bruno Visagista", "bruno@stewer.dev",
                Role.PROFISSIONAL);
        ficha = dados.profissional(tenant.getId(), exFuncionario.getId(), "Visagismo");

        cliente = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev",
                Role.CLIENTE);
    }

    @Test
    @DisplayName("o token que ele ja tinha para de abrir a base de clientes")
    void tokenAntigoNaoAbreMaisAListaDeClientes() {
        String tokenDele = login("bruno@stewer.dev");

        // Antes de ser desligado, enxerga a clientela.
        given().header("Authorization", "Bearer " + tokenDele)
                .when().get("/clientes")
                .then().statusCode(200);

        removerFicha();

        // Mesmo token, depois do desligamento.
        given().header("Authorization", "Bearer " + tokenDele)
                .when().get("/clientes")
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + tokenDele)
                .when().get("/clientes/{id}", cliente.getId())
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + tokenDele)
                .when().get("/clientes/{id}/anotacoes", cliente.getId())
                .then().statusCode(403);
    }

    @Test
    @DisplayName("ao entrar de novo, ele ja nao e mais profissional")
    void novoLoginVemComoCliente() {
        removerFicha();

        given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "bruno@stewer.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .body("usuario.role", equalTo("CLIENTE"));
    }

    @Test
    @DisplayName("quem continua na equipe nao e afetado")
    void profissionalAtivoSegueComAcesso() {
        Usuario outro = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev",
                Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), outro.getId(), "Visagismo");
        String tokenDoOutro = login("stewer@stewer.dev");

        removerFicha();

        given().header("Authorization", "Bearer " + tokenDoOutro)
                .when().get("/clientes")
                .then().statusCode(200);
    }

    @Test
    @DisplayName("o ADMIN nao precisa de ficha para ver a clientela")
    void adminContinuaVendoAClientela() {
        removerFicha();

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/clientes")
                .then().statusCode(200);
    }

    // ------------------------------------------------------------------

    private void removerFicha() {
        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().delete("/profissionais/{id}", ficha.getId())
                .then().statusCode(204);
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
