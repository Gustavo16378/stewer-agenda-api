package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
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

/** Dados que faltavam nas telas: visitas, ultima visita, primeira visita e anotacao editavel. */
@QuarkusTest
@DisplayName("ficha do cliente -- visitas, primeira visita e anotacao editavel")
class FichaDoClienteTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");
    private static final LocalDate PASSADO = LocalDate.now(FUSO).minusWeeks(2)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    private static final LocalDate FUTURO = LocalDate.now(FUSO).plusWeeks(2)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Usuario joana;
    private Usuario marcos;
    private Profissional gabriel;
    private Servico servico;
    private String tokenGabriel;
    private String tokenColega;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario u = dados.usuario(tenant.getId(), "Gabriel", "gabriel@stewer.dev", Role.PROFISSIONAL);
        gabriel = dados.profissional(tenant.getId(), u.getId(), "Visagismo");
        Usuario c = dados.usuario(tenant.getId(), "Colega", "colega@stewer.dev", Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), c.getId(), "Barba");
        dados.usuario(tenant.getId(), "Ana", "ana@stewer.dev", Role.ADMIN);
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        marcos = dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);
        servico = dados.servico(tenant.getId(), "Corte", 60, 9000L);

        // Joana ja veio uma vez; Marcos nunca.
        agendar(joana, PASSADO, "10:00", StatusAgendamento.CONCLUIDO);
        agendar(joana, FUTURO, "10:00", StatusAgendamento.AGENDADO);
        agendar(marcos, FUTURO, "14:00", StatusAgendamento.AGENDADO);

        tokenGabriel = login("gabriel@stewer.dev");
        tokenColega = login("colega@stewer.dev");
        tokenAdmin = login("ana@stewer.dev");
    }

    @Test
    @DisplayName("lista de clientes traz visitas e ultima visita")
    void listaComVisitas() {
        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/clientes")
                .then().statusCode(200)
                .body("find { it.nome == 'Joana Ribeiro' }.visitas", equalTo(1))
                .body("find { it.nome == 'Joana Ribeiro' }.ultimaVisita", notNullValue())
                .body("find { it.nome == 'Marcos Lima' }.visitas", equalTo(0))
                .body("find { it.nome == 'Marcos Lima' }.ultimaVisita", nullValue());
    }

    @Test
    @DisplayName("agenda marca primeira visita so para quem nunca teve atendimento concluido")
    void primeiraVisita() {
        given().header("Authorization", "Bearer " + tokenGabriel)
                .queryParam("data", FUTURO.toString())
                .when().get("/agendamentos/agenda")
                .then().statusCode(200)
                .body("find { it.clienteNome == 'Joana Ribeiro' }.primeiraVisita", equalTo(false))
                .body("find { it.clienteNome == 'Marcos Lima' }.primeiraVisita", equalTo(true));
    }

    @Test
    @DisplayName("quem escreveu edita e apaga a anotacao; colega nao; admin sim")
    void anotacaoEditavel() {
        String id = given().header("Authorization", "Bearer " + tokenGabriel)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Rosto oval"))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then().statusCode(201).extract().path("id");

        put(tokenGabriel, id, "Rosto oval, manter volume").statusCode(200)
                .body("texto", equalTo("Rosto oval, manter volume"));
        put(tokenColega, id, "Tentando mudar").statusCode(403);
        put(tokenAdmin, id, "Revisado pelo admin").statusCode(200);

        given().header("Authorization", "Bearer " + tokenColega)
                .when().delete("/clientes/" + joana.getId() + "/anotacoes/" + id)
                .then().statusCode(403);
        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().delete("/clientes/" + joana.getId() + "/anotacoes/" + id)
                .then().statusCode(204);
        given().header("Authorization", "Bearer " + tokenGabriel)
                .when().get("/clientes/" + joana.getId() + "/anotacoes")
                .then().statusCode(200).body("size()", equalTo(0));
    }

    private io.restassured.response.ValidatableResponse put(String token, String id, String texto) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", texto))
                .when().put("/clientes/" + joana.getId() + "/anotacoes/" + id)
                .then();
    }

    private void agendar(Usuario cliente, LocalDate dia, String hora, StatusAgendamento status) {
        OffsetDateTime inicio = dia.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
        dados.agendamento(tenant.getId(), cliente.getId(), gabriel.getId(), servico.getId(),
                inicio, inicio.plusHours(1), status);
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
