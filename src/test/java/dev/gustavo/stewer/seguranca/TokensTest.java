package dev.gustavo.stewer.seguranca;

import static io.restassured.RestAssured.given;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.security.ClaimsStewer;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.jwt.build.Jwt;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seguranca do token (OWASP A07, falhas de autenticacao): quem nao tem a chave nao
 * consegue se passar por admin, e token vencido, de outro emissor ou adulterado nao entra.
 * Caixa preta: monta o token por fora e so olha o status.
 */
@QuarkusTest
@DisplayName("seguranca -- tokens forjados, adulterados e vencidos")
class TokensTest {

    private static final String OUTRO_SEGREDO = "um-segredo-que-nao-e-o-da-api-e-tem-48-bytes-ok!!";

    @Inject
    DadosDeTeste dados;

    @ConfigProperty(name = "stewer.jwt.secret")
    String segredo;

    @ConfigProperty(name = "stewer.jwt.issuer")
    String emissor;

    private Usuario cliente;
    private Tenant tenant;

    @BeforeEach
    void preparar() {
        dados.limpar();
        tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        cliente = dados.usuario(tenant.getId(), "Joana", "joana@x.dev", Role.CLIENTE);
    }

    private String token(String chave, String iss, String papel, Instant expira) {
        return Jwt.issuer(iss).subject(cliente.getId().toString()).upn(cliente.getEmail())
                .groups(Set.of(papel))
                .claim(ClaimsStewer.TENANT_ID, tenant.getId().toString())
                .claim(ClaimsStewer.ROLE, papel).claim(ClaimsStewer.NOME, cliente.getNome())
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(expira)
                .signWithSecret(chave);
    }

    private static int status(String path, String token) {
        return given().header("Authorization", "Bearer " + token).when().get(path).then().extract().statusCode();
    }

    @Test
    @DisplayName("token valido entra; o mesmo com o papel trocado para ADMIN no meio do caminho nao")
    void papelAdulterado() {
        String bom = token(segredo, emissor, "CLIENTE", Instant.now().plusSeconds(600));
        org.junit.jupiter.api.Assertions.assertEquals(200, status("/auth/eu", bom));

        String[] partes = bom.split("\\.");
        String corpo = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8)
                .replace("\"CLIENTE\"", "\"ADMIN\"");
        String adulterado = partes[0] + "." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(corpo.getBytes(StandardCharsets.UTF_8)) + "." + partes[2];
        org.junit.jupiter.api.Assertions.assertEquals(401, status("/usuarios/admins", adulterado));
    }

    @Test
    @DisplayName("assinado com outra chave, de outro emissor ou vencido (alem do minuto de folga do relogio): 401")
    void forjados() {
        Instant depois = Instant.now().plusSeconds(600);
        org.junit.jupiter.api.Assertions.assertEquals(401,
                status("/usuarios/admins", token(OUTRO_SEGREDO, emissor, "ADMIN", depois)));
        org.junit.jupiter.api.Assertions.assertEquals(401,
                status("/auth/eu", token(segredo, "https://outro-emissor", "CLIENTE", depois)));
        org.junit.jupiter.api.Assertions.assertEquals(401,
                status("/auth/eu", token(segredo, emissor, "CLIENTE", Instant.now().minusSeconds(300))));
    }

    @Test
    @DisplayName("alg none e lixo no lugar do token: 401")
    void algNoneELixo() {
        String cabecalho = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"alg\":\"none\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String corpo = Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"iss\":\"" + emissor + "\",\"sub\":\"" + cliente.getId() + "\",\"groups\":[\"ADMIN\"],"
                        + "\"role\":\"ADMIN\",\"exp\":" + (Instant.now().getEpochSecond() + 600) + "}")
                        .getBytes(StandardCharsets.UTF_8));
        org.junit.jupiter.api.Assertions.assertEquals(401, status("/usuarios/admins", cabecalho + "." + corpo + "."));
        for (String lixo : new String[] { "abc", "a.b.c", "Bearer", "' OR 1=1 --" }) {
            org.junit.jupiter.api.Assertions.assertEquals(401, status("/auth/eu", lixo), lixo);
        }
    }

    @Test
    @DisplayName("senha errada nao diz se o e-mail existe")
    void enumeracao() {
        String existe = given().contentType(ContentType.JSON)
                .body(Map.of("email", "joana@x.dev", "senha", "errada123")).when().post("/auth/login")
                .then().statusCode(401).extract().path("mensagem");
        String naoExiste = given().contentType(ContentType.JSON)
                .body(Map.of("email", "ninguem@x.dev", "senha", "errada123")).when().post("/auth/login")
                .then().statusCode(401).extract().path("mensagem");
        org.junit.jupiter.api.Assertions.assertEquals(existe, naoExiste);
    }
}
