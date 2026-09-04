package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.util.DadosDeTeste;

import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prova que DELETE nunca apaga linha.
 *
 * <p>Os testes de cada recurso ja checam que o item some da API depois do
 * DELETE -- mas isso passaria igual se o service tivesse feito um delete
 * fisico. Aqui a verificacao e no banco: a linha continua la, com
 * {@code deleted_at} preenchido. E a regra que mais custa caro perder, entao
 * ela tem um teste que olha o disco, nao o JSON.
 */
@QuarkusTest
@DisplayName("Soft delete: a linha sobrevive com deleted_at preenchido")
class SoftDeleteTest {

    @Inject
    DadosDeTeste dados;

    @Inject
    EntityManager em;

    private Tenant tenant;
    private String tokenAdmin;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Ana Administradora", "ana@stewer.dev", Role.ADMIN);
        tokenAdmin = autenticar("ana@stewer.dev");
    }

    @Test
    @DisplayName("DELETE /servicos/{id} marca deleted_at e mantem a linha")
    void servicoSobreviveAoDelete() {
        Servico servico = dados.servico(tenant.getId(), "Barba", 30, 7000L);

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().delete("/servicos/{id}", servico.getId())
                .then().statusCode(204);

        assertNotNull(deletedAt("servico", servico.getId()),
                "a linha do servico deve continuar no banco, apenas marcada");
    }

    @Test
    @DisplayName("DELETE /profissionais/{id} marca deleted_at e mantem a linha")
    void profissionalSobreviveAoDelete() {
        Usuario usuario = dados.usuario(tenant.getId(), "Stewer Gabriel",
                "stewer@stewer.dev", Role.PROFISSIONAL);
        Profissional profissional =
                dados.profissional(tenant.getId(), usuario.getId(), "Visagismo");

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().delete("/profissionais/{id}", profissional.getId())
                .then().statusCode(204);

        assertNotNull(deletedAt("profissional", profissional.getId()),
                "a ficha do profissional deve continuar no banco, apenas marcada");
        assertNull(deletedAt("usuario", usuario.getId()),
                "apagar a ficha nao pode derrubar a conta do usuario");
    }

    @Test
    @DisplayName("DELETE de bloqueio marca deleted_at e mantem a linha")
    void bloqueioSobreviveAoDelete() {
        Usuario usuario = dados.usuario(tenant.getId(), "Stewer Gabriel",
                "stewer@stewer.dev", Role.PROFISSIONAL);
        Profissional profissional =
                dados.profissional(tenant.getId(), usuario.getId(), "Visagismo");

        OffsetDateTime inicio = OffsetDateTime.now(ZoneOffset.UTC).plusDays(3).withNano(0);
        UUID bloqueioId = dados.bloqueio(tenant.getId(), profissional.getId(),
                inicio, inicio.plusHours(2), "Folga").getId();

        given().header("Authorization", "Bearer " + tokenAdmin)
                .when().delete("/profissionais/{p}/bloqueios/{b}", profissional.getId(), bloqueioId)
                .then().statusCode(204);

        assertNotNull(deletedAt("bloqueio", bloqueioId),
                "a linha do bloqueio deve continuar no banco, apenas marcada");
    }

    /** Le a coluna crua, sem passar pelos filtros de soft delete do repositorio. */
    private OffsetDateTime deletedAt(String tabela, UUID id) {
        return QuarkusTransaction.requiringNew().call(() -> (OffsetDateTime) em
                .createNativeQuery("SELECT deleted_at FROM " + tabela + " WHERE id = :id",
                        OffsetDateTime.class)
                .setParameter("id", id)
                .getSingleResult());
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
