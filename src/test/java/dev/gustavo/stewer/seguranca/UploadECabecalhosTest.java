package dev.gustavo.stewer.seguranca;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

/**
 * Upload e cabecalhos (OWASP A04, A05): arquivo disfarcado de foto nao entra, a resposta
 * diz ao navegador para nao adivinhar tipo nem deixar embutir, e erro nao vaza detalhe
 * interno.
 */
@QuarkusTest
@DisplayName("seguranca -- upload disfarcado, cabecalhos e erros")
class UploadECabecalhosTest {

    @Inject
    DadosDeTeste dados;

    private String admin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant t = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(t.getId(), "Admin", "admin@x.dev", Role.ADMIN);
        admin = given().contentType(ContentType.JSON)
                .body(Map.of("email", "admin@x.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
    }

    private int enviar(String tipo, byte[] conteudo) {
        return given().header("Authorization", "Bearer " + admin).contentType(ContentType.JSON)
                .body(Map.of("tipo", "TRABALHO", "tipoConteudo", tipo,
                        "conteudoBase64", Base64.getEncoder().encodeToString(conteudo)))
                .when().post("/estudio/fotos").then().extract().statusCode();
    }

    @Test
    @DisplayName("HTML, SVG e script dizendo que sao JPEG, PNG ou WebP: recusados")
    void disfarcados() {
        byte[] html = "<html><script>alert(document.cookie)</script></html>".getBytes(StandardCharsets.UTF_8);
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg' onload='alert(1)'/>".getBytes(StandardCharsets.UTF_8);
        for (String tipo : new String[] { "image/jpeg", "image/png", "image/webp" }) {
            org.junit.jupiter.api.Assertions.assertEquals(422, enviar(tipo, html), tipo + " com HTML");
            org.junit.jupiter.api.Assertions.assertEquals(422, enviar(tipo, svg), tipo + " com SVG");
        }
        org.junit.jupiter.api.Assertions.assertEquals(422, enviar("image/svg+xml", svg));
        org.junit.jupiter.api.Assertions.assertEquals(422, enviar("text/html", html));
        // e a foto de verdade entra
        org.junit.jupiter.api.Assertions.assertEquals(201,
                enviar("image/png", new byte[] { (byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10 }));
    }

    @Test
    @DisplayName("toda resposta leva os cabecalhos de seguranca; dado pessoal nao fica em cache")
    void cabecalhos() {
        given().header("Authorization", "Bearer " + admin).when().get("/auth/eu").then().statusCode(200)
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("Content-Security-Policy", containsString("default-src 'none'"))
                .header("Strict-Transport-Security", containsString("max-age="))
                .header("Referrer-Policy", "no-referrer")
                .header("Cache-Control", "no-store");
        given().when().get("/rota-que-nao-existe").then().statusCode(404)
                .header("X-Content-Type-Options", "nosniff");
    }

    @Test
    @DisplayName("site de fora nao recebe permissao de CORS")
    void corsDeFora() {
        given().header("Origin", "https://site-malicioso.example").header("Access-Control-Request-Method", "POST")
                .when().options("/auth/login").then()
                .header("Access-Control-Allow-Origin", nullValue());
    }

    @Test
    @DisplayName("JSON quebrado e erro de validacao respondem sem pilha nem nome de classe")
    void erroSemDetalheInterno() {
        given().header("Authorization", "Bearer " + admin).contentType(ContentType.JSON).body("{\"nome\": ")
                .when().put("/estudio").then().statusCode(400)
                .body(not(containsString("Exception"))).body(not(containsString("at dev.gustavo")));
        given().contentType(ContentType.JSON).body(Map.of("email", "nao-e-email", "senha", "x"))
                .when().post("/auth/registrar").then().statusCode(400)
                .body(not(containsString("Exception"))).body("codigo", not(equalTo(null)));
    }
}
