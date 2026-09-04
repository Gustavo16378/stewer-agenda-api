package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.UUID;

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
 * O endpoint mais importante do sistema.
 *
 * <p>Todos os cenarios usam uma segunda-feira daqui a um mes: data fixa no
 * futuro, para que a regra que descarta slot no passado nunca interfira, e dia
 * da semana derivado (nunca chutado) para casar com a grade cadastrada.
 */
@QuarkusTest
@DisplayName("GET /profissionais/{id}/horarios-livres")
class HorariosLivresResourceTest {

    /** Segunda-feira futura: deterministica e imune a regra do "slot no passado". */
    private static final LocalDate DIA = LocalDate.now(ZoneOffset.UTC)
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
    }

    @Test
    @DisplayName("dia sem disponibilidade na grade devolve 200 com lista vazia")
    void diaSemDisponibilidade() {
        // Grade so na terca; a consulta e na segunda.
        dados.disponibilidade(tenant.getId(), profissional.getId(),
                (DIA_SEMANA + 1) % 7, "09:00", "19:00");

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("data", equalTo(DIA.toString()))
                .body("duracaoMinutos", equalTo(60))
                .body("horarios", hasSize(0));
    }

    @Test
    @DisplayName("grade de 09:00 as 12:00 com servico de 60min gera exatamente 3 slots")
    void gradeCheiaGeraSlotsConsecutivos() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(3))
                .body("horarios.inicio", contains(
                        iso(DIA, "09:00"), iso(DIA, "10:00"), iso(DIA, "11:00")))
                .body("horarios.fim", contains(
                        iso(DIA, "10:00"), iso(DIA, "11:00"), iso(DIA, "12:00")));
    }

    @Test
    @DisplayName("agendamento de 10:00 as 10:30 derruba o slot das 10:00 e so ele")
    void slotParcialmenteOcupado() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");
        // Meia hora de ocupacao no meio de um slot de uma hora: sobrepoe
        // parcialmente, e sobreposicao parcial ja invalida o slot inteiro.
        dados.agendamento(tenant.getId(), cliente.getId(), profissional.getId(), servico60.getId(),
                em(DIA, "10:00"), em(DIA, "10:30"), StatusAgendamento.AGENDADO);

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(2))
                .body("horarios.inicio", contains(iso(DIA, "09:00"), iso(DIA, "11:00")));
    }

    @Test
    @DisplayName("agendamento encostado no slot seguinte nao o derruba")
    void agendamentoEncostadoNaoConflita() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");
        // Termina exatamente as 10:00, que e quando o proximo slot comeca.
        dados.agendamento(tenant.getId(), cliente.getId(), profissional.getId(), servico60.getId(),
                em(DIA, "09:00"), em(DIA, "10:00"), StatusAgendamento.AGENDADO);

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(2))
                .body("horarios.inicio", contains(iso(DIA, "10:00"), iso(DIA, "11:00")));
    }

    @Test
    @DisplayName("bloqueio no meio da grade remove exatamente os slots que ele toca")
    void bloqueioNoMeioDaGrade() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "13:00");
        // Almoco esticado: pega o fim do slot das 10:00 e o comeco do das 11:00.
        dados.bloqueio(tenant.getId(), profissional.getId(),
                em(DIA, "10:30"), em(DIA, "11:30"), "Almoco");

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(2))
                .body("horarios.inicio", contains(iso(DIA, "09:00"), iso(DIA, "12:00")));
    }

    @Test
    @DisplayName("agendamento CANCELADO nao ocupa slot")
    void canceladoNaoOcupa() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");
        dados.agendamento(tenant.getId(), cliente.getId(), profissional.getId(), servico60.getId(),
                em(DIA, "10:00"), em(DIA, "11:00"), StatusAgendamento.CANCELADO);

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(3))
                .body("horarios.inicio", contains(
                        iso(DIA, "09:00"), iso(DIA, "10:00"), iso(DIA, "11:00")));
    }

    @Test
    @DisplayName("agendamento NAO_COMPARECEU tambem libera o slot")
    void naoCompareceuNaoOcupa() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");
        dados.agendamento(tenant.getId(), cliente.getId(), profissional.getId(), servico60.getId(),
                em(DIA, "09:00"), em(DIA, "10:00"), StatusAgendamento.NAO_COMPARECEU);

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(3));
    }

    @Test
    @DisplayName("agendamento CONCLUIDO tambem libera o slot")
    void concluidoNaoOcupa() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");
        dados.agendamento(tenant.getId(), cliente.getId(), profissional.getId(), servico60.getId(),
                em(DIA, "11:00"), em(DIA, "12:00"), StatusAgendamento.CONCLUIDO);

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(3));
    }

    @Test
    @DisplayName("servico mais longo que a faixa nao gera slot que estoure o expediente")
    void servicoNaoCabeNaFaixa() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "10:00");
        Servico consultoria = dados.servico(tenant.getId(), "Consultoria de Imagem", 90, 32000);

        horarios(DIA, consultoria.getId())
                .statusCode(200)
                .body("horarios", hasSize(0));
    }

    @Test
    @DisplayName("faixa de 09:00 as 10:30 com servico de 60min gera um slot so, sem sobra")
    void sobraDaFaixaNaoViraSlot() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "10:30");

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(1))
                .body("horarios[0].inicio", equalTo(iso(DIA, "09:00")))
                .body("horarios[0].fim", equalTo(iso(DIA, "10:00")));
    }

    @Test
    @DisplayName("duas faixas no mesmo dia geram slots das duas, em ordem")
    void duasFaixasNoMesmoDia() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "11:00");
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "14:00", "16:00");

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(4))
                .body("horarios.inicio", contains(
                        iso(DIA, "09:00"), iso(DIA, "10:00"),
                        iso(DIA, "14:00"), iso(DIA, "15:00")));
    }

    @Test
    @DisplayName("servico de 30min fatia a mesma faixa em o dobro de slots")
    void duracaoDoServicoDefineOFatiamento() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "11:00");
        Servico barba = dados.servico(tenant.getId(), "Barba", 30, 7000);

        horarios(DIA, barba.getId())
                .statusCode(200)
                .body("duracaoMinutos", equalTo(30))
                .body("horarios", hasSize(4))
                .body("horarios.inicio", contains(
                        iso(DIA, "09:00"), iso(DIA, "09:30"),
                        iso(DIA, "10:00"), iso(DIA, "10:30")));
    }

    @Test
    @DisplayName("agenda de outro profissional nao interfere no calculo")
    void agendaDeOutroProfissionalNaoInterfere() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "11:00");

        Usuario outroUsuario = dados.usuario(tenant.getId(), "Outro Profissional",
                "outro@stewer.dev", Role.PROFISSIONAL);
        Profissional outro = dados.profissional(tenant.getId(), outroUsuario.getId(), "Barbearia");
        dados.agendamento(tenant.getId(), cliente.getId(), outro.getId(), servico60.getId(),
                em(DIA, "09:00"), em(DIA, "10:00"), StatusAgendamento.AGENDADO);

        horarios(DIA, servico60.getId())
                .statusCode(200)
                .body("horarios", hasSize(2));
    }

    @Test
    @DisplayName("profissional inexistente devolve 404")
    void profissionalInexistente() {
        given().header("Authorization", "Bearer " + tokenCliente)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", servico60.getId().toString())
                .when().get("/profissionais/{id}/horarios-livres", UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("servico inexistente devolve 404")
    void servicoInexistente() {
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "12:00");

        horarios(DIA, UUID.randomUUID())
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("sem token devolve 401")
    void semToken() {
        given()
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", servico60.getId().toString())
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then()
                .statusCode(401);
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

    private static OffsetDateTime em(LocalDate data, String hora) {
        return data.atTime(LocalTime.parse(hora)).atOffset(ZoneOffset.UTC);
    }

    /** Mesma serializacao que o Jackson produz para OffsetDateTime em UTC. */
    private static String iso(LocalDate data, String hora) {
        return data + "T" + hora + ":00Z";
    }
}
