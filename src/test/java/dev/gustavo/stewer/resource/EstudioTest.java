package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

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

/** O estudio editavel pelo admin, lido por qualquer um, e as fotos de exemplo dos servicos. */
@QuarkusTest
@DisplayName("estudio -- dados, retrato, fotos do trabalho e fotos dos servicos")
class EstudioTest {

    private static final byte[] BYTES = { (byte) 0xFF, (byte) 0xD8, 1, 2, 3 };
    private static final String JPEG = Base64.getEncoder().encodeToString(BYTES);

    @Inject
    DadosDeTeste dados;

    private Servico corte;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.ADMIN);
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        corte = dados.servico(tenant.getId(), "Corte", 60, 9000);
    }

    @Test
    @DisplayName("sem nada salvo, responde vazio e sem login")
    void vazio() {
        given().when().get("/estudio?tenantSlug=stewer-gabriel")
                .then().statusCode(200)
                .body("endereco", nullValue())
                .body("retrato", nullValue())
                .body("fotos", hasSize(0));
    }

    @Test
    @DisplayName("admin salva endereco e contatos; o link do mapa preenche as coordenadas")
    void salva() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nomeLocal", "Conciso");
        corpo.put("endereco", "204 Sul, Alameda 10, Palmas");
        corpo.put("linkMapa", "https://www.google.com/maps/place/Conciso/@-10.195029,-48.328703,17z");
        corpo.put("whatsapp", "(63) 9 8511-6783");
        corpo.put("instagram", "https://www.instagram.com/stewergabriel/");
        given().header("Authorization", "Bearer " + login("stewer@stewer.dev")).contentType(ContentType.JSON)
                .body(corpo).when().put("/estudio")
                .then().statusCode(200)
                .body("nomeLocal", equalTo("Conciso"))
                .body("latitude", equalTo(-10.195029f))
                .body("longitude", equalTo(-48.328703f))
                .body("whatsapp", equalTo("63985116783"))
                .body("instagram", equalTo("stewergabriel"));

        given().when().get("/estudio").then().statusCode(200).body("endereco", equalTo("204 Sul, Alameda 10, Palmas"));
        given().header("Authorization", "Bearer " + login("stewer@stewer.dev"))
                .when().get("/auditoria?entidade=ESTUDIO")
                .then().statusCode(200).body("[0].resumo", equalTo("Atualizou os dados do estúdio"));
    }

    @Test
    @DisplayName("cliente nao edita o estudio")
    void clienteNaoEdita() {
        given().header("Authorization", "Bearer " + login("joana@exemplo.dev")).contentType(ContentType.JSON)
                .body(Map.of("endereco", "outro lugar")).when().put("/estudio")
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + login("joana@exemplo.dev")).contentType(ContentType.JSON)
                .body(foto("TRABALHO")).when().post("/estudio/fotos")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("retrato novo troca o antigo, e a foto sai como imagem com cache longo")
    void retrato() {
        String admin = login("stewer@stewer.dev");
        given().header("Authorization", "Bearer " + admin).contentType(ContentType.JSON)
                .body(foto("RETRATO")).when().post("/estudio/fotos").then().statusCode(201);
        String segundo = given().header("Authorization", "Bearer " + admin).contentType(ContentType.JSON)
                .body(foto("RETRATO")).when().post("/estudio/fotos")
                .then().statusCode(201).extract().path("id");

        given().when().get("/estudio").then().body("retrato.id", equalTo(segundo)).body("fotos", hasSize(0));
        byte[] recebido = given().when().get("/imagens/" + segundo)
                .then().statusCode(200)
                .header("Content-Type", equalTo("image/jpeg"))
                .header("Cache-Control", equalTo("public, max-age=31536000, immutable"))
                .extract().asByteArray();
        org.junit.jupiter.api.Assertions.assertArrayEquals(BYTES, recebido);
    }

    @Test
    @DisplayName("fotos do trabalho em ordem, mudam de lugar e somem ao apagar")
    void fotosDoTrabalho() {
        String admin = login("stewer@stewer.dev");
        String a = novaFoto(admin, "TRABALHO");
        String b = novaFoto(admin, "TRABALHO");
        String c = novaFoto(admin, "TRABALHO");
        given().when().get("/estudio").then().body("fotos.id", contains(a, b, c));

        given().header("Authorization", "Bearer " + admin).when().put("/estudio/fotos/" + c + "/posicao/0")
                .then().statusCode(200);
        given().when().get("/estudio").then().body("fotos.id", contains(c, a, b));

        given().header("Authorization", "Bearer " + admin).when().delete("/estudio/fotos/" + a)
                .then().statusCode(204);
        given().when().get("/estudio").then().body("fotos.id", contains(c, b));
        given().when().get("/imagens/" + a).then().statusCode(404);
    }

    @Test
    @DisplayName("foto de exemplo do servico aparece no catalogo e sai ao apagar")
    void fotosDoServico() {
        String admin = login("stewer@stewer.dev");
        String id = given().header("Authorization", "Bearer " + admin).contentType(ContentType.JSON)
                .body(foto(null)).when().post("/servicos/" + corte.getId() + "/fotos")
                .then().statusCode(201).extract().path("id");

        given().header("Authorization", "Bearer " + login("joana@exemplo.dev"))
                .when().get("/servicos")
                .then().statusCode(200).body("[0].fotos", contains(id));
        given().when().get("/imagens/" + id).then().statusCode(200);

        given().header("Authorization", "Bearer " + admin)
                .when().delete("/servicos/" + corte.getId() + "/fotos/" + id)
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + admin)
                .when().get("/servicos/" + corte.getId())
                .then().body("fotos", hasSize(0));
    }

    @Test
    @DisplayName("cliente informa o aniversario ao criar a conta e ve em Editar dados")
    void nascimentoNoCadastro() {
        String token = given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Marcos Lima", "email", "marcos@exemplo.dev", "senha", "segredo123",
                        "dataNascimento", "1994-11-20"))
                .when().post("/auth/registrar")
                .then().statusCode(201).body("usuario.dataNascimento", equalTo("1994-11-20"))
                .extract().path("token");

        given().header("Authorization", "Bearer " + token).when().get("/auth/eu")
                .then().statusCode(200).body("dataNascimento", equalTo("1994-11-20"));

        // sem o campo, mantem; com o campo, troca
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("nome", "Marcos Lima", "email", "marcos@exemplo.dev"))
                .when().put("/auth/eu")
                .then().statusCode(200).body("usuario.dataNascimento", equalTo("1994-11-20"));
        given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(Map.of("nome", "Marcos Lima", "email", "marcos@exemplo.dev", "dataNascimento", "1995-01-02"))
                .when().put("/auth/eu")
                .then().statusCode(200).body("usuario.dataNascimento", equalTo("1995-01-02"));

        given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Futuro", "email", "futuro@exemplo.dev", "senha", "segredo123",
                        "dataNascimento", "2999-01-01"))
                .when().post("/auth/registrar")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("le coordenadas dos formatos de link do Google Maps")
    void linksDoMapa() {
        assertCoordenadas("https://maps.google.com/?q=-10.19,-48.32", -10.19, -48.32);
        assertCoordenadas("https://www.google.com/maps/search/?api=1&query=-10.19%2C-48.32", -10.19, -48.32);
        assertCoordenadas("https://www.google.com/maps/place/X/@-1.5,-2.5,17z/data=!3d-10.195!4d-48.328", -10.195,
                -48.328);
        org.junit.jupiter.api.Assertions.assertNull(
                dev.gustavo.stewer.service.EstudioServiceTestAcesso.coordenadas("https://maps.app.goo.gl/abc"));
    }

    private static void assertCoordenadas(String link, double lat, double lon) {
        double[] c = dev.gustavo.stewer.service.EstudioServiceTestAcesso.coordenadas(link);
        org.junit.jupiter.api.Assertions.assertNotNull(c, link);
        org.junit.jupiter.api.Assertions.assertEquals(lat, c[0], 1e-9);
        org.junit.jupiter.api.Assertions.assertEquals(lon, c[1], 1e-9);
    }

    private String novaFoto(String token, String tipo) {
        return given().header("Authorization", "Bearer " + token).contentType(ContentType.JSON)
                .body(foto(tipo)).when().post("/estudio/fotos")
                .then().statusCode(201).body("id", notNullValue()).extract().path("id");
    }

    private static Map<String, Object> foto(String tipo) {
        Map<String, Object> m = new HashMap<>();
        if (tipo != null) {
            m.put("tipo", tipo);
        }
        m.put("tipoConteudo", "image/jpeg");
        m.put("conteudoBase64", JPEG);
        return m;
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }

}
