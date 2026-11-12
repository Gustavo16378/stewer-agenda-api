package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
 * O estudio no dia a dia: o dono que tambem atende, a equipe marcando para o
 * cliente que ligou, remarcar sem perder o horario e o teto do que um cliente
 * reserva sozinho.
 */
@QuarkusTest
@DisplayName("agenda da equipe -- dono que atende, marcar pelo cliente, remarcar e limites")
class AgendaDaEquipeTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");

    private static final LocalDate DIA = LocalDate.now(FUSO)
            .plusWeeks(2)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final int DIA_SEMANA = DIA.getDayOfWeek().getValue() % 7;

    @Inject
    DadosDeTeste dados;

    private Profissional gabriel;
    private Profissional dono;
    private Usuario joana;
    private Usuario colega;
    private Servico servico;

    private String tokenJoana;
    private String tokenMarcos;
    private String tokenGabriel;
    private String tokenDono;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        Usuario usuarioGabriel = dados.usuario(tenant.getId(), "Gabriel Stewer",
                "gabriel@stewer.dev", Role.PROFISSIONAL);
        gabriel = dados.profissional(tenant.getId(), usuarioGabriel.getId(), "Visagismo");

        // O dono e ADMIN e tambem atende: tem ficha de profissional.
        Usuario usuarioDono = dados.usuario(tenant.getId(), "Stewer Dono",
                "stewer@stewer.dev", Role.ADMIN);
        dono = dados.profissional(tenant.getId(), usuarioDono.getId(), "Visagismo");

        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);
        colega = usuarioGabriel;

        servico = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 9000L);
        dados.disponibilidade(tenant.getId(), gabriel.getId(), DIA_SEMANA, "09:00", "19:00");
        dados.disponibilidade(tenant.getId(), dono.getId(), DIA_SEMANA, "09:00", "19:00");

        tokenJoana = login("joana@exemplo.dev");
        tokenMarcos = login("marcos@exemplo.dev");
        tokenGabriel = login("gabriel@stewer.dev");
        tokenDono = login("stewer@stewer.dev");
    }

    // ------------------------------------------------------------------
    // Marcar em nome do cliente
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profissional marca para o cliente na propria agenda: 201 com o cliente certo")
    void profissionalMarcaParaCliente() {
        post(tokenGabriel, corpo(gabriel.getId(), em("10:00"), joana.getId()))
                .statusCode(201)
                .body("clienteId", equalTo(joana.getId().toString()))
                .body("clienteNome", equalTo("Joana Ribeiro"))
                .body("profissionalId", equalTo(gabriel.getId().toString()));
    }

    @Test
    @DisplayName("profissional nao marca na agenda de outro profissional: 403")
    void profissionalNaoMarcaNaAgendaDoOutro() {
        post(tokenGabriel, corpo(dono.getId(), em("10:00"), joana.getId()))
                .statusCode(403);
    }

    @Test
    @DisplayName("clienteId que nao e de um CLIENTE devolve 404")
    void clienteIdDeNaoClienteDevolve404() {
        post(tokenDono, corpo(dono.getId(), em("10:00"), colega.getId()))
                .statusCode(404);
    }

    @Test
    @DisplayName("cliente que manda clienteId de outra pessoa continua reservando para si")
    void clienteNaoReservaEmNomeDeOutro() {
        post(tokenJoana, corpo(gabriel.getId(), em("10:00"), UUID.randomUUID()))
                .statusCode(201)
                .body("clienteId", equalTo(joana.getId().toString()));
    }

    @Test
    @DisplayName("admin marca para o cliente em qualquer agenda: 201")
    void adminMarcaParaCliente() {
        post(tokenDono, corpo(gabriel.getId(), em("11:00"), joana.getId()))
                .statusCode(201)
                .body("profissionalId", equalTo(gabriel.getId().toString()));
    }

    // ------------------------------------------------------------------
    // Dono que atende
    // ------------------------------------------------------------------

    @Test
    @DisplayName("admin com ficha ve a propria agenda sem informar profissionalId")
    void donoVeAPropriaAgenda() {
        post(tokenJoana, corpo(dono.getId(), em("10:00"), null)).statusCode(201);

        given()
                .header("Authorization", "Bearer " + tokenDono)
                .queryParam("data", DIA.toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].profissionalId", equalTo(dono.getId().toString()));
    }

    @Test
    @DisplayName("admin com ficha assina anotacao de visagismo: 201")
    void donoAnota() {
        given()
                .header("Authorization", "Bearer " + tokenDono)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Rosto oval, manter volume no topo"))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then()
                .statusCode(201)
                .body("profissionalId", equalTo(dono.getId().toString()));
    }

    // ------------------------------------------------------------------
    // Remarcar
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cliente remarca o proprio horario: 200 e o horario antigo volta a ficar livre")
    void clienteRemarca() {
        String id = agendar(tokenJoana, em("10:00"));

        String novoInicio = patchHorario(tokenJoana, id, em("15:00"), null)
                .statusCode(200)
                .body("status", equalTo("AGENDADO"))
                .extract().path("inicio");
        assertEquals(em("15:00").toInstant(), OffsetDateTime.parse(novoInicio).toInstant());

        List<Instant> livres = livres(null);
        assertTrue(livres.contains(em("10:00").toInstant()), "10:00 deveria ter sido liberado");
        assertFalse(livres.contains(em("15:00").toInstant()), "15:00 agora esta ocupado");
    }

    @Test
    @DisplayName("remarcar meia hora para frente, sobrepondo o proprio horario, e valido")
    void remarcarSobreOProprioHorario() {
        String id = agendar(tokenJoana, em("10:00"));
        patchHorario(tokenJoana, id, em("10:30"), null).statusCode(200);
    }

    @Test
    @DisplayName("remarcar para horario de outra pessoa devolve 409 HORARIO_OCUPADO e nada muda")
    void remarcarParaHorarioOcupado() {
        String id = agendar(tokenJoana, em("10:00"));
        agendar(tokenMarcos, em("14:00"));

        patchHorario(tokenJoana, id, em("14:00"), null)
                .statusCode(409)
                .body("codigo", equalTo("HORARIO_OCUPADO"));

        assertFalse(livres(null).contains(em("10:00").toInstant()),
                "o horario original continua da Joana");
    }

    @Test
    @DisplayName("remarcar um agendamento cancelado devolve 409 STATUS_FINAL")
    void remarcarCanceladoDevolve409() {
        String id = agendar(tokenJoana, em("10:00"));
        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .contentType(ContentType.JSON)
                .body(Map.of("status", "CANCELADO"))
                .when().patch("/agendamentos/{id}/status", id)
                .then().statusCode(200);

        patchHorario(tokenJoana, id, em("15:00"), null)
                .statusCode(409)
                .body("codigo", equalTo("STATUS_FINAL"));
    }

    @Test
    @DisplayName("cliente nao remarca agendamento de outro cliente: 403")
    void clienteNaoRemarcaODeOutro() {
        String id = agendar(tokenJoana, em("10:00"));
        patchHorario(tokenMarcos, id, em("15:00"), null).statusCode(403);
    }

    @Test
    @DisplayName("remarcar trocando de profissional move o atendimento para a outra agenda")
    void remarcarTrocandoProfissional() {
        String id = agendar(tokenJoana, em("10:00"));
        patchHorario(tokenJoana, id, em("10:00"), dono.getId())
                .statusCode(200)
                .body("profissionalId", equalTo(dono.getId().toString()));
    }

    @Test
    @DisplayName("horarios livres ignorando o proprio agendamento mostram o horario atual como livre")
    void horariosLivresIgnorandoOProprio() {
        String id = agendar(tokenJoana, em("10:00"));
        assertFalse(livres(null).contains(em("10:00").toInstant()));
        assertTrue(livres(id).contains(em("10:00").toInstant()));
    }

    // ------------------------------------------------------------------
    // Limites do cliente
    // ------------------------------------------------------------------

    @Test
    @DisplayName("quarto horario futuro do mesmo cliente devolve 422 LIMITE_DE_AGENDAMENTOS")
    void limiteDeAgendamentosFuturos() {
        agendar(tokenJoana, em("09:00"));
        agendar(tokenJoana, em("10:00"));
        agendar(tokenJoana, em("11:00"));

        post(tokenJoana, corpo(gabriel.getId(), em("12:00"), null))
                .statusCode(422)
                .body("codigo", equalTo("LIMITE_DE_AGENDAMENTOS"));

        // A equipe nao passa pelo teto: quem marca conhece o cliente.
        post(tokenGabriel, corpo(gabriel.getId(), em("12:00"), joana.getId()))
                .statusCode(201);
    }

    @Test
    @DisplayName("horario alem de 60 dias devolve 422 ANTECEDENCIA_MAXIMA")
    void antecedenciaMaxima() {
        LocalDate longe = LocalDate.now(FUSO).plusDays(70)
                .with(TemporalAdjusters.next(DIA.getDayOfWeek()));
        OffsetDateTime inicio = longe.atTime(LocalTime.parse("10:00")).atZone(FUSO).toOffsetDateTime();

        post(tokenJoana, corpo(gabriel.getId(), inicio, null))
                .statusCode(422)
                .body("codigo", equalTo("ANTECEDENCIA_MAXIMA"));
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

    private String agendar(String token, OffsetDateTime inicio) {
        return post(token, corpo(gabriel.getId(), inicio, null))
                .statusCode(201)
                .extract().path("id");
    }

    private ValidatableResponse patchHorario(String token, String id, OffsetDateTime inicio,
            UUID profissionalId) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("inicio", inicio.toString());
        if (profissionalId != null) {
            corpo.put("profissionalId", profissionalId.toString());
        }
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().patch("/agendamentos/{id}/horario", id)
                .then();
    }

    private List<Instant> livres(String ignorarAgendamentoId) {
        var req = given()
                .header("Authorization", "Bearer " + tokenJoana)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", servico.getId().toString());
        if (ignorarAgendamentoId != null) {
            req = req.queryParam("ignorarAgendamentoId", ignorarAgendamentoId);
        }
        List<String> brutos = req.when()
                .get("/profissionais/{id}/horarios-livres", gabriel.getId())
                .then().statusCode(200)
                .extract().path("horarios.inicio");
        return brutos.stream().map(OffsetDateTime::parse).map(OffsetDateTime::toInstant).toList();
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private Map<String, Object> corpo(UUID profissionalId, OffsetDateTime inicio, UUID clienteId) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("profissionalId", profissionalId.toString());
        corpo.put("servicoId", servico.getId().toString());
        corpo.put("inicio", inicio.toString());
        if (clienteId != null) {
            corpo.put("clienteId", clienteId.toString());
        }
        return corpo;
    }

    private static OffsetDateTime em(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
    }
}
