package dev.gustavo.stewer.config;

import static io.restassured.RestAssured.given;

import java.util.Map;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O dono perdeu a senha e o e-mail de recuperacao ainda nao esta configurado: as
 * variaveis do bootstrap com REDEFINIR_SENHA trocam a senha dele na subida.
 */
@QuarkusTest
@TestProfile(RecuperarAcessoDoDonoTest.Recuperando.class)
@DisplayName("bootstrap -- recuperar o acesso do dono")
class RecuperarAcessoDoDonoTest {

    public static class Recuperando implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "stewer.bootstrap.habilitado", "true",
                    "stewer.bootstrap.catalogo-inicial", "false",
                    "stewer.bootstrap.admin.email", "dono@stewer.dev",
                    "stewer.bootstrap.admin.senha", "senha-nova-123",
                    "stewer.bootstrap.admin.redefinir-senha", "true",
                    "stewer.bootstrap.admin.profissional", "false");
        }
    }

    @Inject
    BootstrapEstabelecimento bootstrap;

    @Inject
    DadosDeTeste dados;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(tenant.getId(), "Stewer Gabriel", "dono@stewer.dev", Role.ADMIN);
    }

    @Test
    @DisplayName("admin existente passa a entrar com a senha nova; a antiga deixa de valer")
    void redefine() {
        login("dono@stewer.dev", DadosDeTeste.SENHA, 200);
        bootstrap.executar();
        login("dono@stewer.dev", "senha-nova-123", 200);
        login("dono@stewer.dev", DadosDeTeste.SENHA, 401);
    }

    private void login(String email, String senha, int status) {
        given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", senha))
                .when().post("/auth/login")
                .then().statusCode(status);
    }
}
