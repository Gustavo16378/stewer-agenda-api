package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import java.util.Base64;
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

@QuarkusTest
@DisplayName("foto do profissional -- a do perfil por padrao, ou uma so da equipe")
class FotoDoProfissionalTest {

    private static final byte[] BYTES = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 7, 8, 9 };
    private static final String JPEG = Base64.getEncoder().encodeToString(BYTES);

    @Inject
    DadosDeTeste dados;

    private Profissional stewer;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario admin = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.ADMIN);
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        stewer = dados.profissional(tenant.getId(), admin.getId(), "Visagismo");
    }

    @Test
    @DisplayName("sem foto propria, a ficha vem com fotoId nulo (vale a do perfil)")
    void semFotoPropria() {
        given().header("Authorization", "Bearer " + login("stewer@stewer.dev"))
                .when().get("/profissionais/" + stewer.getId())
                .then().statusCode(200).body("fotoId", nullValue());
    }

    @Test
    @DisplayName("admin envia a foto, ela fica publica pelo id e troca a anterior")
    void enviaETroca() {
        String token = login("stewer@stewer.dev");
        String primeira = enviar(token).then().statusCode(200).body("fotoId", notNullValue())
                .extract().path("fotoId");

        byte[] recebido = given().when().get("/imagens/" + primeira)
                .then().statusCode(200).extract().asByteArray();
        assertArrayEquals(BYTES, recebido);

        String segunda = enviar(token).then().statusCode(200).extract().path("fotoId");
        given().when().get("/imagens/" + primeira).then().statusCode(404);
        given().when().get("/imagens/" + segunda).then().statusCode(200);

        // a lista da equipe, que o cliente ve, ja traz a foto nova
        given().header("Authorization", "Bearer " + login("joana@exemplo.dev"))
                .when().get("/profissionais")
                .then().statusCode(200).body("[0].fotoId", equalTo(segunda));
    }

    @Test
    @DisplayName("tirar a foto volta para a do perfil e apaga a imagem")
    void tira() {
        String token = login("stewer@stewer.dev");
        String id = enviar(token).then().statusCode(200).extract().path("fotoId");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/profissionais/" + stewer.getId() + "/foto")
                .then().statusCode(200).body("fotoId", nullValue());
        given().when().get("/imagens/" + id).then().statusCode(404);
    }

    @Test
    @DisplayName("a foto da equipe nao sai pela rota das fotos do estudio")
    void naoSaiPeloEstudio() {
        String token = login("stewer@stewer.dev");
        String id = enviar(token).then().statusCode(200).extract().path("fotoId");

        given().header("Authorization", "Bearer " + token)
                .when().delete("/estudio/fotos/" + id)
                .then().statusCode(404);
        given().when().get("/imagens/" + id).then().statusCode(200);
    }

    @Test
    @DisplayName("cliente nao troca foto de ninguem; arquivo que nao e imagem e recusado")
    void recusas() {
        given().header("Authorization", "Bearer " + login("joana@exemplo.dev"))
                .contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/jpeg", "conteudoBase64", JPEG))
                .when().put("/profissionais/" + stewer.getId() + "/foto")
                .then().statusCode(403);

        String falso = Base64.getEncoder().encodeToString("<script>alert(1)</script>".getBytes());
        given().header("Authorization", "Bearer " + login("stewer@stewer.dev"))
                .contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/jpeg", "conteudoBase64", falso))
                .when().put("/profissionais/" + stewer.getId() + "/foto")
                .then().statusCode(422);
    }

    private io.restassured.response.Response enviar(String token) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/jpeg", "conteudoBase64", JPEG))
                .when().put("/profissionais/" + stewer.getId() + "/foto");
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
