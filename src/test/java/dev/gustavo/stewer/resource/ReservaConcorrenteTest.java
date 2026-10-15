package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
 * Duas pessoas reservando o mesmo horario ao mesmo tempo.
 *
 * <p>A checagem de conflito do service e um check-then-insert: consulta
 * {@code existeSobreposicao} e so entao insere. Entre a consulta e o insert
 * cabe a transacao do vizinho, e nenhuma das duas enxerga a outra -- em READ
 * COMMITTED cada uma le o estado de antes. Oito pedidos simultaneos viravam
 * oito agendamentos no mesmo slot, e dois clientes apareciam na porta do
 * estudio na mesma hora.
 *
 * <p>Quem resolve isso e o banco, com uma restricao EXCLUDE: o conflito passa a
 * ser impossivel por construcao, nao por ordem de execucao.
 */
@QuarkusTest
@DisplayName("POST /agendamentos sob concorrencia")
class ReservaConcorrenteTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");

    private static final LocalDate DIA = LocalDate.now(FUSO)
            .plusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final int DIA_SEMANA = DIA.getDayOfWeek().getValue() % 7;

    /** Quantos pedidos disparam juntos no mesmo slot. */
    private static final int PEDIDOS = 8;

    @Inject
    DadosDeTeste dados;

    private Profissional profissional;
    private Servico servico60;
    private final List<String> tokens = new ArrayList<>();

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        Usuario usuarioProfissional = dados.usuario(tenant.getId(), "Stewer Gabriel",
                "stewer@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), usuarioProfissional.getId(), "Visagismo");
        servico60 = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 18000);
        dados.disponibilidade(tenant.getId(), profissional.getId(), DIA_SEMANA, "09:00", "19:00");

        // Um cliente por pedido: assim nenhuma reserva e recusada por ser "do
        // mesmo cliente" e o unico motivo possivel de 409 e a disputa do slot.
        tokens.clear();
        for (int i = 0; i < PEDIDOS; i++) {
            dados.usuario(tenant.getId(), "Cliente " + i, "cliente" + i + "@exemplo.dev",
                    Role.CLIENTE);
            tokens.add(login("cliente" + i + "@exemplo.dev"));
        }
    }

    @Test
    @DisplayName(PEDIDOS + " pedidos simultaneos no mesmo slot geram 1 reserva e 7 conflitos")
    void apenasUmPedidoSimultaneoViraReserva() throws Exception {
        OffsetDateTime slot = em(DIA, "10:00");

        List<Integer> status = dispararJuntos(slot);

        long criados = status.stream().filter(s -> s == 201).count();
        long conflitos = status.stream().filter(s -> s == 409).count();

        assertEquals(1, criados,
                "exatamente um pedido pode vencer a disputa; vieram " + status);
        assertEquals(PEDIDOS - 1, conflitos,
                "os perdedores precisam receber 409, nao 500; vieram " + status);
    }

    @Test
    @DisplayName("o slot seguinte continua livre depois da disputa")
    void disputaNaoDerrubaOSlotVizinho() throws Exception {
        dispararJuntos(em(DIA, "10:00"));

        given()
                .header("Authorization", "Bearer " + tokens.get(0))
                .contentType(ContentType.JSON)
                .body(corpo(em(DIA, "11:00")))
                .when().post("/agendamentos")
                .then().statusCode(201);
    }

    @Test
    @DisplayName("agendamento cancelado nao impede uma nova reserva no mesmo horario")
    void canceladoLiberaOSlot() throws Exception {
        OffsetDateTime slot = em(DIA, "14:00");

        String id = given()
                .header("Authorization", "Bearer " + tokens.get(0))
                .contentType(ContentType.JSON)
                .body(corpo(slot))
                .when().post("/agendamentos")
                .then().statusCode(201)
                .extract().path("id");

        given()
                .header("Authorization", "Bearer " + tokens.get(0))
                .contentType(ContentType.JSON)
                .body(Map.of("status", "CANCELADO"))
                .when().patch("/agendamentos/{id}/status", id)
                .then().statusCode(200);

        // A restricao do banco so vale para AGENDADO: cancelado libera a agenda.
        given()
                .header("Authorization", "Bearer " + tokens.get(1))
                .contentType(ContentType.JSON)
                .body(corpo(slot))
                .when().post("/agendamentos")
                .then().statusCode(201);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    /** Solta os {@value #PEDIDOS} pedidos no mesmo instante, por um latch. */
    private List<Integer> dispararJuntos(OffsetDateTime slot) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PEDIDOS);
        CountDownLatch largada = new CountDownLatch(1);

        List<Callable<Integer>> tarefas = new ArrayList<>();
        for (String token : tokens) {
            tarefas.add(() -> {
                largada.await();
                return given()
                        .header("Authorization", "Bearer " + token)
                        .contentType(ContentType.JSON)
                        .body(corpo(slot))
                        .when().post("/agendamentos")
                        .then().extract().statusCode();
            });
        }

        List<Future<Integer>> futuros = new ArrayList<>();
        for (Callable<Integer> tarefa : tarefas) {
            futuros.add(pool.submit(tarefa));
        }
        largada.countDown();

        List<Integer> status = new ArrayList<>();
        for (Future<Integer> f : futuros) {
            status.add(f.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "o pool precisa encerrar");
        return status;
    }

    private Map<String, Object> corpo(OffsetDateTime inicio) {
        return Map.of(
                "profissionalId", profissional.getId().toString(),
                "servicoId", servico60.getId().toString(),
                "inicio", inicio.toString());
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
        return data.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
    }

}
