package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Bloqueio;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.util.DadosDeTeste;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import jakarta.inject.Inject;
import org.hamcrest.Matcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Agenda do profissional: grade semanal recorrente e bloqueios pontuais.
 *
 * <p>Os dias sao derivados de {@link TemporalAdjusters} sobre uma data no futuro
 * em vez de chutados: assim o diaSemana da grade e o instante do bloqueio sempre
 * combinam, independente do dia em que a suite roda.
 */
@QuarkusTest
@DisplayName("/profissionais/{id} -- grade semanal e bloqueios da agenda")
class DisponibilidadeResourceTest {

    private static final String AUTH = "Authorization";

    /** Data deterministica e sempre no futuro, para nenhum bloqueio nascer no passado. */
    private static final LocalDate SEGUNDA_FUTURA = LocalDate.now(ZoneOffset.UTC)
            .plusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final LocalDate QUARTA_FUTURA = LocalDate.now(ZoneOffset.UTC)
            .plusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.WEDNESDAY));

    /** 0 = domingo ... 6 = sabado, a mesma convencao da entidade Disponibilidade. */
    private static final int SEGUNDA = SEGUNDA_FUTURA.getDayOfWeek().getValue() % 7;
    private static final int QUARTA = QUARTA_FUTURA.getDayOfWeek().getValue() % 7;

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Profissional gabriel;
    private Profissional colega;

    private String tokenGabriel;
    private String tokenColega;
    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void preparar() {
        dados.limpar();
        // O slug bate com stewer.tenant.slug-padrao para que o login dispense o tenantSlug.
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        Usuario usuarioGabriel = dados.usuario(tenant.getId(), "Gabriel Stewer",
                "gabriel@stewer.dev", Role.PROFISSIONAL);
        gabriel = dados.profissional(tenant.getId(), usuarioGabriel.getId(), "Visagismo");

        Usuario usuarioColega = dados.usuario(tenant.getId(), "Rafael Moura",
                "rafael@stewer.dev", Role.PROFISSIONAL);
        colega = dados.profissional(tenant.getId(), usuarioColega.getId(), "Barboterapia");

        dados.usuario(tenant.getId(), "Ana Administradora", "ana@stewer.dev", Role.ADMIN);
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);

        tokenGabriel = login("gabriel@stewer.dev");
        tokenColega = login("rafael@stewer.dev");
        tokenAdmin = login("ana@stewer.dev");
        tokenCliente = login("joana@exemplo.dev");
    }

    // ---------------------------------------------------------------- grade

    @Test
    @DisplayName("GET devolve a grade do profissional ordenada por dia da semana e hora de inicio")
    void gradeVemOrdenadaPorDiaEHora() {
        dados.disponibilidade(tenant.getId(), gabriel.getId(), QUARTA, "14:00", "18:00");
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "13:00", "18:00");
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "09:00", "12:00");
        // Faixa do colega para provar que a grade e filtrada por profissional.
        dados.disponibilidade(tenant.getId(), colega.getId(), SEGUNDA, "08:00", "10:00");

        given()
                .header(AUTH, bearer(tokenCliente))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(3))
                .body("[0].id", notNullValue())
                .body("[0].profissionalId", equalTo(gabriel.getId().toString()))
                .body("[0].diaSemana", equalTo(SEGUNDA))
                .body("[0].horaInicio", hora("09:00"))
                .body("[0].horaFim", hora("12:00"))
                .body("[1].diaSemana", equalTo(SEGUNDA))
                .body("[1].horaInicio", hora("13:00"))
                .body("[1].horaFim", hora("18:00"))
                .body("[2].diaSemana", equalTo(QUARTA))
                .body("[2].horaInicio", hora("14:00"))
                .body("[2].horaFim", hora("18:00"));
    }

    @Test
    @DisplayName("PUT troca a semana inteira: as faixas antigas somem e so as enviadas sobram")
    void putSubstituiAGradeInteira() {
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "07:00", "08:00");
        dados.disponibilidade(tenant.getId(), gabriel.getId(), QUARTA, "07:00", "08:00");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixa(QUARTA, "14:00", "18:00"),
                        faixa(SEGUNDA, "13:00", "18:00"),
                        faixa(SEGUNDA, "09:00", "12:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                // A resposta do PUT ja vem na ordem canonica do GET.
                .body("size()", equalTo(3))
                .body("[0].diaSemana", equalTo(SEGUNDA))
                .body("[0].horaInicio", hora("09:00"))
                .body("[0].horaFim", hora("12:00"))
                .body("[1].diaSemana", equalTo(SEGUNDA))
                .body("[1].horaInicio", hora("13:00"))
                .body("[2].diaSemana", equalTo(QUARTA))
                .body("[2].horaInicio", hora("14:00"));

        // O GET seguinte confirma que as faixas de 07:00 foram soft-deletadas.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(3))
                .body("[0].horaInicio", hora("09:00"))
                .body("[1].horaInicio", hora("13:00"))
                .body("[2].horaInicio", hora("14:00"));
    }

    @Test
    @DisplayName("PUT com lista vazia zera a grade do profissional")
    void putComListaVaziaZeraAGrade() {
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "09:00", "18:00");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(Map.of("faixas", List.of()))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("duas faixas sobrepostas no mesmo dia devolvem 422 FAIXAS_SOBREPOSTAS")
    void faixasSobrepostasNoMesmoDiaDevolvem422() {
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "07:00", "08:00");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "09:00", "13:00"),
                        faixa(SEGUNDA, "12:00", "18:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(422)
                .body("codigo", equalTo("FAIXAS_SOBREPOSTAS"))
                .body("status", equalTo(422))
                .body("mensagem", containsString("segunda-feira"));

        // A validacao roda antes de encostar no banco: a grade anterior continua de pe.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].horaInicio", hora("07:00"));
    }

    @Test
    @DisplayName("faixas apenas encostadas no mesmo dia (09:00-12:00 e 12:00-18:00) sao aceitas")
    void faixasEncostadasSaoAceitas() {
        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "09:00", "12:00"),
                        faixa(SEGUNDA, "12:00", "18:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].horaInicio", hora("09:00"))
                .body("[0].horaFim", hora("12:00"))
                .body("[1].horaInicio", hora("12:00"))
                .body("[1].horaFim", hora("18:00"));
    }

    @Test
    @DisplayName("faixa com horaFim menor ou igual a horaInicio devolve 422 FAIXA_INVALIDA")
    void faixaInvertidaDevolve422() {
        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "09:00", "12:00"),
                        faixa(QUARTA, "18:00", "09:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(422)
                .body("codigo", equalTo("FAIXA_INVALIDA"))
                .body("mensagem", containsString("quarta-feira"));

        // horaFim igual a horaInicio tambem e faixa invalida.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "10:00", "10:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(422)
                .body("codigo", equalTo("FAIXA_INVALIDA"));
    }

    @Test
    @DisplayName("profissional nao edita a grade de um colega: 403 e a agenda alheia intacta")
    void profissionalNaoEditaAgendaAlheia() {
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "09:00", "18:00");

        given()
                .header(AUTH, bearer(tokenColega))
                .contentType(ContentType.JSON)
                .body(grade(faixa(QUARTA, "08:00", "12:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(403);

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].diaSemana", equalTo(SEGUNDA))
                .body("[0].horaInicio", hora("09:00"));
    }

    @Test
    @DisplayName("admin edita a grade de qualquer profissional: 200")
    void adminEditaAgendaAlheia() {
        given()
                .header(AUTH, bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "10:00", "16:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].profissionalId", equalTo(gabriel.getId().toString()))
                .body("[0].diaSemana", equalTo(SEGUNDA))
                .body("[0].horaInicio", hora("10:00"))
                .body("[0].horaFim", hora("16:00"));
    }

    @Test
    @DisplayName("cliente nao tem papel para escrever grade: 403")
    void clienteNaoEditaGrade() {
        given()
                .header(AUTH, bearer(tokenCliente))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "09:00", "18:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(403);
    }

    @Test
    @DisplayName("GET e PUT de profissional inexistente devolvem 404")
    void profissionalInexistenteDevolve404() {
        UUID fantasma = UUID.randomUUID();

        given()
                .header(AUTH, bearer(tokenAdmin))
                .when().get("/profissionais/" + fantasma + "/disponibilidade")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));

        given()
                .header(AUTH, bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "09:00", "18:00")))
                .when().put("/profissionais/" + fantasma + "/disponibilidade")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("PUT com diaSemana fora de 0..6 devolve 400 VALIDACAO e nao toca na grade")
    void gradeComDiaSemanaForaDoIntervaloDevolve400() {
        dados.disponibilidade(tenant.getId(), gabriel.getId(), SEGUNDA, "09:00", "18:00");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixa(9, "09:00", "12:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", greaterThan(0))
                .body("violacoes.campo", hasItem("diaSemana"));

        // O Bean Validation barra antes do service: a grade anterior continua de pe.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].diaSemana", equalTo(SEGUNDA))
                .body("[0].horaInicio", hora("09:00"));
    }

    @Test
    @DisplayName("PUT com horaInicio nula devolve 400 VALIDACAO")
    void gradeComHoraInicioNulaDevolve400() {
        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(grade(faixaCrua(SEGUNDA, null, "18:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", greaterThan(0))
                .body("violacoes.campo", hasItem("horaInicio"));

        // Nenhuma faixa foi gravada pela requisicao recusada.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(disponibilidadeDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    // ------------------------------------------------------------ bloqueios

    @Test
    @DisplayName("POST cria bloqueio com 201, Location e corpo; e ele aparece na listagem")
    void criaBloqueioEListaDepois() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(13, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime fim = SEGUNDA_FUTURA.atTime(15, 0).atOffset(ZoneOffset.UTC);

        String id = given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                // Motivo com espacos sobrando: o service normaliza antes de gravar.
                .body(corpoBloqueio(inicio, fim, "  Curso de colorimetria  "))
                .when().post(bloqueiosDe(gabriel))
                .then()
                .statusCode(201)
                .header("Location",
                        containsString("/profissionais/" + gabriel.getId() + "/bloqueios/"))
                .body("id", notNullValue())
                .body("profissionalId", equalTo(gabriel.getId().toString()))
                .body("motivo", equalTo("Curso de colorimetria"))
                .body("createdAt", notNullValue())
                .extract().path("id");

        // Instantes conferidos pelo valor, nao pelo texto: o formato do JSON nao importa.
        ExtractableResponse<Response> listagem = given()
                .header(AUTH, bearer(tokenCliente))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(id))
                .body("[0].motivo", equalTo("Curso de colorimetria"))
                .extract();

        String inicioNaResposta = listagem.path("[0].inicio");
        String fimNaResposta = listagem.path("[0].fim");
        assertEquals(inicio.toInstant(), OffsetDateTime.parse(inicioNaResposta).toInstant());
        assertEquals(fim.toInstant(), OffsetDateTime.parse(fimNaResposta).toInstant());
    }

    @Test
    @DisplayName("listagem de bloqueios vem do mais recente para o mais antigo")
    void bloqueiosVemDoMaisRecenteParaOMaisAntigo() {
        OffsetDateTime antigo = SEGUNDA_FUTURA.atTime(8, 0).atOffset(ZoneOffset.UTC);
        OffsetDateTime recente = QUARTA_FUTURA.plusWeeks(2).atTime(8, 0).atOffset(ZoneOffset.UTC);
        dados.bloqueio(tenant.getId(), gabriel.getId(), antigo, antigo.plusHours(2), "Dentista");
        dados.bloqueio(tenant.getId(), gabriel.getId(), recente, recente.plusHours(2), "Viagem");
        // Bloqueio do colega nao pode vazar na listagem do Gabriel.
        dados.bloqueio(tenant.getId(), colega.getId(), antigo, antigo.plusHours(2), "Folga alheia");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].motivo", equalTo("Viagem"))
                .body("[1].motivo", equalTo("Dentista"))
                .body("[0].profissionalId", equalTo(gabriel.getId().toString()));
    }

    @Test
    @DisplayName("bloqueio com fim menor ou igual ao inicio devolve 422 BLOQUEIO_INVALIDO")
    void bloqueioInvertidoDevolve422() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(13, 0).atOffset(ZoneOffset.UTC);

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio, inicio.minusHours(1), "Fim antes do inicio"))
                .when().post(bloqueiosDe(gabriel))
                .then()
                .statusCode(422)
                .body("codigo", equalTo("BLOQUEIO_INVALIDO"))
                .body("status", equalTo(422));

        // Fim igual ao inicio tambem e recusado.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio, inicio, "Janela de duracao zero"))
                .when().post(bloqueiosDe(gabriel))
                .then()
                .statusCode(422)
                .body("codigo", equalTo("BLOQUEIO_INVALIDO"));

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("DELETE devolve 204 e o bloqueio some da listagem")
    void deleteRemoveBloqueio() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(9, 0).atOffset(ZoneOffset.UTC);
        Bloqueio removido = dados.bloqueio(tenant.getId(), gabriel.getId(),
                inicio, inicio.plusHours(2), "Folga");
        Bloqueio mantido = dados.bloqueio(tenant.getId(), gabriel.getId(),
                inicio.plusDays(1), inicio.plusDays(1).plusHours(2), "Curso");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().delete(bloqueiosDe(gabriel) + "/" + removido.getId())
                .then()
                .statusCode(204);

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(mantido.getId().toString()))
                .body("[0].motivo", equalTo("Curso"));
    }

    @Test
    @DisplayName("DELETE de bloqueio inexistente devolve 404")
    void deleteDeBloqueioInexistenteDevolve404() {
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().delete(bloqueiosDe(gabriel) + "/" + UUID.randomUUID())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("bloqueio de outro profissional e tratado como inexistente: 404, nunca 403")
    void deleteDeBloqueioDeOutroProfissionalDevolve404() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(9, 0).atOffset(ZoneOffset.UTC);
        Bloqueio doColega = dados.bloqueio(tenant.getId(), colega.getId(),
                inicio, inicio.plusHours(2), "Folga do Rafael");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().delete(bloqueiosDe(gabriel) + "/" + doColega.getId())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));

        // Continua vivo na agenda de quem e dono dele.
        given()
                .header(AUTH, bearer(tokenAdmin))
                .when().get(bloqueiosDe(colega))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(doColega.getId().toString()));
    }

    @Test
    @DisplayName("motivo so com espacos vira null e o campo nem aparece no JSON")
    void motivoEmBrancoNaoVaiNoJson() {
        OffsetDateTime inicio = QUARTA_FUTURA.atTime(10, 0).atOffset(ZoneOffset.UTC);

        ExtractableResponse<Response> criado = given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio, inicio.plusHours(1), "   "))
                .when().post(bloqueiosDe(gabriel))
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("profissionalId", equalTo(gabriel.getId().toString()))
                .extract();

        // normalizar() gravou null e serialization-inclusion=non-null tirou o campo do corpo.
        assertFalse(criado.asString().contains("motivo"),
                "o 201 nao deveria trazer motivo: " + criado.asString());

        ExtractableResponse<Response> listagem = given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .extract();

        assertFalse(listagem.asString().contains("motivo"),
                "a listagem nao deveria trazer motivo: " + listagem.asString());
    }

    @Test
    @DisplayName("POST de bloqueio sem inicio devolve 400 VALIDACAO e nao cria nada")
    void bloqueioSemInicioDevolve400() {
        OffsetDateTime fim = SEGUNDA_FUTURA.atTime(15, 0).atOffset(ZoneOffset.UTC);

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(Map.of("fim", iso(fim), "motivo", "So mandei o fim"))
                .when().post(bloqueiosDe(gabriel))
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", greaterThan(0))
                .body("violacoes.campo", hasItem("inicio"));

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("cliente nao tem papel para criar nem remover bloqueio: 403 nas duas rotas")
    void clienteNaoEscreveBloqueios() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(9, 0).atOffset(ZoneOffset.UTC);
        Bloqueio existente = dados.bloqueio(tenant.getId(), gabriel.getId(),
                inicio, inicio.plusHours(2), "Folga");

        // 403 do @RolesAllowed: corpo do Quarkus Security, que nao tem campo "codigo".
        given()
                .header(AUTH, bearer(tokenCliente))
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio.plusDays(1), inicio.plusDays(1).plusHours(2),
                        "Bloqueio de cliente"))
                .when().post(bloqueiosDe(gabriel))
                .then()
                .statusCode(403);

        given()
                .header(AUTH, bearer(tokenCliente))
                .when().delete(bloqueiosDe(gabriel) + "/" + existente.getId())
                .then()
                .statusCode(403);

        // Nada foi criado e nada foi removido: a agenda segue com o unico bloqueio original.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().get(bloqueiosDe(gabriel))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(existente.getId().toString()))
                .body("[0].motivo", equalTo("Folga"));
    }

    @Test
    @DisplayName("profissional nao cria bloqueio na agenda do colega: 403 ACESSO_NEGADO")
    void profissionalNaoCriaBloqueioNaAgendaDoColega() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(14, 0).atOffset(ZoneOffset.UTC);

        given()
                .header(AUTH, bearer(tokenGabriel))
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio, inicio.plusHours(2), "Bloqueio intruso"))
                .when().post(bloqueiosDe(colega))
                .then()
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"))
                .body("status", equalTo(403));

        // A agenda do colega continua sem nenhum bloqueio.
        given()
                .header(AUTH, bearer(tokenColega))
                .when().get(bloqueiosDe(colega))
                .then()
                .statusCode(200)
                .body("size()", equalTo(0));
    }

    @Test
    @DisplayName("na rota do colega o 403 vem antes do 404: nem bloqueio real nem id inventado passam")
    void profissionalNaoRemoveBloqueioDaAgendaDoColega() {
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(9, 0).atOffset(ZoneOffset.UTC);
        Bloqueio doColega = dados.bloqueio(tenant.getId(), colega.getId(),
                inicio, inicio.plusHours(2), "Folga do Rafael");

        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().delete(bloqueiosDe(colega) + "/" + doColega.getId())
                .then()
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"));

        // Mesmo com bloqueioId inexistente a checagem de dono roda primeiro: 403, nunca 404.
        given()
                .header(AUTH, bearer(tokenGabriel))
                .when().delete(bloqueiosDe(colega) + "/" + UUID.randomUUID())
                .then()
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"));

        // O bloqueio do colega segue vivo na agenda de quem e dono dele.
        given()
                .header(AUTH, bearer(tokenAdmin))
                .when().get(bloqueiosDe(colega))
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(doColega.getId().toString()))
                .body("[0].motivo", equalTo("Folga do Rafael"));
    }

    @Test
    @DisplayName("GET e POST de bloqueios de profissional inexistente devolvem 404")
    void bloqueiosDeProfissionalInexistenteDevolvem404() {
        UUID fantasma = UUID.randomUUID();
        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(13, 0).atOffset(ZoneOffset.UTC);

        given()
                .header(AUTH, bearer(tokenAdmin))
                .when().get("/profissionais/" + fantasma + "/bloqueios")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));

        // O admin passa pelo @RolesAllowed e pela checagem de dono: sobra o 404 do profissional.
        given()
                .header(AUTH, bearer(tokenAdmin))
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio, inicio.plusHours(2), "Agenda de ninguem"))
                .when().post("/profissionais/" + fantasma + "/bloqueios")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    // ----------------------------------------------------------------- auth

    @Test
    @DisplayName("todas as rotas da agenda respondem 401 sem token")
    void rotasSemTokenDevolvem401() {
        given().when().get(disponibilidadeDe(gabriel)).then().statusCode(401);

        given()
                .contentType(ContentType.JSON)
                .body(grade(faixa(SEGUNDA, "09:00", "18:00")))
                .when().put(disponibilidadeDe(gabriel))
                .then().statusCode(401);

        given().when().get(bloqueiosDe(gabriel)).then().statusCode(401);

        OffsetDateTime inicio = SEGUNDA_FUTURA.atTime(13, 0).atOffset(ZoneOffset.UTC);
        given()
                .contentType(ContentType.JSON)
                .body(corpoBloqueio(inicio, inicio.plusHours(1), "Sem token"))
                .when().post(bloqueiosDe(gabriel))
                .then().statusCode(401);

        given()
                .when().delete(bloqueiosDe(gabriel) + "/" + UUID.randomUUID())
                .then().statusCode(401);
    }

    // -------------------------------------------------------------- helpers

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String disponibilidadeDe(Profissional profissional) {
        return "/profissionais/" + profissional.getId() + "/disponibilidade";
    }

    private static String bloqueiosDe(Profissional profissional) {
        return "/profissionais/" + profissional.getId() + "/bloqueios";
    }

    private static Map<String, Object> faixa(int diaSemana, String horaInicio, String horaFim) {
        return Map.of("diaSemana", diaSemana, "horaInicio", horaInicio, "horaFim", horaFim);
    }

    /**
     * Mesma faixa, mas montada em HashMap para poder mandar campo nulo no JSON --
     * {@code Map.of} recusa null e o Bean Validation e justamente o que se quer testar.
     */
    private static Map<String, Object> faixaCrua(Object diaSemana, Object horaInicio,
            Object horaFim) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("diaSemana", diaSemana);
        corpo.put("horaInicio", horaInicio);
        corpo.put("horaFim", horaFim);
        return corpo;
    }

    @SafeVarargs
    private static Map<String, Object> grade(Map<String, Object>... faixas) {
        return Map.of("faixas", List.of(faixas));
    }

    private static Map<String, Object> corpoBloqueio(OffsetDateTime inicio, OffsetDateTime fim,
            String motivo) {
        return Map.of("inicio", iso(inicio), "fim", iso(fim), "motivo", motivo);
    }

    /** Serializa a mao para nao depender do ObjectMapper interno do RestAssured. */
    private static String iso(OffsetDateTime instante) {
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instante);
    }

    /**
     * O Jackson pode escrever LocalTime como "09:00" ou "09:00:00" conforme a
     * versao do jsr310; o teste aceita os dois em vez de amarrar no formato.
     */
    private static Matcher<String> hora(String hhmm) {
        return anyOf(equalTo(hhmm), equalTo(hhmm + ":00"));
    }
}
