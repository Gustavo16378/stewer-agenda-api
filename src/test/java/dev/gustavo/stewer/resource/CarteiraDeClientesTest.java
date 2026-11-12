package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.StatusAgendamento;
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
 * O que serve para guardar a clientela: a ficha (aniversario, origem, etiquetas,
 * visagismo), as fotos, os numeros de cada cliente, os aniversariantes e os
 * sumidos.
 */
@QuarkusTest
@DisplayName("carteira de clientes -- ficha, fotos, numeros, aniversariantes e sumidos")
class CarteiraDeClientesTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.now(ZoneOffset.UTC);

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Usuario joana;
    private Usuario marcos;
    private Usuario bia;
    private Profissional gabriel;
    private Servico corte;
    private String tokenGabriel;
    private String tokenAdmin;
    private String tokenJoana;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario u = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev", Role.PROFISSIONAL);
        gabriel = dados.profissional(tenant.getId(), u.getId(), "Visagismo");
        dados.usuario(tenant.getId(), "Ana", "ana@stewer.dev", Role.ADMIN);
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        marcos = dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);
        bia = dados.usuario(tenant.getId(), "Bia Souza", "bia@exemplo.dev", Role.CLIENTE);
        corte = dados.servico(tenant.getId(), "Corte", 60, 9000L);

        tokenGabriel = login("stewer@stewer.dev");
        tokenAdmin = login("ana@stewer.dev");
        tokenJoana = login("joana@exemplo.dev");
    }

    @Test
    @DisplayName("ficha vazia ate alguem salvar; salvar devolve e a ficha aparece no detalhe")
    void salvaEFichaApareceNoDetalhe() {
        detalhe(joana).body("ficha.etiquetas", hasSize(0)).body("ficha.atualizadaEm", nullValue());

        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("dataNascimento", "1994-11-20", "origem", "Instagram",
                        "etiquetas", List.of("VIP", " VIP ", "", "Indica amigos"),
                        "formatoRosto", "Oval", "manter", "Volume em cima", "evitar", "Laterais raspadas"))
                .when().put("/clientes/" + joana.getId() + "/ficha")
                .then().statusCode(200)
                .body("etiquetas", contains("VIP", "Indica amigos"))
                .body("atualizadaEm", notNullValue());

        detalhe(joana)
                .body("ficha.dataNascimento", equalTo("1994-11-20"))
                .body("ficha.origem", equalTo("Instagram"))
                .body("ficha.formatoRosto", equalTo("Oval"))
                .body("ficha.evitar", equalTo("Laterais raspadas"));
    }

    @Test
    @DisplayName("ficha valida: aniversario no futuro e etiqueta longa demais viram 400")
    void fichaValida() {
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("dataNascimento", LocalDate.now().plusDays(2).toString()))
                .when().put("/clientes/" + joana.getId() + "/ficha")
                .then().statusCode(400);
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("etiquetas", List.of("x".repeat(41))))
                .when().put("/clientes/" + joana.getId() + "/ficha")
                .then().statusCode(400);
    }

    @Test
    @DisplayName("cliente nao le nem escreve ficha; id de outro papel e 404")
    void acessoDaFicha() {
        given().header("Authorization", "Bearer " + tokenJoana).contentType(ContentType.JSON)
                .body(Map.of("origem", "Eu mesma"))
                .when().put("/clientes/" + joana.getId() + "/ficha")
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + tokenAdmin).contentType(ContentType.JSON)
                .body(Map.of("origem", "x"))
                .when().put("/clientes/" + gabriel.getUsuario().getId() + "/ficha")
                .then().statusCode(404);
    }

    @Test
    @DisplayName("numeros do cliente: gasto, ticket, ritmo, faltas e preferidos")
    void numerosDoCliente() {
        agendar(joana, AGORA.minusDays(70), StatusAgendamento.CONCLUIDO);
        agendar(joana, AGORA.minusDays(40), StatusAgendamento.CONCLUIDO);
        agendar(joana, AGORA.minusDays(10), StatusAgendamento.CONCLUIDO);
        agendar(joana, AGORA.minusDays(5), StatusAgendamento.NAO_COMPARECEU);

        detalhe(joana)
                .body("metricas.visitas", equalTo(3))
                .body("metricas.totalGastoCentavos", equalTo(27000))
                .body("metricas.ticketMedioCentavos", equalTo(9000))
                .body("metricas.intervaloMedioDias", equalTo(30))
                .body("metricas.diasDesdeUltimaVisita", equalTo(10))
                .body("metricas.faltas", equalTo(1))
                .body("metricas.servicoPreferido", equalTo("Corte"))
                .body("metricas.profissionalPreferido", equalTo("Stewer Gabriel"));
    }

    @Test
    @DisplayName("foto: sobe, lista sem a imagem, devolve a imagem e apaga")
    void fotos() {
        byte[] jpeg = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3 };
        String id = given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("momento", "DEPOIS", "legenda", "Corte novo", "tipoConteudo", "image/jpeg",
                        "conteudoBase64", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg)))
                .when().post("/clientes/" + joana.getId() + "/fotos")
                .then().statusCode(201)
                .body("momento", equalTo("DEPOIS"))
                .body("tamanhoBytes", equalTo(6))
                .body("autorNome", equalTo("Stewer Gabriel"))
                .extract().path("id");

        fotosDe(joana).body("$", hasSize(1)).body("[0].legenda", equalTo("Corte novo"))
                .body("[0]", not(org.hamcrest.Matchers.hasKey("dataUrl")));

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/clientes/" + joana.getId() + "/fotos/" + id)
                .then().statusCode(200)
                .body("dataUrl", startsWith("data:image/jpeg;base64,/9j/"));

        // foto de um cliente nao abre pela rota de outro
        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/clientes/" + marcos.getId() + "/fotos/" + id)
                .then().statusCode(404);

        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().delete("/clientes/" + joana.getId() + "/fotos/" + id)
                .then().statusCode(204);
        fotosDe(joana).body("$", hasSize(0));
    }

    @Test
    @DisplayName("foto recusada: tipo errado, base64 invalido e grande demais")
    void fotoRecusada() {
        postarFoto("application/pdf", "AAAA").statusCode(422).body("codigo", equalTo("FOTO_TIPO_INVALIDO"));
        postarFoto("image/jpeg", "@@nao e base64@@").statusCode(422).body("codigo", equalTo("FOTO_INVALIDA"));
        String grande = Base64.getEncoder().encodeToString(new byte[1_500_001]);
        postarFoto("image/jpeg", grande).statusCode(422).body("codigo", equalTo("FOTO_GRANDE_DEMAIS"));
    }

    @Test
    @DisplayName("aniversariantes do mes, em ordem de dia")
    void aniversariantes() {
        salvarNascimento(joana, "1990-03-20");
        salvarNascimento(marcos, "1985-03-02");
        salvarNascimento(bia, "1999-07-10");

        given().header("Authorization", "Bearer " + tokenGabriel).queryParam("mes", 3)
                .when().get("/clientes/aniversariantes")
                .then().statusCode(200)
                .body("nome", contains("Marcos Lima", "Joana Ribeiro"));
        given().header("Authorization", "Bearer " + tokenGabriel).queryParam("mes", 13)
                .when().get("/clientes/aniversariantes")
                .then().statusCode(422);
    }

    @Test
    @DisplayName("sumidos: passou do proprio ritmo e nao tem nada marcado")
    void sumidos() {
        // Joana volta a cada 20 dias e esta ha 40 sem vir: sumiu
        agendar(joana, AGORA.minusDays(80), StatusAgendamento.CONCLUIDO);
        agendar(joana, AGORA.minusDays(60), StatusAgendamento.CONCLUIDO);
        agendar(joana, AGORA.minusDays(40), StatusAgendamento.CONCLUIDO);
        // Marcos veio uma vez ha 30 dias: ainda nao (limite de 60 com uma visita so)
        agendar(marcos, AGORA.minusDays(30), StatusAgendamento.CONCLUIDO);
        // Bia sumiria, mas ja tem horario marcado
        agendar(bia, AGORA.minusDays(120), StatusAgendamento.CONCLUIDO);
        agendar(bia, AGORA.plusDays(3), StatusAgendamento.AGENDADO);

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/clientes/sumidos")
                .then().statusCode(200)
                .body("nome", contains("Joana Ribeiro"))
                .body("[0].intervaloHabitualDias", equalTo(20))
                .body("[0].diasSemVir", equalTo(40));
    }

    @Test
    @DisplayName("excluir a conta leva a ficha e as fotos junto (LGPD)")
    void exclusaoLevaFicha() {
        salvarNascimento(joana, "1990-03-20");
        postarFoto("image/jpeg", Base64.getEncoder().encodeToString(
                new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 2 })).statusCode(201);

        given().header("Authorization", "Bearer " + tokenJoana).contentType(ContentType.JSON)
                .body(Map.of("senha", DadosDeTeste.SENHA))
                .when().post("/auth/eu/excluir")
                .then().statusCode(204);

        given().header("Authorization", "Bearer " + tokenGabriel).queryParam("mes", 3)
                .when().get("/clientes/aniversariantes")
                .then().statusCode(200).body("$", hasSize(0));
        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/auditoria")
                .then().statusCode(200)
                .body("resumo", not(hasItem(org.hamcrest.Matchers.containsString("Joana Ribeiro"))));
    }

    private io.restassured.response.ValidatableResponse detalhe(Usuario cliente) {
        return given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/clientes/" + cliente.getId())
                .then().statusCode(200);
    }

    private io.restassured.response.ValidatableResponse fotosDe(Usuario cliente) {
        return given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/clientes/" + cliente.getId() + "/fotos")
                .then().statusCode(200);
    }

    private io.restassured.response.ValidatableResponse postarFoto(String tipo, String base64) {
        return given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("momento", "ANTES", "tipoConteudo", tipo, "conteudoBase64", base64))
                .when().post("/clientes/" + joana.getId() + "/fotos")
                .then();
    }

    private void salvarNascimento(Usuario cliente, String data) {
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("dataNascimento", data))
                .when().put("/clientes/" + cliente.getId() + "/ficha")
                .then().statusCode(200);
    }

    private void agendar(Usuario cliente, OffsetDateTime inicio, StatusAgendamento status) {
        dados.agendamento(tenant.getId(), cliente.getId(), gabriel.getId(), corte.getId(),
                inicio, inicio.plusHours(1), status);
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
