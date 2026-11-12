package dev.gustavo.stewer.seguranca;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Caixa cinza: faz o pedido pela API, como o app, e depois abre o banco para conferir
 * o que foi gravado. Coisas que a resposta HTTP nao mostra mas que precisam ser verdade.
 */
@QuarkusTest
@DisplayName("caixa cinza -- o que a API grava no banco")
class CaixaCinzaTest {

    @Inject
    DadosDeTeste dados;

    @Inject
    EntityManager em;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant t = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(t.getId(), "Admin", "admin@x.dev", Role.ADMIN);
    }

    private Object valor(String sql) {
        return em.createNativeQuery(sql).getSingleResult();
    }

    @Test
    @DisplayName("a senha vira hash bcrypt; nunca fica o texto que a pessoa digitou")
    void senhaViraHash() {
        given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Joana", "email", "joana@x.dev", "senha", "minha-senha-123"))
                .when().post("/auth/registrar").then().statusCode(201);
        String hash = (String) valor("select senha_hash from usuario where email = 'joana@x.dev'");
        assertTrue(hash.startsWith("$2"), "nao e bcrypt: " + hash.substring(0, 4));
        assertNotEquals("minha-senha-123", hash);
        assertTrue(!hash.contains("minha-senha-123"));
    }

    @Test
    @DisplayName("o cadastro entra na auditoria com quem fez, e o e-mail e guardado em minusculas")
    void auditoriaECadastro() {
        given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Marcos", "email", "MARCOS@X.dev", "senha", "segredo123",
                        "dataNascimento", "1990-05-04"))
                .when().post("/auth/registrar").then().statusCode(201);
        assertEquals("marcos@x.dev", valor("select email from usuario where nome = 'Marcos'"));
        Object autor = valor("select autor_nome from evento_auditoria where acao = 'CADASTROU'");
        assertEquals("Marcos", autor);
        assertNotNull(valor("select data_nascimento from ficha_cliente f join usuario u on u.id = f.cliente_id "
                + "where u.email = 'marcos@x.dev'"));
    }

    @Test
    @DisplayName("servico removido some do catalogo mas a linha fica (soft delete), com a data")
    void softDelete() {
        String admin = given().contentType(ContentType.JSON)
                .body(Map.of("email", "admin@x.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
        String id = given().header("Authorization", "Bearer " + admin).contentType(ContentType.JSON)
                .body(Map.of("nome", "Pigmentação", "duracaoMinutos", 30, "precoCentavos", 5000))
                .when().post("/servicos").then().statusCode(201).extract().path("id");
        given().header("Authorization", "Bearer " + admin).when().delete("/servicos/" + id).then().statusCode(204);
        assertNotNull(valor("select deleted_at from servico where id = '" + id + "'"));
        given().header("Authorization", "Bearer " + admin).when().get("/servicos/" + id).then().statusCode(404);
    }
}
