package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Toda mudanca deixa rastro: quem fez, o que fez e quando. */
@QuarkusTest
@DisplayName("auditoria -- quem fez o que e quando")
class AuditoriaTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");
    private static final LocalDate SEGUNDA = LocalDate.now(FUSO).plusWeeks(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Usuario joana;
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
        for (int dia = 1; dia <= 6; dia++) {
            dados.disponibilidade(tenant.getId(), gabriel.getId(), dia, "09:00", "19:00");
        }
        dados.usuario(tenant.getId(), "Ana Admin", "ana@stewer.dev", Role.ADMIN);
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        corte = dados.servico(tenant.getId(), "Corte", 60, 9000L);
        tokenGabriel = login("stewer@stewer.dev");
        tokenAdmin = login("ana@stewer.dev");
        tokenJoana = login("joana@exemplo.dev");
    }

    @Test
    @DisplayName("marcar, remarcar e cancelar ficam registrados com o autor de cada passo")
    void ciclodoAgendamento() {
        String id = given().header("Authorization", "Bearer " + tokenJoana).contentType(ContentType.JSON)
                .body(Map.of("profissionalId", gabriel.getId(), "servicoId", corte.getId(),
                        "inicio", instante("10:00")))
                .when().post("/agendamentos")
                .then().statusCode(201).extract().path("id");
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("inicio", instante("15:00")))
                .when().patch("/agendamentos/" + id + "/horario")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenJoana).contentType(ContentType.JSON)
                .body(Map.of("status", "CANCELADO"))
                .when().patch("/agendamentos/" + id + "/status")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("entidade", "AGENDAMENTO")
                .when().get("/auditoria")
                .then().statusCode(200)
                .body("acao", contains("CANCELOU", "REMARCOU", "CRIOU"))
                .body("autorNome", contains("Joana Ribeiro", "Stewer Gabriel", "Joana Ribeiro"))
                .body("clienteId", everyItem(equalTo(joana.getId().toString())))
                .body("[1].resumo", containsString("para seg"))
                .body("[2].resumo", containsString("Marcou Corte de Joana Ribeiro com Stewer Gabriel"));
    }

    @Test
    @DisplayName("catalogo, equipe, horarios e ficha tambem entram")
    void restoDoSistema() {
        String servico = given().header("Authorization", "Bearer " + tokenAdmin).contentType(ContentType.JSON)
                .body(Map.of("nome", "Barba", "duracaoMinutos", 30, "precoCentavos", 5000))
                .when().post("/servicos")
                .then().statusCode(201).extract().path("id");
        given().header("Authorization", "Bearer " + tokenAdmin).contentType(ContentType.JSON)
                .body(Map.of("nome", "Barba", "duracaoMinutos", 30, "precoCentavos", 6000))
                .when().put("/servicos/" + servico)
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("faixas", List.of(Map.of("diaSemana", 2, "horaInicio", "10:00", "horaFim", "18:00"))))
                .when().put("/profissionais/" + gabriel.getId() + "/disponibilidade")
                .then().statusCode(200);
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("formatoRosto", "Oval"))
                .when().put("/clientes/" + joana.getId() + "/ficha")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().get("/auditoria")
                .then().statusCode(200)
                .body("entidade", contains("FICHA", "HORARIOS", "SERVICO", "SERVICO"))
                .body("[2].resumo", containsString("R$ 50,00, 30 min para R$ 60,00, 30 min"));

        given().header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("entidade", "SERVICO").queryParam("entidade", "HORARIOS")
                .when().get("/auditoria")
                .then().statusCode(200).body("$", hasSize(3));
    }

    @Test
    @DisplayName("profissional ve so a linha do tempo de um cliente; cliente nao ve nada")
    void quemVe() {
        given().header("Authorization", "Bearer " + tokenGabriel).contentType(ContentType.JSON)
                .body(Map.of("formatoRosto", "Oval"))
                .when().put("/clientes/" + joana.getId() + "/ficha")
                .then().statusCode(200);

        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/auditoria")
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + tokenGabriel)
                .queryParam("clienteId", joana.getId())
                .when().get("/auditoria")
                .then().statusCode(200).body("acao", hasItem("ALTEROU"));
        given().header("Authorization", "Bearer " + tokenJoana)
                .queryParam("clienteId", joana.getId())
                .when().get("/auditoria")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("cadastro pelo app fica registrado com o proprio cliente como autor")
    void cadastro() {
        given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Pedro Novo", "email", "pedro@exemplo.dev", "senha", "senha-forte-1"))
                .when().post("/auth/registrar")
                .then().statusCode(201);
        given().header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("entidade", "CLIENTE")
                .when().get("/auditoria")
                .then().statusCode(200)
                .body("[0].acao", equalTo("CADASTROU"))
                .body("[0].autorNome", equalTo("Pedro Novo"));
    }

    private String instante(String hora) {
        return SEGUNDA.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime().toString();
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
