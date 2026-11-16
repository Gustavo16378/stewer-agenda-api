package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
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
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O Stewer atendeu as 15h alguem que nao marcou pelo app e lanca depois: o
 * atendimento entra ja concluido, no passado, fora da grade se for o caso, e
 * aparece no historico do cliente e no financeiro.
 */
@QuarkusTest
@DisplayName("lancar atendimento ja feito -- passado, concluido, so a equipe")
class AtendimentoJaFeitoTest {

    @Inject
    DadosDeTeste dados;

    private Profissional profissional;
    private Servico corte;
    private Servico barba;
    private Usuario joana;
    private String tokenStewer;
    private String tokenJoana;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario stewer = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev",
                Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), stewer.getId(), "Visagismo");
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        corte = dados.servico(tenant.getId(), "Corte", 60, 9000L);
        barba = dados.servico(tenant.getId(), "Barba", 30, 5000L);
        // sem nenhuma grade cadastrada: lancar o que ja aconteceu nao depende dela
        tokenStewer = login("stewer@stewer.dev");
        tokenJoana = login("joana@exemplo.dev");
    }

    @Test
    @DisplayName("tres horas atras, corte + barba: entra CONCLUIDO e soma no historico do cliente")
    void lancaConcluido() {
        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC).minusHours(3)
                .truncatedTo(ChronoUnit.MINUTES);

        String id = lancar(tokenStewer, inicio, true)
                .statusCode(201)
                .body("status", equalTo("CONCLUIDO"))
                .body("servicoNome", equalTo("Corte + Barba"))
                .body("precoCentavos", equalTo(14000))
                .extract().path("id");

        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then().statusCode(200)
                .body("id", hasItem(id));

        // concluido nao ocupa a agenda: nao entra nas proximas do cliente
        given().header("Authorization", "Bearer " + tokenStewer)
                .when().get("/clientes/{id}", joana.getId())
                .then().statusCode(200)
                .body("metricas.visitas", equalTo(1));
    }

    @Test
    @DisplayName("horario que ainda nao chegou e 422, mais de 30 dias atras tambem")
    void foraDaJanela() {
        lancar(tokenStewer, OffsetDateTime.now(ZoneOffset.UTC).plusHours(2), true)
                .statusCode(422)
                .body("codigo", equalTo("ATENDIMENTO_NO_FUTURO"));

        lancar(tokenStewer, OffsetDateTime.now(ZoneOffset.UTC).minusDays(31), true)
                .statusCode(422)
                .body("codigo", equalTo("ATENDIMENTO_ANTIGO_DEMAIS"));
    }

    @Test
    @DisplayName("sem jaAtendido, horario no passado continua recusado como antes")
    void semAFlagContinuaRecusando() {
        lancar(tokenStewer, OffsetDateTime.now(ZoneOffset.UTC).minusHours(1), false)
                .statusCode(422)
                .body("codigo", equalTo("HORARIO_NO_PASSADO"));
    }

    @Test
    @DisplayName("cliente nao lanca atendimento ja feito")
    void clienteNaoLanca() {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("profissionalId", profissional.getId().toString());
        corpo.put("servicoIds", List.of(corte.getId().toString()));
        corpo.put("inicio", OffsetDateTime.now(ZoneOffset.UTC).minusHours(2).toString());
        corpo.put("jaAtendido", true);
        given().header("Authorization", "Bearer " + tokenJoana)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post("/agendamentos")
                .then().statusCode(403);

        given().header("Authorization", "Bearer " + tokenJoana)
                .when().get("/agendamentos/meus")
                .then().statusCode(200)
                .body("status", not(hasItem("CONCLUIDO")));
    }

    // ------------------------------------------------------------------

    private ValidatableResponse lancar(String token, OffsetDateTime inicio, boolean jaAtendido) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("profissionalId", profissional.getId().toString());
        corpo.put("servicoIds", List.of(corte.getId().toString(), barba.getId().toString()));
        corpo.put("clienteId", joana.getId().toString());
        corpo.put("inicio", inicio.toString());
        if (jaAtendido) {
            corpo.put("jaAtendido", true);
        }
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post("/agendamentos")
                .then();
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
