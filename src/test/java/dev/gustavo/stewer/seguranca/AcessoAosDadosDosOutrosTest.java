package dev.gustavo.stewer.seguranca;

import static io.restassured.RestAssured.given;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
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
 * Controle de acesso por registro (OWASP A01, IDOR): ter login nao basta. Um cliente nao
 * ve, nao remarca e nao cancela o horario de outro; nao ve a foto nem a ficha de outro;
 * e um estudio nao enxerga nada de outro estudio.
 */
@QuarkusTest
@DisplayName("seguranca -- ninguem mexe no que e dos outros")
class AcessoAosDadosDosOutrosTest {

    @Inject
    DadosDeTeste dados;

    private Usuario joana;
    private Usuario marcos;
    private Agendamento daJoana;
    private String tokenMarcos;
    private String adminDeOutroEstudio;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant t = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario stewer = dados.usuario(t.getId(), "Stewer", "stewer@x.dev", Role.PROFISSIONAL);
        Profissional p = dados.profissional(t.getId(), stewer.getId(), "Visagismo");
        Servico corte = dados.servico(t.getId(), "Corte", 60, 9000);
        joana = dados.usuario(t.getId(), "Joana", "joana@x.dev", Role.CLIENTE);
        marcos = dados.usuario(t.getId(), "Marcos", "marcos@x.dev", Role.CLIENTE);
        OffsetDateTime amanha = OffsetDateTime.now(ZoneOffset.UTC).plusDays(2).withHour(15).withMinute(0)
                .withSecond(0).withNano(0);
        daJoana = dados.agendamento(t.getId(), joana.getId(), p.getId(), corte.getId(), amanha,
                amanha.plusHours(1), dev.gustavo.stewer.domain.StatusAgendamento.AGENDADO);
        tokenMarcos = login("marcos@x.dev");

        Tenant outro = dados.tenant("Outro estudio", "outro");
        dados.usuario(outro.getId(), "Dono", "dono@outro.dev", Role.ADMIN);
        adminDeOutroEstudio = given().contentType(ContentType.JSON)
                .body(Map.of("email", "dono@outro.dev", "senha", DadosDeTeste.SENHA, "tenantSlug", "outro"))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
    }

    @Test
    @DisplayName("cliente nao remarca nem cancela o horario de outro cliente, nem ve na lista dele")
    void horarioDeOutro() {
        int cancelar = given().header("Authorization", "Bearer " + tokenMarcos).contentType(ContentType.JSON)
                .body(Map.of("status", "CANCELADO")).when().patch("/agendamentos/" + daJoana.getId() + "/status")
                .then().extract().statusCode();
        org.junit.jupiter.api.Assertions.assertTrue(cancelar == 403 || cancelar == 404, "cancelar deu " + cancelar);
        int remarcar = given().header("Authorization", "Bearer " + tokenMarcos).contentType(ContentType.JSON)
                .body(Map.of("inicio", daJoana.getInicio().plusDays(1).toString()))
                .when().patch("/agendamentos/" + daJoana.getId() + "/horario").then().extract().statusCode();
        org.junit.jupiter.api.Assertions.assertTrue(remarcar == 403 || remarcar == 404, "remarcar deu " + remarcar);
        given().header("Authorization", "Bearer " + tokenMarcos).when().get("/agendamentos/meus")
                .then().statusCode(200).body("size()", org.hamcrest.Matchers.is(0));
        given().header("Authorization", "Bearer " + login("joana@x.dev")).when().get("/agendamentos/meus")
                .then().statusCode(200).body("[0].status", org.hamcrest.Matchers.equalTo("AGENDADO"));
    }

    @Test
    @DisplayName("cliente nao ve a foto de perfil de outro cliente")
    void fotoDeOutro() {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1 };
        given().header("Authorization", "Bearer " + login("joana@x.dev")).contentType(ContentType.JSON)
                .body(Map.of("tipoConteudo", "image/jpeg", "conteudoBase64",
                        Base64.getEncoder().encodeToString(jpeg)))
                .when().put("/auth/eu/foto").then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenMarcos).when().get("/usuarios/" + joana.getId() + "/foto")
                .then().statusCode(org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is(403),
                        org.hamcrest.Matchers.is(404)));
    }

    @Test
    @DisplayName("admin de outro estudio nao acha nem o cliente nem o horario deste")
    void outroEstudio() {
        given().header("Authorization", "Bearer " + adminDeOutroEstudio).when().get("/clientes/" + joana.getId())
                .then().statusCode(404);
        int cancelar = given().header("Authorization", "Bearer " + adminDeOutroEstudio).contentType(ContentType.JSON)
                .body(Map.of("status", "CANCELADO")).when().patch("/agendamentos/" + daJoana.getId() + "/status")
                .then().extract().statusCode();
        org.junit.jupiter.api.Assertions.assertEquals(404, cancelar);
        given().header("Authorization", "Bearer " + adminDeOutroEstudio).queryParam("busca", "Joana")
                .when().get("/usuarios").then().statusCode(200).body("size()", org.hamcrest.Matchers.is(0));
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
    }
}
