package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.util.DadosDeTeste;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Um agendamento com mais de um servico: corte e barba no mesmo horario.
 *
 * <p>A regra e uma so: os servicos sao feitos em sequencia, entao o atendimento
 * ocupa a soma das duracoes e custa a soma dos precos. Daqui sai tudo -- o
 * tamanho do slot, o fim do agendamento, o conflito com o vizinho e o valor.
 */
@QuarkusTest
@DisplayName("agendamento com varios servicos -- soma de tempo e preco, slots e conflito")
class VariosServicosTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");

    private static final LocalDate DIA = LocalDate.now(FUSO)
            .plusWeeks(3)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final int DIA_SEMANA = DIA.getDayOfWeek().getValue() % 7;

    @Inject
    DadosDeTeste dados;

    private Profissional profissional;
    private Servico corte;
    private Servico barba;
    private Servico sobrancelha;
    private String tokenJoana;
    private String tokenMarcos;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario gabriel = dados.usuario(tenant.getId(), "Gabriel Stewer", "gabriel@stewer.dev",
                Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), gabriel.getId(), "Visagismo");
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);

        corte = dados.servico(tenant.getId(), "Corte", 60, 9000L);
        barba = dados.servico(tenant.getId(), "Barba", 30, 5000L);
        sobrancelha = dados.servico(tenant.getId(), "Sobrancelha", 20, 3000L);
        // Faixa de 09:00 as 12:00: 180 minutos, conta facil de conferir.
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");

        tokenJoana = login("joana@exemplo.dev");
        tokenMarcos = login("marcos@exemplo.dev");
    }

    @Test
    @DisplayName("corte + barba: 90 minutos, R$ 140, os dois servicos na resposta, na ordem")
    void criaComDoisServicos() {
        OffsetDateTime inicio = em("09:00");

        String fim = post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoIds", List.of(corte.getId().toString(), barba.getId().toString()),
                "inicio", inicio.toString()))
                .statusCode(201)
                .body("servicoId", equalTo(corte.getId().toString()))
                .body("servicoNome", equalTo("Corte + Barba"))
                .body("duracaoMinutos", equalTo(90))
                .body("precoCentavos", equalTo(14000))
                .body("servicos.nome", contains("Corte", "Barba"))
                .body("servicos.duracaoMinutos", contains(60, 30))
                .body("servicos.precoCentavos", contains(9000, 5000))
                .extract().path("fim");

        assertEquals(inicio.plusMinutes(90).toInstant(), OffsetDateTime.parse(fim).toInstant());
    }

    @Test
    @DisplayName("pedido antigo, so com servicoId, continua funcionando e vira lista de um")
    void servicoIdSozinhoContinuaValendo() {
        post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoId", barba.getId().toString(),
                "inicio", em("09:00").toString()))
                .statusCode(201)
                .body("servicoNome", equalTo("Barba"))
                .body("duracaoMinutos", equalTo(30))
                .body("servicos.size()", equalTo(1));
    }

    @Test
    @DisplayName("servico repetido conta uma vez; sem nenhum servico e 400 no campo servicoId")
    void repetidoESemServico() {
        post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoId", corte.getId().toString(),
                "servicoIds", List.of(corte.getId().toString(), barba.getId().toString(),
                        barba.getId().toString()),
                "inicio", em("09:00").toString()))
                .statusCode(201)
                .body("servicos.nome", contains("Corte", "Barba"))
                .body("duracaoMinutos", equalTo(90));

        post(tokenMarcos, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoIds", List.of(),
                "inicio", em("11:00").toString()))
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.campo", containsInAnyOrder("servicoId"));
    }

    @Test
    @DisplayName("horarios livres com dois servicos: slot do tamanho da soma e que caiba inteiro")
    void horariosLivresSomamAsDuracoes() {
        ValidatableResponse resposta = given()
                .header("Authorization", "Bearer " + tokenJoana)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", corte.getId().toString(), barba.getId().toString())
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then().statusCode(200)
                .body("duracaoMinutos", equalTo(90))
                .body("servicoIds.size()", equalTo(2));

        // 09:00-12:00 em blocos de 90: 09:00 e 10:30.
        assertEquals(List.of(utc("09:00"), utc("10:30")), inicios(resposta));
    }

    @Test
    @DisplayName("o atendimento de 90 minutos ocupa a agenda inteira: ninguem marca no meio dele")
    void ocupaASomaNaAgenda() {
        post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoIds", List.of(corte.getId().toString(), barba.getId().toString()),
                "inicio", em("09:00").toString()))
                .statusCode(201);

        // 10:00 cai dentro de 09:00-10:30.
        post(tokenMarcos, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoId", barba.getId().toString(),
                "inicio", em("10:00").toString()))
                .statusCode(409)
                .body("codigo", equalTo("HORARIO_OCUPADO"));

        // Para a barba (30 min) sobram 10:30, 11:00 e 11:30; nada antes das 10:30.
        List<OffsetDateTime> livres = inicios(given()
                .header("Authorization", "Bearer " + tokenMarcos)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", barba.getId().toString())
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then().statusCode(200));
        assertEquals(List.of(utc("10:30"), utc("11:00"), utc("11:30")), livres);
    }

    @Test
    @DisplayName("tres servicos que nao cabem na faixa sao recusados com FORA_DA_DISPONIBILIDADE")
    void naoCabeNaFaixa() {
        // 110 minutos a partir das 11:00 passariam do meio-dia.
        post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoIds", List.of(corte.getId().toString(), barba.getId().toString(),
                        sobrancelha.getId().toString()),
                "inicio", em("11:00").toString()))
                .statusCode(422)
                .body("codigo", equalTo("FORA_DA_DISPONIBILIDADE"));
    }

    @Test
    @DisplayName("remarcar mantem os servicos e os 90 minutos; a tela de remarcar usa esse tamanho")
    void remarcarMantemADuracao() {
        String id = post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoIds", List.of(corte.getId().toString(), barba.getId().toString()),
                "inicio", em("09:00").toString()))
                .statusCode(201)
                .extract().path("id");

        // Mesmo pedindo so o corte, ao remarcar o slot e o do proprio agendamento.
        List<OffsetDateTime> livres = inicios(given()
                .header("Authorization", "Bearer " + tokenJoana)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", corte.getId().toString())
                .queryParam("ignorarAgendamentoId", id)
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then().statusCode(200)
                .body("duracaoMinutos", equalTo(90)));
        assertEquals(List.of(utc("09:00"), utc("10:30")), livres);

        String fim = given()
                .header("Authorization", "Bearer " + tokenJoana)
                .contentType(ContentType.JSON)
                .body(Map.of("inicio", em("10:30").toString()))
                .when().patch("/agendamentos/{id}/horario", id)
                .then().statusCode(200)
                .body("servicoNome", equalTo("Corte + Barba"))
                .body("duracaoMinutos", equalTo(90))
                .extract().path("fim");
        assertEquals(em("12:00").toInstant(), OffsetDateTime.parse(fim).toInstant());
    }

    @Test
    @DisplayName("mais servicos que o limite e 400")
    void limiteDeServicos() {
        List<String> sete = java.util.Collections.nCopies(7, corte.getId().toString());
        post(tokenJoana, Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoIds", sete,
                "inicio", em("09:00").toString()))
                .statusCode(400);
    }

    // ------------------------------------------------------------------

    private ValidatableResponse post(String token, Map<String, Object> corpo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post("/agendamentos")
                .then();
    }

    private static List<OffsetDateTime> inicios(ValidatableResponse resposta) {
        List<String> brutos = resposta.extract().path("horarios.inicio");
        assertFalse(brutos == null);
        return brutos.stream()
                .map(OffsetDateTime::parse)
                .map(m -> m.withOffsetSameInstant(ZoneOffset.UTC))
                .toList();
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private static OffsetDateTime em(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
    }

    private static OffsetDateTime utc(String hora) {
        return em(hora).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
