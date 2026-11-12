package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.email.LembreteDeHorario;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.MockMailbox;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Esqueci a senha, lembrete por e-mail, freio de login e exclusao de conta. */
@QuarkusTest
@DisplayName("senha por e-mail, lembrete, freio de login e exclusao de conta")
class SenhaEmailEContaTest {

    private static final Pattern SEIS_DIGITOS = Pattern.compile("(\\d{6})");

    @Inject
    DadosDeTeste dados;

    @Inject
    MockMailbox caixa;

    @Inject
    LembreteDeHorario lembrete;

    private Tenant tenant;
    private Usuario joana;
    private Profissional profissional;
    private Servico servico;

    @BeforeEach
    void preparar() {
        dados.limpar();
        caixa.clear();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        Usuario gabriel = dados.usuario(tenant.getId(), "Gabriel", "gabriel@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), gabriel.getId(), "Visagismo");
        servico = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 9000L);
    }

    // ------------------------------------------------------------------
    // Esqueci a senha
    // ------------------------------------------------------------------

    @Test
    @DisplayName("esqueci a senha manda codigo; com ele a senha troca e a sessao ja abre")
    void redefineComCodigo() {
        esqueci("joana@exemplo.dev").statusCode(204);
        String codigo = codigoDoUltimoEmail("joana@exemplo.dev");

        redefinir("joana@exemplo.dev", codigo, "senhaNova123")
                .statusCode(200)
                .body("token", org.hamcrest.Matchers.notNullValue())
                .body("usuario.email", equalTo("joana@exemplo.dev"));

        login("joana@exemplo.dev", "senhaNova123").statusCode(200);
        // O mesmo codigo nao vale duas vezes.
        redefinir("joana@exemplo.dev", codigo, "outraSenha123")
                .statusCode(422).body("codigo", equalTo("CODIGO_INVALIDO"));
    }

    @Test
    @DisplayName("e-mail sem conta responde igual (204) e nao manda nada")
    void naoRevelaQuemTemConta() {
        esqueci("ninguem@exemplo.dev").statusCode(204);
        assertTrue(caixa.getMailsSentTo("ninguem@exemplo.dev") == null
                || caixa.getMailsSentTo("ninguem@exemplo.dev").isEmpty());
    }

    @Test
    @DisplayName("codigo errado 5 vezes queima o codigo, mesmo acertando depois")
    void codigoQueimaDepoisDeCincoErros() {
        esqueci("joana@exemplo.dev").statusCode(204);
        String codigo = codigoDoUltimoEmail("joana@exemplo.dev");
        String errado = codigo.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            redefinir("joana@exemplo.dev", errado, "senhaNova123").statusCode(422);
        }
        redefinir("joana@exemplo.dev", codigo, "senhaNova123")
                .statusCode(422).body("codigo", equalTo("CODIGO_INVALIDO"));
    }

    @Test
    @DisplayName("pedir codigo de novo invalida o anterior")
    void codigoNovoInvalidaOAnterior() {
        esqueci("joana@exemplo.dev").statusCode(204);
        String primeiro = codigoDoUltimoEmail("joana@exemplo.dev");
        esqueci("joana@exemplo.dev").statusCode(204);
        String segundo = codigoDoUltimoEmail("joana@exemplo.dev");

        if (!primeiro.equals(segundo)) {
            redefinir("joana@exemplo.dev", primeiro, "senhaNova123").statusCode(422);
        }
        redefinir("joana@exemplo.dev", segundo, "senhaNova123").statusCode(200);
    }

    // ------------------------------------------------------------------
    // Freio de login
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cinco senhas erradas seguidas travam o login com 429, mesmo com a senha certa")
    void freioDeLogin() {
        for (int i = 0; i < 5; i++) {
            login("joana@exemplo.dev", "errada" + i).statusCode(401);
        }
        login("joana@exemplo.dev", DadosDeTeste.SENHA)
                .statusCode(429).body("codigo", equalTo("MUITAS_TENTATIVAS"));
        // Outra conta nao e afetada.
        login("gabriel@stewer.dev", DadosDeTeste.SENHA).statusCode(200);
    }

    // ------------------------------------------------------------------
    // Lembrete por e-mail
    // ------------------------------------------------------------------

    @Test
    @DisplayName("lembrete sai uma vez para horario nas proximas 24h e respeita quem desligou")
    void lembreteDeHorario() {
        OffsetDateTime amanha = OffsetDateTime.now(ZoneOffset.UTC).plusHours(20);
        dados.agendamento(tenant.getId(), joana.getId(), profissional.getId(), servico.getId(),
                amanha, amanha.plusHours(1), StatusAgendamento.AGENDADO);

        assertEquals(1, lembrete.enviarPendentes());
        List<Mail> enviados = caixa.getMailsSentTo("joana@exemplo.dev");
        assertNotNull(enviados);
        assertTrue(enviados.get(0).getSubject().startsWith("Lembrete: Corte + Visagismo"));
        assertEquals(0, lembrete.enviarPendentes(), "nao manda duas vezes");

        // Quem desligou o lembrete nao recebe.
        Usuario marcos = dados.usuario(tenant.getId(), "Marcos", "marcos@exemplo.dev", Role.CLIENTE);
        String tokenMarcos = login("marcos@exemplo.dev", DadosDeTeste.SENHA).extract().path("token");
        given().header("Authorization", "Bearer " + tokenMarcos)
                .contentType(ContentType.JSON)
                .body(Map.of("lembreteEmail", false))
                .when().put("/auth/eu/preferencias")
                .then().statusCode(200).body("lembreteEmail", equalTo(false));
        dados.agendamento(tenant.getId(), marcos.getId(), profissional.getId(), servico.getId(),
                amanha.plusHours(2), amanha.plusHours(3), StatusAgendamento.AGENDADO);
        assertEquals(0, lembrete.enviarPendentes());
    }

    // ------------------------------------------------------------------
    // Excluir conta
    // ------------------------------------------------------------------

    @Test
    @DisplayName("cliente exclui a conta: horarios futuros cancelados, login some e o e-mail fica livre")
    void clienteExcluiConta() {
        OffsetDateTime futuro = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3);
        dados.agendamento(tenant.getId(), joana.getId(), profissional.getId(), servico.getId(),
                futuro, futuro.plusHours(1), StatusAgendamento.AGENDADO);
        String token = login("joana@exemplo.dev", DadosDeTeste.SENHA).extract().path("token");

        excluir(token, "errada123").statusCode(422).body("codigo", equalTo("SENHA_ATUAL_INCORRETA"));
        excluir(token, DadosDeTeste.SENHA).statusCode(204);

        login("joana@exemplo.dev", DadosDeTeste.SENHA).statusCode(401);

        String tokenProf = login("gabriel@stewer.dev", DadosDeTeste.SENHA).extract().path("token");
        given().header("Authorization", "Bearer " + tokenProf)
                .queryParam("data", futuro.atZoneSameInstant(tenant.zoneId()).toLocalDate().toString())
                .when().get("/agendamentos/agenda")
                .then().statusCode(200)
                .body("[0].status", equalTo("CANCELADO"))
                .body("[0].clienteNome", equalTo("Conta excluída"));

        // O e-mail pode ser usado de novo num cadastro novo.
        given().contentType(ContentType.JSON)
                .body(Map.of("nome", "Joana de Novo", "email", "joana@exemplo.dev", "senha", "senha12345"))
                .when().post("/auth/registrar")
                .then().statusCode(201);
    }

    @Test
    @DisplayName("conta da equipe nao se exclui por aqui: 403 para o profissional")
    void equipeNaoExcluiPorAqui() {
        String tokenProf = login("gabriel@stewer.dev", DadosDeTeste.SENHA).extract().path("token");
        excluir(tokenProf, DadosDeTeste.SENHA).statusCode(403);
    }

    // ------------------------------------------------------------------

    private ValidatableResponse esqueci(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email))
                .when().post("/auth/senha/esqueci").then();
    }

    private ValidatableResponse redefinir(String email, String codigo, String nova) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "codigo", codigo, "novaSenha", nova))
                .when().post("/auth/senha/redefinir").then();
    }

    private ValidatableResponse login(String email, String senha) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", senha))
                .when().post("/auth/login").then();
    }

    private ValidatableResponse excluir(String token, String senha) {
        return given().header("Authorization", "Bearer " + token)
                .contentType(ContentType.JSON)
                .body(Map.of("senha", senha))
                .when().post("/auth/eu/excluir").then();
    }

    private String codigoDoUltimoEmail(String para) {
        List<Mail> mails = caixa.getMailsSentTo(para);
        assertNotNull(mails, "nenhum e-mail para " + para);
        Matcher m = SEIS_DIGITOS.matcher(mails.get(mails.size() - 1).getText());
        assertTrue(m.find(), "o e-mail deveria trazer um codigo de 6 digitos");
        return m.group(1);
    }
}
