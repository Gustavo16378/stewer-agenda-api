package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * A grade semanal e hora de PAREDE do estabelecimento, nao hora UTC.
 *
 * <p>Antes da correcao a API montava os instantes da grade com
 * {@code atOffset(ZoneOffset.UTC)}: a faixa "09:00 as 19:00" virava
 * {@code 09:00Z..19:00Z}, que num estabelecimento em UTC-3 e 06:00 as 16:00 da
 * manha local. O cliente via a agenda tres horas deslocada e o bloqueio que o
 * profissional marcava para a tarde nao derrubava slot nenhum.
 *
 * <p>O estudio fica em Palmas (TO), cujo fuso e {@code America/Araguaina} --
 * nao {@code America/Sao_Paulo}: se o horario de verao voltar, so Sao Paulo
 * muda de offset, e usar o fuso errado reintroduziria o bug por uma porta
 * diferente.
 */
@QuarkusTest
@DisplayName("Fuso do estabelecimento na grade, nos bloqueios e na agenda")
class FusoDoEstabelecimentoTest {

    /** Fuso do estudio: Palmas (TO). UTC-3 o ano inteiro, sem horario de verao. */
    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");

    /** Segunda-feira futura: deterministica e imune a regra do "slot no passado". */
    private static final LocalDate DIA = LocalDate.now(FUSO)
            .plusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    /** Convencao do schema: 0 = domingo ... 6 = sabado. */
    private static final int DIA_SEMANA = DIA.getDayOfWeek().getValue() % 7;

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Profissional profissional;
    private Servico servico60;
    private Usuario cliente;
    private String tokenCliente;
    private String tokenProfissional;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        Usuario usuarioProfissional = dados.usuario(tenant.getId(), "Stewer Gabriel",
                "stewer@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), usuarioProfissional.getId(), "Visagismo");

        servico60 = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 18000);
        cliente = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);

        tokenCliente = login("joana@exemplo.dev");
        tokenProfissional = login("stewer@stewer.dev");

        // A grade do estudio: 09:00 as 19:00, hora de parede de Palmas.
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "19:00");
    }

    @Test
    @DisplayName("grade 09:00-19:00 oferece o primeiro slot as 09:00 LOCAIS, nao as 09:00Z")
    void primeiroSlotRespeitaOFusoDoEstabelecimento() {
        horarios(DIA, servico60.getId())
                .statusCode(200)
                // 10 slots de 60min entre 09:00 e 19:00
                .body("horarios", hasSize(10))
                .body("horarios[0].inicio", equalTo(isoLocal(DIA, "09:00")))
                .body("horarios[0].fim", equalTo(isoLocal(DIA, "10:00")))
                .body("horarios[9].inicio", equalTo(isoLocal(DIA, "18:00")))
                .body("horarios[9].fim", equalTo(isoLocal(DIA, "19:00")));
    }

    @Test
    @DisplayName("nenhum slot cai na madrugada local (o bug punha o expediente em 06:00-16:00)")
    void nenhumSlotNaMadrugada() {
        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios.inicio", not(hasItem(isoLocal(DIA, "06:00"))))
                .body("horarios.inicio", not(hasItem(isoLocal(DIA, "07:00"))))
                .body("horarios.inicio", not(hasItem(isoLocal(DIA, "08:00"))));
    }

    @Test
    @DisplayName("bloqueio de 16:00 as 18:00 locais derruba exatamente os slots de 16:00 e 17:00")
    void bloqueioDaTardeDerrubaOsSlotsDaTarde() {
        // Exatamente o que o app envia: o instante correspondente a 16:00 locais.
        dados.bloqueio(tenant.getId(), profissional.getId(),
                local(DIA, "16:00"), local(DIA, "18:00"), "compromisso");

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(8))
                .body("horarios.inicio", not(hasItem(isoLocal(DIA, "16:00"))))
                .body("horarios.inicio", not(hasItem(isoLocal(DIA, "17:00"))))
                .body("horarios[7].inicio", equalTo(isoLocal(DIA, "18:00")));
    }

    @Test
    @DisplayName("POST /agendamentos aceita 09:00 local e recusa 06:00 local (fora da grade)")
    void agendamentoValidaAGradeNoFusoLocal() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "profissionalId", profissional.getId().toString(),
                        "servicoId", servico60.getId().toString(),
                        "inicio", local(DIA, "09:00").toString()))
                .when().post("/agendamentos")
                .then().statusCode(201);

        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "profissionalId", profissional.getId().toString(),
                        "servicoId", servico60.getId().toString(),
                        "inicio", local(DIA, "06:00").toString()))
                .when().post("/agendamentos")
                .then().statusCode(422)
                .body("codigo", equalTo("FORA_DA_DISPONIBILIDADE"));
    }

    @Test
    @DisplayName("agenda do dia usa o dia do calendario local: 22:00 locais nao vazam para amanha")
    void agendaDoDiaUsaOCalendarioLocal() {
        // 22:00 locais = 01:00Z do dia seguinte. Na janela antiga (00:00Z-24:00Z)
        // esse atendimento aparecia na agenda do dia errado.
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "22:00", "23:00");
        dados.agendamento(tenant.getId(), cliente.getId(), profissional.getId(), servico60.getId(),
                local(DIA, "22:00"), local(DIA, "23:00"),
                dev.gustavo.stewer.domain.StatusAgendamento.AGENDADO);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .queryParam("data", DIA.toString())
                .when().get("/agendamentos/agenda")
                .then().statusCode(200)
                .body("", hasSize(1))
                .body("[0].inicio", equalTo(isoLocal(DIA, "22:00")));

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .queryParam("data", DIA.plusDays(1).toString())
                .when().get("/agendamentos/agenda")
                .then().statusCode(200)
                .body("", hasSize(0));
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private io.restassured.response.ValidatableResponse horarios(LocalDate data, UUID servicoId) {
        return given()
                .header("Authorization", "Bearer " + tokenCliente)
                .queryParam("data", data.toString())
                .queryParam("servicoId", servicoId.toString())
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then();
    }

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    /** O instante correspondente a uma hora de PAREDE do estabelecimento. */
    private static OffsetDateTime local(LocalDate data, String hora) {
        return data.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
    }

    /** Como o Jackson serializa esse mesmo instante (offset do estabelecimento). */
    private static String isoLocal(LocalDate data, String hora) {
        OffsetDateTime instante = local(data, hora);
        ZoneOffset offset = instante.getOffset();
        return data + "T" + hora + ":00" + offset.getId();
    }
}
