package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
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
import io.restassured.response.ExtractableResponse;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reservas: criacao, listagens e mudanca de status.
 *
 * <p>Todo cenario acontece numa segunda-feira daqui a um mes. A data e fixa (o
 * teste nunca depende da hora em que roda) e esta no futuro, para que a regra
 * que descarta slot no passado nunca derrube um caso por acidente. O dia da
 * semana e DERIVADO da data e usado para cadastrar a grade -- chutar "segunda =
 * 1" quebraria assim que a convencao do schema mudasse.
 *
 * <p>Os instantes devolvidos pela API sao comparados apos {@code parse}, e nao
 * como texto: a serializacao do Jackson omite os segundos quando eles sao zero,
 * e amarrar o teste nesse detalhe o tornaria fragil sem ganho nenhum.
 */
@QuarkusTest
@DisplayName("/agendamentos -- reservas, agenda do dia e mudanca de status")
class AgendamentoResourceTest {


    /** Fuso do estabelecimento: a grade e hora de parede, nao hora UTC. */
    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");

    /** Segunda-feira futura: deterministica e imune a regra do "horario no passado". */
    private static final LocalDate DIA = LocalDate.now(FUSO)
            .plusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    /** Segunda-feira que ja passou, para os casos de historico e de 422. */
    private static final LocalDate DIA_PASSADO = LocalDate.now(FUSO)
            .minusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    /** Convencao do schema: 0 = domingo ... 6 = sabado. */
    private static final int DIA_SEMANA = DIA.getDayOfWeek().getValue() % 7;

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Usuario usuarioProfissional;
    private Profissional profissional;
    private Usuario joana;
    private Usuario marcos;
    private Servico servico;

    private String tokenJoana;
    private String tokenMarcos;
    private String tokenProfissional;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        // O slug bate com stewer.tenant.slug-padrao para que o login dispense o tenantSlug.
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        usuarioProfissional = dados.usuario(tenant.getId(), "Gabriel Stewer",
                "gabriel@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), usuarioProfissional.getId(), "Visagismo");
        dados.usuario(tenant.getId(), "Ana Administradora", "ana@stewer.dev", Role.ADMIN);

        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        marcos = dados.usuario(tenant.getId(), "Marcos Lima", "marcos@exemplo.dev", Role.CLIENTE);

        servico = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 9000L);
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "19:00");

        tokenJoana = login("joana@exemplo.dev");
        tokenMarcos = login("marcos@exemplo.dev");
        tokenProfissional = login("gabriel@stewer.dev");
        tokenAdmin = login("ana@stewer.dev");
    }

    // ------------------------------------------------------------------
    // POST /agendamentos
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cliente reserva horario livre: 201 com Location e o agendamento pronto para a tela")
    void clienteAgendaComSucesso() {
        OffsetDateTime inicio = em(DIA, "10:00");

        ExtractableResponse<Response> resposta = given()
                .header("Authorization", "Bearer " + tokenJoana)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "profissionalId", profissional.getId().toString(),
                        "servicoId", servico.getId().toString(),
                        "inicio", inicio.toString(),
                        "observacao", "Chego cinco minutos antes"))
                .when().post("/agendamentos")
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("status", equalTo("AGENDADO"))
                .body("clienteId", equalTo(joana.getId().toString()))
                .body("clienteNome", equalTo("Joana Ribeiro"))
                .body("profissionalId", equalTo(profissional.getId().toString()))
                .body("profissionalNome", equalTo("Gabriel Stewer"))
                .body("servicoId", equalTo(servico.getId().toString()))
                .body("servicoNome", equalTo("Corte + Visagismo"))
                .body("duracaoMinutos", equalTo(60))
                .body("precoCentavos", equalTo(9000))
                .body("observacao", equalTo("Chego cinco minutos antes"))
                .body("createdAt", notNullValue())
                .extract();

        String id = resposta.path("id");
        String location = resposta.header("Location");
        assertTrue(location.endsWith("/agendamentos/" + id),
                "Location deveria apontar para o recurso criado, veio: " + location);

        // O fim nao vem do cliente: quem manda nele e a duracao do servico.
        assertEquals(inicio.toInstant(), instante(resposta.path("inicio")));
        assertEquals(inicio.plusMinutes(60).toInstant(), instante(resposta.path("fim")));
    }

    @Test
    @DisplayName("mesmo horario reservado de novo, por outro cliente, devolve 409 HORARIO_OCUPADO")
    void mesmoHorarioDevolve409() {
        agendar(tokenJoana, em(DIA, "10:00"));

        postAgendamento(tokenMarcos, corpo(profissional.getId(), servico.getId(), em(DIA, "10:00")))
                .statusCode(409)
                .body("codigo", equalTo("HORARIO_OCUPADO"))
                .body("status", equalTo(409))
                .body("mensagem", notNullValue());
    }

    @Test
    @DisplayName("horario que comeca exatamente no fim do anterior nao conflita: 201")
    void horarioEncostadoNaoConflita() {
        agendar(tokenJoana, em(DIA, "10:00"));

        OffsetDateTime encostado = em(DIA, "11:00");
        ExtractableResponse<Response> resposta =
                postAgendamento(tokenMarcos, corpo(profissional.getId(), servico.getId(), encostado))
                        .statusCode(201)
                        .body("status", equalTo("AGENDADO"))
                        .body("clienteNome", equalTo("Marcos Lima"))
                        .extract();

        assertEquals(encostado.toInstant(), instante(resposta.path("inicio")));

        // Os dois convivem na mesma agenda, um logo depois do outro.
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .queryParam("data", DIA.toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].clienteNome", equalTo("Joana Ribeiro"))
                .body("[1].clienteNome", equalTo("Marcos Lima"));
    }

    @Test
    @DisplayName("horario fora da grade 09:00-19:00 devolve 422 FORA_DA_DISPONIBILIDADE")
    void foraDaGradeDevolve422() {
        postAgendamento(tokenJoana, corpo(profissional.getId(), servico.getId(), em(DIA, "07:00")))
                .statusCode(422)
                .body("codigo", equalTo("FORA_DA_DISPONIBILIDADE"));

        postAgendamento(tokenJoana, corpo(profissional.getId(), servico.getId(), em(DIA, "20:00")))
                .statusCode(422)
                .body("codigo", equalTo("FORA_DA_DISPONIBILIDADE"));

        // Comeca dentro da grade, mas terminaria 19:30: nao cabe inteiro.
        postAgendamento(tokenJoana, corpo(profissional.getId(), servico.getId(), em(DIA, "18:30")))
                .statusCode(422)
                .body("codigo", equalTo("FORA_DA_DISPONIBILIDADE"));
    }

    @Test
    @DisplayName("horario em cima de um bloqueio devolve 409 HORARIO_BLOQUEADO")
    void horarioBloqueadoDevolve409() {
        dados.bloqueio(tenant.getId(), profissional.getId(),
                em(DIA, "14:00"), em(DIA, "15:00"), "Compromisso pessoal");

        postAgendamento(tokenJoana, corpo(profissional.getId(), servico.getId(), em(DIA, "14:00")))
                .statusCode(409)
                .body("codigo", equalTo("HORARIO_BLOQUEADO"));
    }

    @Test
    @DisplayName("horario que ja passou devolve 422 HORARIO_NO_PASSADO")
    void horarioNoPassadoDevolve422() {
        // Mesma segunda-feira da grade, so que num mes que ja passou: quem
        // reprova e a regra do passado, nao a da disponibilidade.
        postAgendamento(tokenJoana,
                corpo(profissional.getId(), servico.getId(), em(DIA_PASSADO, "10:00")))
                .statusCode(422)
                .body("codigo", equalTo("HORARIO_NO_PASSADO"));
    }

    @Test
    @DisplayName("servicoId inexistente devolve 404")
    void servicoInexistenteDevolve404() {
        postAgendamento(tokenJoana, corpo(profissional.getId(), UUID.randomUUID(), em(DIA, "10:00")))
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("profissionalId inexistente devolve 404")
    void profissionalInexistenteDevolve404() {
        postAgendamento(tokenJoana, corpo(UUID.randomUUID(), servico.getId(), em(DIA, "10:00")))
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("servico desativado nao aceita reserva: 422 SERVICO_INATIVO")
    void servicoInativoDevolve422() {
        // A desativacao passa pela propria API (PUT do ADMIN) em vez de escrever
        // direto no banco: assim o teste cobre o caminho real do catalogo.
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "nome", "Corte + Visagismo",
                        "duracaoMinutos", 60,
                        "precoCentavos", 9000,
                        "ativo", false))
                .when().put("/servicos/{id}", servico.getId().toString())
                .then()
                .statusCode(200)
                .body("ativo", equalTo(false));

        postAgendamento(tokenJoana, corpo(profissional.getId(), servico.getId(), em(DIA, "10:00")))
                .statusCode(422)
                .body("codigo", equalTo("SERVICO_INATIVO"));
    }

    @Test
    @DisplayName("profissional desativado nao aceita reserva: 422 PROFISSIONAL_INATIVO")
    void profissionalInativoDevolve422() {
        // usuarioId vai no corpo porque o PUT o exige como confirmacao do dono
        // da ficha; o que muda de fato aqui e o ativo.
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "usuarioId", usuarioProfissional.getId().toString(),
                        "especialidade", "Visagismo",
                        "ativo", false))
                .when().put("/profissionais/{id}", profissional.getId().toString())
                .then()
                .statusCode(200)
                .body("ativo", equalTo(false));

        postAgendamento(tokenJoana, corpo(profissional.getId(), servico.getId(), em(DIA, "10:00")))
                .statusCode(422)
                .body("codigo", equalTo("PROFISSIONAL_INATIVO"));
    }

    @Test
    @DisplayName("profissional nao reserva horario para si: 403")
    void profissionalNaoAgendaDevolve403() {
        // O corpo do 403 vem do Quarkus Security, entao aqui so o status importa.
        postAgendamento(tokenProfissional,
                corpo(profissional.getId(), servico.getId(), em(DIA, "10:00")))
                .statusCode(403);
    }

    @Test
    @DisplayName("agendar sem token devolve 401")
    void semTokenDevolve401() {
        given()
                .contentType(ContentType.JSON)
                .body(corpo(profissional.getId(), servico.getId(), em(DIA, "10:00")))
                .when().post("/agendamentos")
                .then()
                .statusCode(401);
    }

    @Test
    @DisplayName("corpo sem profissionalId, servicoId e inicio devolve 400 VALIDACAO com os tres campos")
    void corpoIncompletoDevolve400() {
        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .contentType(ContentType.JSON)
                .body(Map.of("observacao", "so mandei a observacao"))
                .when().post("/agendamentos")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"))
                .body("violacoes.size()", equalTo(3))
                .body("violacoes.campo",
                        containsInAnyOrder("profissionalId", "servicoId", "inicio"));
    }

    // ------------------------------------------------------------------
    // GET /agendamentos/meus
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cada cliente enxerga apenas os proprios agendamentos em /meus")
    void meusTrazSomenteDoProprioCliente() {
        String daJoana = agendar(tokenJoana, em(DIA, "09:00"));
        String doMarcos = agendar(tokenMarcos, em(DIA, "10:00"));

        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(daJoana))
                .body("[0].clienteId", equalTo(joana.getId().toString()))
                .body("[0].clienteNome", equalTo("Joana Ribeiro"))
                .body("[0].servicoNome", equalTo("Corte + Visagismo"));

        given()
                .header("Authorization", "Bearer " + tokenMarcos)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(doMarcos))
                .body("[0].clienteId", equalTo(marcos.getId().toString()))
                .body("[0].clienteNome", equalTo("Marcos Lima"));
    }

    @Test
    @DisplayName("periodo=futuros traz so o que ainda vai acontecer; historico traz so o que passou")
    void meusFiltraPorPeriodo() {
        // Direto no banco: o endpoint (com razao) recusaria criar algo no passado.
        dados.agendamento(tenant.getId(), joana.getId(), profissional.getId(), servico.getId(),
                em(DIA_PASSADO, "10:00"), em(DIA_PASSADO, "11:00"), StatusAgendamento.CONCLUIDO);
        String futuro = agendar(tokenJoana, em(DIA, "15:00"));

        // Sem filtro: os dois, do mais recente para o mais antigo.
        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].status", equalTo("AGENDADO"))
                .body("[1].status", equalTo("CONCLUIDO"));

        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .queryParam("periodo", "futuros")
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(futuro))
                .body("[0].status", equalTo("AGENDADO"));

        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .queryParam("periodo", "historico")
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].status", equalTo("CONCLUIDO"));
    }

    // ------------------------------------------------------------------
    // GET /agendamentos/agenda
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profissional ve a agenda do dia com todos os status, ordenada por inicio")
    void agendaDoDiaComoProfissional() {
        agendar(tokenJoana, em(DIA, "10:00"));
        dados.agendamento(tenant.getId(), marcos.getId(), profissional.getId(), servico.getId(),
                em(DIA, "15:00"), em(DIA, "16:00"), StatusAgendamento.CANCELADO);
        // Dia seguinte: nao pode vazar para a agenda consultada.
        dados.agendamento(tenant.getId(), marcos.getId(), profissional.getId(), servico.getId(),
                em(DIA.plusDays(1), "10:00"), em(DIA.plusDays(1), "11:00"),
                StatusAgendamento.AGENDADO);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .queryParam("data", DIA.toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].status", equalTo("AGENDADO"))
                .body("[0].clienteNome", equalTo("Joana Ribeiro"))
                .body("[0].profissionalNome", equalTo("Gabriel Stewer"))
                .body("[1].status", equalTo("CANCELADO"))
                .body("[1].clienteNome", equalTo("Marcos Lima"));
    }

    @Test
    @DisplayName("admin sem profissionalId nao tem agenda propria: 422 PROFISSIONAL_OBRIGATORIO")
    void agendaComoAdminSemProfissionalIdDevolve422() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("data", DIA.toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(422)
                .body("codigo", equalTo("PROFISSIONAL_OBRIGATORIO"));
    }

    @Test
    @DisplayName("admin informando profissionalId ve a agenda daquele profissional: 200")
    void agendaComoAdminComProfissionalId() {
        agendar(tokenJoana, em(DIA, "10:00"));

        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("data", DIA.toString())
                .queryParam("profissionalId", profissional.getId().toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].profissionalId", equalTo(profissional.getId().toString()))
                .body("[0].clienteNome", equalTo("Joana Ribeiro"))
                .body("[0].status", equalTo("AGENDADO"));
    }

    @Test
    @DisplayName("admin com profissionalId inexistente na agenda devolve 404")
    void agendaComoAdminComProfissionalInexistenteDevolve404() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .queryParam("data", DIA.toString())
                .queryParam("profissionalId", UUID.randomUUID().toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("usuario PROFISSIONAL sem ficha nao tem agenda: 403 ACESSO_NEGADO")
    void agendaSemFichaDeProfissionalDevolve403() {
        // Role no token, nenhuma ficha na equipe. A rota existe e o papel entra
        // no @RolesAllowed: quem barra e o dominio, entao o corpo traz codigo.
        dados.usuario(tenant.getId(), "Rafael Sem Ficha", "rafael@stewer.dev", Role.PROFISSIONAL);
        String tokenSemFicha = login("rafael@stewer.dev");

        given()
                .header("Authorization", "Bearer " + tokenSemFicha)
                .queryParam("data", DIA.toString())
                .when().get("/agendamentos/agenda")
                .then()
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"));
    }

    // ------------------------------------------------------------------
    // PATCH /agendamentos/{id}/status
    // ------------------------------------------------------------------

    @Test
    @DisplayName("profissional conclui o atendimento da propria agenda: 200 com status CONCLUIDO")
    void profissionalConcluiAgendamento() {
        String id = agendar(tokenJoana, em(DIA, "10:00"));

        patchStatus(tokenProfissional, id, StatusAgendamento.CONCLUIDO)
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("CONCLUIDO"));

        // O cliente enxerga o novo status na propria lista.
        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].status", equalTo("CONCLUIDO"));
    }

    @Test
    @DisplayName("cliente cancela o proprio agendamento: 200 com status CANCELADO")
    void clienteCancelaOProprio() {
        String id = agendar(tokenJoana, em(DIA, "10:00"));

        patchStatus(tokenJoana, id, StatusAgendamento.CANCELADO)
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("CANCELADO"));
    }

    @Test
    @DisplayName("cliente nao conclui atendimento, so cancela: 403")
    void clienteNaoConcluiDevolve403() {
        String id = agendar(tokenJoana, em(DIA, "10:00"));

        patchStatus(tokenJoana, id, StatusAgendamento.CONCLUIDO)
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"));

        // Continua AGENDADO: a tentativa nao pode ter efeito colateral.
        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("AGENDADO"));
    }

    @Test
    @DisplayName("cliente nao cancela agendamento de outro cliente: 403")
    void clienteNaoCancelaDeOutroDevolve403() {
        String doMarcos = agendar(tokenMarcos, em(DIA, "10:00"));

        patchStatus(tokenJoana, doMarcos, StatusAgendamento.CANCELADO)
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"));

        given()
                .header("Authorization", "Bearer " + tokenMarcos)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("AGENDADO"));
    }

    @Test
    @DisplayName("agendamento ja cancelado nao muda mais de status: 409 STATUS_FINAL")
    void patchEmCanceladoDevolve409() {
        String id = agendar(tokenJoana, em(DIA, "10:00"));
        patchStatus(tokenJoana, id, StatusAgendamento.CANCELADO).statusCode(200);

        patchStatus(tokenProfissional, id, StatusAgendamento.CONCLUIDO)
                .statusCode(409)
                .body("codigo", equalTo("STATUS_FINAL"));
    }

    @Test
    @DisplayName("patch em agendamento inexistente devolve 404")
    void patchEmInexistenteDevolve404() {
        patchStatus(tokenProfissional, UUID.randomUUID().toString(), StatusAgendamento.CONCLUIDO)
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("admin muda o status de agendamento alheio: 200 com NAO_COMPARECEU")
    void adminMudaStatusDeAgendamentoAlheio() {
        String id = agendar(tokenJoana, em(DIA, "10:00"));

        // Ana nao e a cliente nem a dona da agenda: o acesso vem so do papel de
        // ADMIN, que passa direto pela checagem de permissao.
        patchStatus(tokenAdmin, id, StatusAgendamento.NAO_COMPARECEU)
                .statusCode(200)
                .body("id", equalTo(id))
                .body("status", equalTo("NAO_COMPARECEU"));

        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].status", equalTo("NAO_COMPARECEU"));
    }

    @Test
    @DisplayName("profissional nao mexe em agendamento da agenda de outro: 403 ACESSO_NEGADO")
    void profissionalDeOutraAgendaDevolve403() {
        String daAgendaDoGabriel = agendar(tokenJoana, em(DIA, "10:00"));

        // A segunda profissional nasce dentro do teste para nao alterar o
        // cenario montado no @BeforeEach para os demais casos.
        Usuario usuarioOutro = dados.usuario(tenant.getId(), "Bruna Alves",
                "bruna@stewer.dev", Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), usuarioOutro.getId(), "Colorimetria");
        String tokenOutroProfissional = login("bruna@stewer.dev");

        patchStatus(tokenOutroProfissional, daAgendaDoGabriel, StatusAgendamento.CONCLUIDO)
                .statusCode(403)
                .body("codigo", equalTo("ACESSO_NEGADO"));

        // O atendimento do Gabriel continua intacto.
        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("AGENDADO"));
    }

    @Test
    @DisplayName("agendamento nao volta para AGENDADO: 422 STATUS_INVALIDO")
    void voltarParaAgendadoDevolve422() {
        // O agendamento tem de continuar em AGENDADO: num status final quem
        // responderia antes seria a checagem de STATUS_FINAL, e nao esta.
        String id = agendar(tokenJoana, em(DIA, "10:00"));

        patchStatus(tokenProfissional, id, StatusAgendamento.AGENDADO)
                .statusCode(422)
                .body("codigo", equalTo("STATUS_INVALIDO"));

        given()
                .header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then()
                .statusCode(200)
                .body("[0].status", equalTo("AGENDADO"));
    }

    // ------------------------------------------------------------------
    // Fluxo completo
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cancelar devolve o slot para a lista de horarios livres")
    void cancelamentoDevolveOSlotParaHorariosLivres() {
        // Normalizado em UTC igual a horariosLivres(): OffsetDateTime.equals compara
        // tambem o offset, entao 10:00-03:00 e 13:00Z nao sao "iguais" apesar de
        // serem o mesmo instante.
        OffsetDateTime slot = em(DIA, "10:00").withOffsetSameInstant(ZoneOffset.UTC);

        // Grade 09:00-19:00 fatiada de hora em hora: 09, 10, ... 18.
        List<OffsetDateTime> antes = horariosLivres();
        assertEquals(10, antes.size(), "grade de 10 horas com servico de 60min gera 10 slots");
        assertTrue(antes.contains(slot), "o slot das 10:00 deveria comecar livre");

        String id = agendar(tokenJoana, slot);

        List<OffsetDateTime> comReserva = horariosLivres();
        assertEquals(9, comReserva.size());
        assertFalse(comReserva.contains(slot), "o slot reservado nao pode continuar sendo oferecido");

        patchStatus(tokenJoana, id, StatusAgendamento.CANCELADO)
                .statusCode(200)
                .body("status", equalTo("CANCELADO"));

        List<OffsetDateTime> depois = horariosLivres();
        assertTrue(depois.contains(slot), "cancelado libera a agenda de novo");
        assertEquals(antes, depois, "a lista deve voltar exatamente ao estado inicial");
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private ValidatableResponse postAgendamento(String token, Map<String, Object> corpo) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post("/agendamentos")
                .then();
    }

    /** Reserva o horario e devolve o id, ja exigindo o 201. */
    private String agendar(String token, OffsetDateTime inicio) {
        return postAgendamento(token, corpo(profissional.getId(), servico.getId(), inicio))
                .statusCode(201)
                .extract().path("id");
    }

    private ValidatableResponse patchStatus(String token, String agendamentoId,
            StatusAgendamento status) {
        return given()
                .header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("status", status.name()))
                .when().patch("/agendamentos/{id}/status", agendamentoId)
                .then();
    }

    /** Inicios livres do dia de teste, normalizados em UTC para comparacao direta. */
    private List<OffsetDateTime> horariosLivres() {
        List<String> brutos = given()
                .header("Authorization", "Bearer " + tokenJoana)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", servico.getId().toString())
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then().statusCode(200)
                .extract().path("horarios.inicio");

        return brutos.stream()
                .map(OffsetDateTime::parse)
                .map(momento -> momento.withOffsetSameInstant(ZoneOffset.UTC))
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

    private static Map<String, Object> corpo(UUID profissionalId, UUID servicoId,
            OffsetDateTime inicio) {
        return Map.of(
                "profissionalId", profissionalId.toString(),
                "servicoId", servicoId.toString(),
                "inicio", inicio.toString());
    }

    /** Hora de parede do estabelecimento -> instante. */
    private static OffsetDateTime em(LocalDate data, String hora) {
        return data.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
    }

    private static Instant instante(String iso) {
        return OffsetDateTime.parse(iso).toInstant();
    }
}
