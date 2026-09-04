package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import dev.gustavo.stewer.domain.Agendamento;
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

@QuarkusTest
@DisplayName("/clientes -- base de clientes e anotacoes de visagismo")
class ClienteResourceTest {

    @Inject
    DadosDeTeste dados;

    private Tenant tenant;
    private Usuario usuarioProfissional;
    private Profissional profissional;
    private Usuario joana;
    private Usuario marcos;
    private Servico servico;

    private String tokenProfissional;
    private String tokenAdmin;
    private String tokenCliente;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        usuarioProfissional = dados.usuario(tenant.getId(), "Gabriel Stewer",
                "gabriel@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), usuarioProfissional.getId(), "Visagismo");
        dados.usuario(tenant.getId(), "Ana Administradora", "ana@stewer.dev", Role.ADMIN);

        joana = dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        marcos = dados.usuario(tenant.getId(), "Marcos Lima", "marcos@outrodominio.dev", Role.CLIENTE);

        servico = dados.servico(tenant.getId(), "Corte Masculino", 45, 9000L);

        tokenProfissional = autenticar("gabriel@stewer.dev");
        tokenAdmin = autenticar("ana@stewer.dev");
        tokenCliente = autenticar("joana@exemplo.dev");
    }

    @Test
    @DisplayName("profissional lista apenas os usuarios com papel CLIENTE, em ordem alfabetica")
    void listaSomenteClientes() {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .when().get("/clientes")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].nome", equalTo("Joana Ribeiro"))
                .body("[0].email", equalTo("joana@exemplo.dev"))
                .body("[0].id", equalTo(joana.getId().toString()))
                .body("[1].nome", equalTo("Marcos Lima"));
    }

    @Test
    @DisplayName("PROFISSIONAL sem ficha cadastrada nao consegue anotar")
    void profissionalSemFichaNaoAnota() {
        // Ter a role PROFISSIONAL nao basta: a anotacao e assinada pela ficha,
        // e sem ficha nao existe autor a registrar.
        dados.usuario(tenant.getId(), "Bruno Sem Ficha", "bruno@stewer.dev", Role.PROFISSIONAL);
        String tokenSemFicha = autenticar("bruno@stewer.dev");

        given()
                .header("Authorization", "Bearer " + tokenSemFicha)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Rosto quadrado, evitar franja reta."))
                .when().post("/clientes/{id}/anotacoes", joana.getId())
                .then()
                .statusCode(422)
                .body("codigo", equalTo("SEM_FICHA_PROFISSIONAL"));
    }

    @Test
    @DisplayName("cliente de outro tenant nao aparece na listagem")
    void naoVazaClienteDeOutroTenant() {
        // O isolamento por tenant e a regra mais cara de perder: com um tenant so
        // no cenario, qualquer query sem filtro passaria despercebida.
        Tenant outro = dados.tenant("Outro Estudio", "outro-estudio");
        dados.usuario(outro.getId(), "Zelia Forasteira", "zelia@outro.dev", Role.CLIENTE);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .when().get("/clientes")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("nome", org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("Zelia Forasteira")));
    }

    @Test
    @DisplayName("cliente de outro tenant devolve 404 no detalhe")
    void detalheDeClienteDeOutroTenantDa404() {
        Tenant outro = dados.tenant("Outro Estudio", "outro-estudio");
        Usuario forasteira = dados.usuario(outro.getId(), "Zelia Forasteira",
                "zelia@outro.dev", Role.CLIENTE);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .when().get("/clientes/{id}", forasteira.getId())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("busca casa trecho do nome")
    void buscaPorTrechoDoNome() {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .queryParam("busca", "ribeir")
                .when().get("/clientes")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].nome", equalTo("Joana Ribeiro"));
    }

    @Test
    @DisplayName("busca casa trecho do e-mail")
    void buscaPorTrechoDoEmail() {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .queryParam("busca", "outrodominio")
                .when().get("/clientes")
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].email", equalTo("marcos@outrodominio.dev"));
    }

    @Test
    @DisplayName("cliente nao enxerga a base de clientes: 403")
    void clienteNaoListaClientes() {
        given()
                .header("Authorization", "Bearer " + tokenCliente)
                .when().get("/clientes")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("listagem sem token devolve 401")
    void listagemSemTokenDevolve401() {
        given().when().get("/clientes").then().statusCode(401);
    }

    @Test
    @DisplayName("detalhe traz o historico de agendamentos, do mais recente para o mais antigo")
    void detalheTrazHistorico() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        dados.agendamento(tenant.getId(), joana.getId(), profissional.getId(), servico.getId(),
                agora.minusDays(30), agora.minusDays(30).plusMinutes(45), StatusAgendamento.CONCLUIDO);
        dados.agendamento(tenant.getId(), joana.getId(), profissional.getId(), servico.getId(),
                agora.plusDays(3), agora.plusDays(3).plusMinutes(45), StatusAgendamento.AGENDADO);
        // Agendamento de outro cliente para garantir que o historico e filtrado.
        dados.agendamento(tenant.getId(), marcos.getId(), profissional.getId(), servico.getId(),
                agora.plusDays(4), agora.plusDays(4).plusMinutes(45), StatusAgendamento.AGENDADO);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .when().get("/clientes/" + joana.getId())
                .then()
                .statusCode(200)
                .body("id", equalTo(joana.getId().toString()))
                .body("tenantId", equalTo(tenant.getId().toString()))
                .body("nome", equalTo("Joana Ribeiro"))
                .body("agendamentos.size()", equalTo(2))
                .body("agendamentos[0].status", equalTo("AGENDADO"))
                .body("agendamentos[0].servicoNome", equalTo("Corte Masculino"))
                .body("agendamentos[0].profissionalNome", equalTo("Gabriel Stewer"))
                .body("agendamentos[1].status", equalTo("CONCLUIDO"));
    }

    @Test
    @DisplayName("detalhe de um id que nao e cliente devolve 404")
    void detalheDeQuemNaoEhClienteDevolve404() {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .when().get("/clientes/" + usuarioProfissional.getId())
                .then()
                .statusCode(404)
                .body("codigo", equalTo("RECURSO_NAO_ENCONTRADO"));
    }

    @Test
    @DisplayName("profissional cria anotacao de visagismo com 201 e Location")
    void criaAnotacao() {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Rosto oval, evitar franja curta"))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then()
                .statusCode(201)
                .header("Location", notNullValue())
                .body("id", notNullValue())
                .body("clienteId", equalTo(joana.getId().toString()))
                .body("profissionalId", equalTo(profissional.getId().toString()))
                .body("profissionalNome", equalTo("Gabriel Stewer"))
                .body("texto", equalTo("Rosto oval, evitar franja curta"))
                .body("createdAt", notNullValue());
    }

    @Test
    @DisplayName("admin nao assina anotacao de visagismo: 403")
    void adminNaoCriaAnotacao() {
        given()
                .header("Authorization", "Bearer " + tokenAdmin)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Anotacao que o admin nao deveria conseguir criar"))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then().statusCode(403);
    }

    @Test
    @DisplayName("anotacao amarrada a agendamento de outro cliente devolve 422")
    void anotacaoComAgendamentoDeOutroCliente() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        Agendamento doMarcos = dados.agendamento(tenant.getId(), marcos.getId(),
                profissional.getId(), servico.getId(),
                agora.plusDays(1), agora.plusDays(1).plusMinutes(45), StatusAgendamento.AGENDADO);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Tentando colar no historico errado",
                        "agendamentoId", doMarcos.getId().toString()))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then()
                .statusCode(422)
                .body("codigo", equalTo("AGENDAMENTO_INVALIDO"));
    }

    @Test
    @DisplayName("anotacao valida amarrada ao agendamento do proprio cliente e aceita")
    void anotacaoComAgendamentoDoProprioCliente() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC).withNano(0);
        Agendamento daJoana = dados.agendamento(tenant.getId(), joana.getId(),
                profissional.getId(), servico.getId(),
                agora.minusDays(2), agora.minusDays(2).plusMinutes(45), StatusAgendamento.CONCLUIDO);

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "Repetir o degrade da ultima visita",
                        "agendamentoId", daJoana.getId().toString()))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then()
                .statusCode(201)
                .body("agendamentoId", equalTo(daJoana.getId().toString()));
    }

    @Test
    @DisplayName("listagem de anotacoes vem da mais nova para a mais antiga")
    void anotacoesVemDaMaisNovaParaAMaisAntiga() {
        criarAnotacao("Primeira visita: cabelo muito fino");
        criarAnotacao("Segunda visita: aceitou bem o degrade");

        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .when().get("/clientes/" + joana.getId() + "/anotacoes")
                .then()
                .statusCode(200)
                .body("size()", equalTo(2))
                .body("[0].texto", equalTo("Segunda visita: aceitou bem o degrade"))
                .body("[1].texto", equalTo("Primeira visita: cabelo muito fino"));
    }

    @Test
    @DisplayName("anotacao sem texto devolve 400 de validacao")
    void anotacaoSemTextoDevolve400() {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", "   "))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then()
                .statusCode(400)
                .body("codigo", equalTo("VALIDACAO"));
    }

    private void criarAnotacao(String texto) {
        given()
                .header("Authorization", "Bearer " + tokenProfissional)
                .contentType(ContentType.JSON)
                .body(Map.of("texto", texto))
                .when().post("/clientes/" + joana.getId() + "/anotacoes")
                .then().statusCode(201);
    }

    private String autenticar(String email) {
        return given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }
}
