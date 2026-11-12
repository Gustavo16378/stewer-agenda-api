package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import dev.gustavo.stewer.push.NotificacaoPush;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Notificacao no celular: quem mexe na agenda avisa o outro lado. */
@QuarkusTest
@DisplayName("aviso no celular -- cliente marca, profissional recebe; equipe marca, cliente recebe")
class AvisoNoCelularTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");
    private static final LocalDate DIA = LocalDate.now(FUSO).plusWeeks(2)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    @Inject
    DadosDeTeste dados;

    @Inject
    NotificacaoPush push;

    private Usuario joana;
    private Profissional gabriel;
    private Servico servico;
    private String tokenJoana;
    private String tokenGabriel;

    @BeforeEach
    void preparar() {
        dados.limpar();
        push.limparRetidas();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario u = dados.usuario(tenant.getId(), "Gabriel", "gabriel@stewer.dev", Role.PROFISSIONAL);
        gabriel = dados.profissional(tenant.getId(), u.getId(), "Visagismo");
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        servico = dados.servico(tenant.getId(), "Corte", 60, 9000L);
        dados.disponibilidade(tenant.getId(), gabriel.getId(), DIA.getDayOfWeek().getValue() % 7,
                "09:00", "19:00");
        tokenJoana = login("joana@exemplo.dev");
        tokenGabriel = login("gabriel@stewer.dev");
        registrar(tokenJoana, "ExponentPushToken[joana]");
        registrar(tokenGabriel, "ExponentPushToken[gabriel]");
    }

    @Test
    @DisplayName("cliente marca: o profissional recebe 'Novo horario'; ela nao recebe o proprio aviso")
    void clienteMarca() {
        marcar(tokenJoana, null);
        List<NotificacaoPush.Mensagem> m = push.retidas();
        assertEquals(1, m.size());
        assertEquals("ExponentPushToken[gabriel]", m.get(0).para());
        assertEquals("Novo horário", m.get(0).titulo());
        assertTrue(m.get(0).corpo().startsWith("Joana Ribeiro · Corte"));
    }

    @Test
    @DisplayName("profissional marca pela cliente: ela recebe 'Horario marcado para voce'")
    void equipeMarca() {
        marcar(tokenGabriel, joana.getId().toString());
        List<NotificacaoPush.Mensagem> m = push.retidas();
        assertEquals(1, m.size());
        assertEquals("ExponentPushToken[joana]", m.get(0).para());
        assertEquals("Horário marcado para você", m.get(0).titulo());
    }

    @Test
    @DisplayName("depois de remover o celular, nao chega mais nada nele")
    void removerDispositivo() {
        given().header("Authorization", "Bearer " + tokenGabriel)
                .contentType(ContentType.JSON)
                .body(Map.of("token", "ExponentPushToken[gabriel]"))
                .when().post("/auth/eu/dispositivos/remover")
                .then().statusCode(204);
        marcar(tokenJoana, null);
        assertTrue(push.retidas().isEmpty());
    }

    private void marcar(String token, String clienteId) {
        Map<String, Object> corpo = new java.util.HashMap<>(Map.of(
                "profissionalId", gabriel.getId().toString(),
                "servicoId", servico.getId().toString(),
                "inicio", DIA.atTime(LocalTime.of(10, 0)).atZone(FUSO).toOffsetDateTime().toString()));
        if (clienteId != null) {
            corpo.put("clienteId", clienteId);
        }
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON).body(corpo)
                .when().post("/agendamentos")
                .then().statusCode(201);
    }

    private void registrar(String token, String expoToken) {
        given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("token", expoToken))
                .when().post("/auth/eu/dispositivos")
                .then().statusCode(204);
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }
}
