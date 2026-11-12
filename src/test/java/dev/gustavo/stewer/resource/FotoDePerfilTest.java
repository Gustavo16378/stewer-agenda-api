package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.util.Base64;
import java.util.Map;

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

/** Foto de perfil: cada um poe a sua; o cliente ve a de quem atende, nunca a de outro cliente. */
@QuarkusTest
@DisplayName("foto de perfil -- trocar, tirar e quem ve")
class FotoDePerfilTest {

    private static final String JPEG = "data:image/jpeg;base64,"
            + Base64.getEncoder().encodeToString(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 9 });

    @Inject
    DadosDeTeste dados;

    private Usuario gabriel;
    private Usuario joana;
    private Usuario marcos;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        gabriel = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), gabriel.getId(), "Visagismo");
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        marcos = dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);
    }

    @Test
    @DisplayName("troca, aparece a versao no perfil e na ficha de profissional, e tira")
    void trocaETira() {
        String token = login("stewer@stewer.dev");
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/jpeg", "conteudoBase64", JPEG))
                .when().put("/auth/eu/foto")
                .then().statusCode(200).body("fotoVersao", notNullValue());

        given().header("Authorization", "Bearer " + login("joana@exemplo.dev"))
                .when().get("/profissionais")
                .then().statusCode(200).body("[0].fotoVersao", notNullValue());

        given().header("Authorization", "Bearer " + token)
                .when().delete("/auth/eu/foto")
                .then().statusCode(200).body("fotoVersao", nullValue());
        given().header("Authorization", "Bearer " + token)
                .when().get("/usuarios/" + gabriel.getId() + "/foto")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("cliente ve a foto de quem atende e a propria; a de outro cliente nao")
    void quemVe() {
        String tokenJoana = login("joana@exemplo.dev");
        String tokenMarcos = login("marcos@exemplo.dev");
        String tokenGabriel = login("stewer@stewer.dev");
        for (String t : new String[] { tokenJoana, tokenMarcos, tokenGabriel }) {
            given().header("Authorization", "Bearer " + t).contentType(ContentType.JSON)
                    .body(Map.of("tipoConteudo", "image/jpeg", "conteudoBase64", JPEG))
                    .when().put("/auth/eu/foto").then().statusCode(200);
        }

        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/usuarios/" + gabriel.getId() + "/foto")
                .then().statusCode(200).body("dataUrl", startsWith("data:image/jpeg;base64,"));
        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/usuarios/" + joana.getId() + "/foto")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/usuarios/" + marcos.getId() + "/foto")
                .then().statusCode(404);
        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/usuarios/" + marcos.getId() + "/foto")
                .then().statusCode(200).body("id", equalTo(marcos.getId().toString()));
    }

    @Test
    @DisplayName("tipo errado ou grande demais e recusado")
    void recusa() {
        String token = login("joana@exemplo.dev");
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/gif", "conteudoBase64", JPEG))
                .when().put("/auth/eu/foto")
                .then().statusCode(422).body("codigo", equalTo("FOTO_TIPO_INVALIDO"));
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/jpeg",
                        "conteudoBase64", Base64.getEncoder().encodeToString(new byte[600_001])))
                .when().put("/auth/eu/foto")
                .then().statusCode(422).body("codigo", equalTo("FOTO_GRANDE_DEMAIS"));
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
