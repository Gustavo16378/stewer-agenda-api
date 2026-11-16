package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
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
 * O Stewer recebe o pedido pelo WhatsApp de quem nunca abriu o app: cadastra o
 * cliente pelo painel (nome e telefone, e-mail se tiver) e marca o horario no
 * nome dele. Se a pessoa criar a conta depois com o mesmo e-mail, ela assume
 * esse cadastro e ve o horario que ja estava marcado.
 */
@QuarkusTest
@DisplayName("cliente cadastrado pela equipe -- cadastro pelo painel, horario e conta depois")
class ClienteCadastradoPelaEquipeTest {

    private static final ZoneId FUSO = ZoneId.of("America/Araguaina");
    private static final LocalDate DIA = LocalDate.now(FUSO)
            .plusWeeks(2)
            .with(TemporalAdjusters.next(DayOfWeek.TUESDAY));

    @Inject
    DadosDeTeste dados;

    private Profissional profissional;
    private Servico servico;
    private String tokenStewer;
    private String tokenCliente;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario stewer = dados.usuario(tenant.getId(), "Stewer Gabriel", "stewer@stewer.dev",
                Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), stewer.getId(), "Visagismo");
        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        servico = dados.servico(tenant.getId(), "Corte", 60, 9000L);
        dados.disponibilidade(tenant.getId(), profissional.getId(),
                DIA.getDayOfWeek().getValue() % 7, "09:00", "18:00");
        tokenStewer = login("stewer@stewer.dev", DadosDeTeste.SENHA);
        tokenCliente = login("joana@exemplo.dev", DadosDeTeste.SENHA);
    }

    @Test
    @DisplayName("so com nome e WhatsApp: cadastra, aparece na busca e recebe horario marcado pelo Stewer")
    void cadastraSemEmailEMarca() {
        String id = cadastrar(tokenStewer, "Carlos Andrade", "(63) 99111-0000", null)
                .statusCode(201)
                .header("Location", notNullValue())
                .body("nome", equalTo("Carlos Andrade"))
                .body("telefone", equalTo("(63) 99111-0000"))
                .body("email", nullValue())
                .extract().path("id");

        given().header("Authorization", "Bearer " + tokenStewer)
                .queryParam("busca", "99111")
                .when().get("/clientes")
                .then().statusCode(200)
                .body("id", hasItem(id));

        // o numero escrito de outro jeito tambem acha: so os digitos contam
        given().header("Authorization", "Bearer " + tokenStewer)
                .queryParam("busca", "63 991110000")
                .when().get("/clientes")
                .then().statusCode(200)
                .body("id", hasItem(id));

        given().header("Authorization", "Bearer " + tokenStewer)
                .contentType(ContentType.JSON)
                .body(Map.of("profissionalId", profissional.getId().toString(),
                        "servicoIds", java.util.List.of(servico.getId().toString()),
                        "clienteId", id,
                        "inicio", em("14:00").toString()))
                .when().post("/agendamentos")
                .then().statusCode(201)
                .body("clienteNome", equalTo("Carlos Andrade"));
    }

    @Test
    @DisplayName("cadastrado com e-mail: nao entra antes de criar a conta; ao criar, assume o cadastro e o horario")
    void criaContaDepoisEAssume() {
        String id = cadastrar(tokenStewer, "Bia Souza", "63 98888-1111", "Bia@Exemplo.dev")
                .statusCode(201)
                .body("email", equalTo("bia@exemplo.dev"))
                .extract().path("id");

        given().header("Authorization", "Bearer " + tokenStewer)
                .contentType(ContentType.JSON)
                .body(Map.of("profissionalId", profissional.getId().toString(),
                        "servicoId", servico.getId().toString(),
                        "clienteId", id,
                        "inicio", em("10:00").toString()))
                .when().post("/agendamentos")
                .then().statusCode(201);

        // sem conta ainda: nenhuma senha entra
        given().contentType(ContentType.JSON)
                .body(Map.of("email", "bia@exemplo.dev", "senha", "qualquer-coisa"))
                .when().post("/auth/login")
                .then().statusCode(401);

        String token = given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Beatriz Souza", "email", "bia@exemplo.dev",
                        "senha", "senha-da-bia-123"))
                .when().post("/auth/registrar")
                .then().statusCode(201)
                .extract().path("token");

        // e o mesmo cliente: o horario que o Stewer marcou esta la
        given().header("Authorization", "Bearer " + token)
                .when().get("/agendamentos/meus")
                .then().statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].clienteId", equalTo(id));

        // a segunda tentativa de registrar com o e-mail ja e conta de verdade
        given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Outra Pessoa", "email", "bia@exemplo.dev",
                        "senha", "outra-senha-123"))
                .when().post("/auth/registrar")
                .then().statusCode(409)
                .body("codigo", equalTo("EMAIL_EM_USO"));

        login("bia@exemplo.dev", "senha-da-bia-123");
    }

    @Test
    @DisplayName("e-mail de quem ja tem conta e 409; sem nome ou telefone e 400")
    void conflitoEValidacao() {
        cadastrar(tokenStewer, "Joana de Novo", "63 90000-0000", "joana@exemplo.dev")
                .statusCode(409)
                .body("codigo", equalTo("EMAIL_EM_USO"));

        cadastrar(tokenStewer, "", "", null)
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"));
    }

    @Test
    @DisplayName("cliente nao cadastra cliente")
    void clienteNaoCadastra() {
        cadastrar(tokenCliente, "Alguem", "63 90000-0000", null).statusCode(403);
    }

    // ------------------------------------------------------------------

    private ValidatableResponse cadastrar(String token, String nome, String telefone, String email) {
        Map<String, Object> corpo = new HashMap<>();
        corpo.put("nome", nome);
        corpo.put("telefone", telefone);
        if (email != null) {
            corpo.put("email", email);
        }
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(corpo)
                .when().post("/clientes")
                .then();
    }

    private String login(String email, String senha) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", senha))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    private static OffsetDateTime em(String hora) {
        return DIA.atTime(LocalTime.parse(hora)).atZone(FUSO).toOffsetDateTime();
    }
}
