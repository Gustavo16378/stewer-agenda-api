package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import dev.gustavo.stewer.domain.Role;
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
 * Unicidade sob concorrencia: o perdedor merece 409, nao 500.
 *
 * <p>Os tres casos sao check-then-insert, como era a reserva de horario:
 * consulta "ja existe?", nao existe, insere. Duas transacoes simultaneas leem o
 * mesmo "nao existe".
 *
 * <p>Dois deles ja tinham indice unico no banco e paravam no lugar certo -- so
 * que a violacao subia crua e virava 500 ERRO_INTERNO, com stack trace no log e
 * uma mensagem inutil para quem tocou o botao duas vezes. O terceiro, o nome do
 * servico, nao tinha indice nenhum: oito criacoes simultaneas viravam oito
 * servicos com o mesmo nome no catalogo.
 */
@QuarkusTest
@DisplayName("Unicidade sob concorrencia")
class UnicidadeConcorrenteTest {

    private static final int PEDIDOS = 8;

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Gabriel Admin", "admin@stewer.dev", Role.ADMIN);
        tokenAdmin = login("admin@stewer.dev");
    }

    @Test
    @DisplayName("cadastros simultaneos do mesmo e-mail: 1 criado, 7 conflitos")
    void cadastroDoMesmoEmail() throws Exception {
        List<Integer> status = juntos(() -> given()
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Joana", "email", "joana@exemplo.dev", "senha", "senha12345"))
                .when().post("/auth/registrar")
                .then().extract().statusCode());

        assertEquals(1, criados(status), "so um cadastro pode vencer; vieram " + status);
        assertEquals(PEDIDOS - 1, conflitos(status),
                "os perdedores precisam de 409, nao 500; vieram " + status);
    }

    @Test
    @DisplayName("servicos simultaneos com o mesmo nome: 1 criado, 7 conflitos")
    void servicoComMesmoNome() throws Exception {
        List<Integer> status = juntos(() -> given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Corte + Visagismo", "duracaoMinutos", 60,
                        "precoCentavos", 18000))
                .when().post("/servicos")
                .then().extract().statusCode());

        assertEquals(1, criados(status), "so um servico pode vencer; vieram " + status);
        assertEquals(PEDIDOS - 1, conflitos(status),
                "os perdedores precisam de 409, nao 500; vieram " + status);
    }

    @Test
    @DisplayName("fichas simultaneas para o mesmo usuario: 1 criada, 7 conflitos")
    void fichaDoMesmoUsuario() throws Exception {
        Usuario futuro = dados.usuario(tenant.getId(), "Bruno Visagista", "bruno@stewer.dev",
                Role.CLIENTE);

        List<Integer> status = juntos(() -> given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("usuarioId", futuro.getId().toString(), "especialidade", "Visagismo"))
                .when().post("/profissionais")
                .then().extract().statusCode());

        assertEquals(1, criados(status), "so uma ficha pode vencer; vieram " + status);
        assertEquals(PEDIDOS - 1, conflitos(status),
                "os perdedores precisam de 409, nao 500; vieram " + status);
    }

    @Test
    @DisplayName("nome livre de novo depois que o servico e removido")
    void nomeVoltaAFicarLivreAposRemocao() {
        String id = given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Barba", "duracaoMinutos", 30, "precoCentavos", 7000))
                .when().post("/servicos")
                .then().statusCode(201)
                .extract().path("id");

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().delete("/servicos/{id}", id)
                .then().statusCode(204);

        // O indice unico vale so para os vivos: o nome do removido nao bloqueia.
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("nome", "Barba", "duracaoMinutos", 30, "precoCentavos", 7000))
                .when().post("/servicos")
                .then().statusCode(201);
    }

    // ------------------------------------------------------------------

    private static long criados(List<Integer> status) {
        return status.stream().filter(s -> s == 201).count();
    }

    private static long conflitos(List<Integer> status) {
        return status.stream().filter(s -> s == 409).count();
    }

    /** Dispara {@value #PEDIDOS} vezes a mesma chamada, soltas por um latch. */
    private static List<Integer> juntos(Callable<Integer> chamada) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(PEDIDOS);
        CountDownLatch largada = new CountDownLatch(1);

        List<Future<Integer>> futuros = new ArrayList<>();
        for (int i = 0; i < PEDIDOS; i++) {
            futuros.add(pool.submit(() -> {
                largada.await();
                return chamada.call();
            }));
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

    private String login(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
