package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
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
 * Financeiro e agenda de varios dias: o balanco sai da propria agenda, no fuso do
 * estudio, com o preco do dia em que o horario foi marcado.
 */
@QuarkusTest
@DisplayName("financeiro -- faturado, a receber, serie e agenda do periodo")
class FinanceiroTest {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    /** Uma segunda-feira de um mes inteiro no passado. */
    private static final LocalDate SEGUNDA = LocalDate.of(2026, 3, 2);

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Profissional stewer;
    private Servico corte;
    private Servico barba;
    private Usuario joana;
    private Usuario marcos;
    private String admin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Admin", "admin@x.dev", Role.ADMIN);
        Usuario u = dados.usuario(tenant.getId(), "Stewer", "stewer@x.dev", Role.PROFISSIONAL);
        stewer = dados.profissional(tenant.getId(), u.getId(), "Visagismo");
        corte = dados.servico(tenant.getId(), "Corte", 60, 9000);
        barba = dados.servico(tenant.getId(), "Barba", 30, 7000);
        joana = dados.usuario(tenant.getId(), "Joana", "joana@x.dev", Role.CLIENTE);
        marcos = dados.usuario(tenant.getId(), "Marcos", "marcos@x.dev", Role.CLIENTE);
        admin = login("admin@x.dev");
    }

    private void marcar(Usuario cliente, Servico s, LocalDate dia, int hora, StatusAgendamento status) {
        OffsetDateTime inicio = dia.atTime(hora, 0).atZone(FUSO).toOffsetDateTime();
        dados.agendamento(tenant.getId(), cliente.getId(), stewer.getId(), s.getId(), inicio,
                inicio.plusMinutes(s.getDuracaoMinutos()), status);
    }

    @Test
    @DisplayName("faturado so do que teve baixa; a receber do que esta marcado; faltas e clientes novos")
    void totais() {
        // semana anterior: Joana ja veio uma vez
        marcar(joana, corte, SEGUNDA.minusDays(3), 10, StatusAgendamento.CONCLUIDO);
        // a semana do relatorio
        marcar(joana, corte, SEGUNDA, 10, StatusAgendamento.CONCLUIDO);
        marcar(marcos, barba, SEGUNDA, 11, StatusAgendamento.CONCLUIDO);
        marcar(marcos, corte, SEGUNDA.plusDays(2), 9, StatusAgendamento.NAO_COMPARECEU);
        marcar(joana, barba, SEGUNDA.plusDays(4), 15, StatusAgendamento.AGENDADO);
        marcar(marcos, corte, SEGUNDA.plusDays(5), 16, StatusAgendamento.CANCELADO);

        given().header("Authorization", "Bearer " + admin)
                .queryParam("de", SEGUNDA.toString()).queryParam("ate", SEGUNDA.plusDays(6).toString())
                .when().get("/relatorios/financeiro")
                .then().statusCode(200)
                .body("agrupamento", equalTo("DIA"))
                .body("totais.faturadoCentavos", equalTo(16000))
                .body("totais.aReceberCentavos", equalTo(7000))
                .body("totais.atendimentos", equalTo(2))
                .body("totais.ticketMedioCentavos", equalTo(8000))
                .body("totais.faltas", equalTo(1))
                .body("totais.cancelados", equalTo(1))
                .body("totais.clientesAtendidos", equalTo(2))
                .body("totais.clientesNovos", equalTo(1))
                .body("totais.taxaDeFaltas", equalTo(33))
                .body("anterior.faturadoCentavos", equalTo(9000))
                .body("serie", hasSize(7))
                .body("serie[0].faturadoCentavos", equalTo(16000))
                .body("serie[4].aReceberCentavos", equalTo(7000))
                .body("porServico[0].nome", equalTo("Corte"))
                .body("porDiaDaSemana[0].atendimentos", equalTo(2));
    }

    @Test
    @DisplayName("ano inteiro agrupa por mes; semestre por mes; um trimestre por semana")
    void agrupamentos() {
        marcar(joana, corte, LocalDate.of(2026, 1, 15), 10, StatusAgendamento.CONCLUIDO);
        marcar(joana, corte, LocalDate.of(2026, 5, 20), 10, StatusAgendamento.CONCLUIDO);
        given().header("Authorization", "Bearer " + admin)
                .queryParam("de", "2026-01-01").queryParam("ate", "2026-12-31")
                .when().get("/relatorios/financeiro")
                .then().statusCode(200)
                .body("agrupamento", equalTo("MES")).body("serie", hasSize(12))
                .body("serie[0].faturadoCentavos", equalTo(9000))
                .body("serie[4].faturadoCentavos", equalTo(9000));
        given().header("Authorization", "Bearer " + admin)
                .queryParam("de", "2026-01-01").queryParam("ate", "2026-03-31")
                .when().get("/relatorios/financeiro")
                .then().statusCode(200).body("agrupamento", equalTo("SEMANA"));
    }

    @Test
    @DisplayName("periodo invertido, longo demais ou agrupamento estranho: 422; so admin ve")
    void validacoes() {
        given().header("Authorization", "Bearer " + admin).queryParam("de", "2026-02-01").queryParam("ate", "2026-01-01")
                .when().get("/relatorios/financeiro").then().statusCode(422);
        given().header("Authorization", "Bearer " + admin).queryParam("de", "2024-01-01").queryParam("ate", "2026-01-01")
                .when().get("/relatorios/financeiro").then().statusCode(422);
        given().header("Authorization", "Bearer " + admin).queryParam("de", "2026-01-01").queryParam("ate", "2026-01-31")
                .queryParam("agrupar", "hora").when().get("/relatorios/financeiro").then().statusCode(422);
        given().header("Authorization", "Bearer " + login("stewer@x.dev")).queryParam("de", "2026-01-01")
                .queryParam("ate", "2026-01-31").when().get("/relatorios/financeiro").then().statusCode(403);
    }

    @Test
    @DisplayName("agenda do periodo traz a semana inteira, com todos os status, e recusa mais de 92 dias")
    void agendaDoPeriodo() {
        marcar(joana, corte, SEGUNDA, 10, StatusAgendamento.CONCLUIDO);
        marcar(marcos, barba, SEGUNDA.plusDays(3), 14, StatusAgendamento.AGENDADO);
        marcar(marcos, barba, SEGUNDA.plusDays(8), 14, StatusAgendamento.AGENDADO);
        String prof = login("stewer@x.dev");
        given().header("Authorization", "Bearer " + prof)
                .queryParam("de", SEGUNDA.toString()).queryParam("ate", SEGUNDA.plusDays(6).toString())
                .when().get("/agendamentos/agenda/periodo")
                .then().statusCode(200).body("", hasSize(2))
                .body("[0].clienteNome", equalTo("Joana"));
        given().header("Authorization", "Bearer " + prof)
                .queryParam("de", "2026-01-01").queryParam("ate", "2026-06-01")
                .when().get("/agendamentos/agenda/periodo").then().statusCode(422);
        given().header("Authorization", "Bearer " + login("joana@x.dev"))
                .queryParam("de", "2026-01-01").queryParam("ate", "2026-01-07")
                .when().get("/agendamentos/agenda/periodo").then().statusCode(403);
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login").then().statusCode(200).extract().path("token");
    }
}
