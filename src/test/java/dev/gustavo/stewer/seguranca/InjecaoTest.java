package dev.gustavo.stewer.seguranca;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
 * Injecao (OWASP A03), caixa cinza: manda os ataques pela API, como um atacante faria,
 * e depois abre o banco para conferir que nenhuma tabela mudou e que o texto foi
 * guardado como texto.
 */
@QuarkusTest
@DisplayName("seguranca -- SQL injection e texto malicioso")
class InjecaoTest {

    static final List<String> ATAQUES = List.of(
            "' OR '1'='1", "' OR 1=1 --", "'; DROP TABLE usuario; --", "\" OR \"\"=\"",
            "1; DELETE FROM agendamento", "' UNION SELECT senha_hash FROM usuario --",
            "%' AND pg_sleep(3) --", "admin'--", "') OR ('a'='a", "\\'; SELECT 1; --",
            "<script>alert(1)</script>", "${jndi:ldap://x/a}", "{{7*7}}", "../../etc/passwd",
            "\u0000", "' || (SELECT current_user) || '");

    @Inject
    DadosDeTeste dados;

    @Inject
    EntityManager em;

    private String admin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant t = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(t.getId(), "Admin", "admin@x.dev", Role.ADMIN);
        dados.usuario(t.getId(), "Joana", "joana@x.dev", Role.CLIENTE);
        admin = given().contentType(ContentType.JSON)
                .body(Map.of("email", "admin@x.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
    }

    private long contar(String tabela) {
        return ((Number) em.createNativeQuery("select count(*) from " + tabela).getSingleResult()).longValue();
    }

    @Test
    @DisplayName("ataques na busca, no login e no cadastro: nada de 500, nenhuma tabela muda")
    void nadaMuda() {
        long usuarios = contar("usuario");
        long agendamentos = contar("agendamento");
        for (String a : ATAQUES) {
            for (String rota : List.of("/usuarios?busca=", "/clientes?busca=")) {
                int s = given().header("Authorization", "Bearer " + admin).queryParam("busca", a)
                        .when().get(rota.substring(0, rota.indexOf('?'))).then().extract().statusCode();
                assertEquals(200, s, rota + a);
            }
            int login = given().contentType(ContentType.JSON).body(Map.of("email", a, "senha", a))
                    .when().post("/auth/login").then().extract().statusCode();
            assertTrue(login == 400 || login == 401 || login == 429, "login com " + a + " deu " + login);
            int caminho = given().urlEncodingEnabled(false).header("Authorization", "Bearer " + admin)
                    .when().get("/clientes/" + java.net.URLEncoder.encode(a, java.nio.charset.StandardCharsets.UTF_8))
                    .then().extract().statusCode();
            assertTrue(caminho >= 400 && caminho < 500, "id " + a + " deu " + caminho);
        }
        assertEquals(usuarios, contar("usuario"));
        assertEquals(agendamentos, contar("agendamento"));
    }

    @Test
    @DisplayName("busca com aspas so acha quem tem aspas no nome: o filtro nao vira OR")
    void buscaNaoViraOr() {
        int achados = given().header("Authorization", "Bearer " + admin).queryParam("busca", "' OR '1'='1")
                .when().get("/usuarios").then().statusCode(200).extract().path("size()");
        assertEquals(0, achados);
    }

    @Test
    @DisplayName("nome com HTML e guardado como texto, igual ao que veio")
    void htmlViraTexto() {
        String nome = "<img src=x onerror=alert(1)> Zé";
        given().contentType(ContentType.JSON)
                .body(Map.of("nome", nome, "email", "xss@x.dev", "senha", "segredo123"))
                .when().post("/auth/registrar").then().statusCode(201);
        String guardado = (String) em.createNativeQuery("select nome from usuario where email = 'xss@x.dev'")
                .getSingleResult();
        assertEquals(nome, guardado);
    }
}
