package dev.gustavo.stewer.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
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
 * Trava uma regressao que os outros testes nao pegam.
 *
 * <p>O resto da suite grava a grade pelo Hibernate e le pelo Hibernate: se
 * houver conversao de fuso na coluna {@code time}, ela acontece nos dois
 * sentidos e o erro se cancela. O seed de dev, porem, grava por SQL cru -- e
 * ali o desvio aparece: com {@code quarkus.hibernate-orm.jdbc.timezone=UTC} e a
 * JVM em UTC-3, uma grade de 09:00 as 19:00 voltava como 06:00 as 16:00Z e
 * todos os horarios oferecidos ao cliente saiam errados.
 *
 * <p>Por isso aqui a insercao e feita em SQL nativo, igual a migration V2.
 */
@QuarkusTest
@DisplayName("Grade gravada por SQL cru mantem a hora de parede")
class DisponibilidadeSeedTimezoneTest {

    private static final LocalDate DIA = LocalDate.now(ZoneOffset.UTC)
            .plusMonths(1)
            .with(TemporalAdjusters.next(DayOfWeek.MONDAY));

    private static final int DIA_SEMANA = DIA.getDayOfWeek().getValue() % 7;

    @Inject
    DadosDeTeste dados;

    @Inject
    EntityManager em;

    private Tenant tenant;
    private Profissional profissional;
    private Servico servico;
    private String token;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");

        Usuario usuarioProfissional = dados.usuario(tenant.getId(), "Stewer Gabriel",
                "stewer@stewer.dev", Role.PROFISSIONAL);
        profissional = dados.profissional(tenant.getId(), usuarioProfissional.getId(), "Visagismo");
        servico = dados.servico(tenant.getId(), "Corte + Visagismo", 60, 18000L);

        dados.usuario(tenant.getId(), "Joana Ribeiro", "joana@exemplo.dev", Role.CLIENTE);
        token = given()
                .contentType(ContentType.JSON)
                .body(Map.of("email", "joana@exemplo.dev", "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200)
                .extract().path("token");
    }

    @Test
    @DisplayName("grade 09:00-12:00 inserida por SQL gera slots 09:00, 10:00 e 11:00 em UTC")
    void gradeInseridaPorSqlNaoSofreDeslocamento() {
        inserirGradeViaSql("09:00", "12:00");

        given().header("Authorization", "Bearer " + token)
                .queryParam("data", DIA.toString())
                .queryParam("servicoId", servico.getId().toString())
                .when().get("/profissionais/{id}/horarios-livres", profissional.getId())
                .then()
                .statusCode(200)
                .body("horarios", hasSize(3))
                .body("horarios.inicio", contains(
                        DIA + "T09:00:00Z", DIA + "T10:00:00Z", DIA + "T11:00:00Z"));
    }

    @Test
    @DisplayName("GET da disponibilidade devolve a mesma hora que esta na coluna")
    void gradeInseridaPorSqlApareceComAHoraCorreta() {
        inserirGradeViaSql("09:00", "19:00");

        given().header("Authorization", "Bearer " + token)
                .when().get("/profissionais/{id}/disponibilidade", profissional.getId())
                .then()
                .statusCode(200)
                .body("", hasSize(1))
                .body("[0].diaSemana", equalTo(DIA_SEMANA))
                .body("[0].horaInicio", org.hamcrest.Matchers.startsWith("09:00"))
                .body("[0].horaFim", org.hamcrest.Matchers.startsWith("19:00"));
    }

    /** Mesma forma de insercao usada pela migration V2 do seed de dev. */
    private void inserirGradeViaSql(String horaInicio, String horaFim) {
        QuarkusTransaction.requiringNew().run(() -> em.createNativeQuery("""
                        INSERT INTO disponibilidade
                            (id, tenant_id, profissional_id, dia_semana, hora_inicio, hora_fim)
                        VALUES (:id, :tenant, :prof, :dia, CAST(:inicio AS time), CAST(:fim AS time))
                        """)
                .setParameter("id", UUID.randomUUID())
                .setParameter("tenant", tenant.getId())
                .setParameter("prof", profissional.getId())
                .setParameter("dia", (short) DIA_SEMANA)
                .setParameter("inicio", horaInicio)
                .setParameter("fim", horaFim)
                .executeUpdate());
    }
}
