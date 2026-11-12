package dev.gustavo.stewer.seguranca;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.resource.AgendaProfissionalResource;
import dev.gustavo.stewer.resource.AgendamentoResource;
import dev.gustavo.stewer.resource.AuditoriaResource;
import dev.gustavo.stewer.resource.AuthResource;
import dev.gustavo.stewer.resource.ClienteResource;
import dev.gustavo.stewer.resource.EstudioResource;
import dev.gustavo.stewer.resource.HorariosLivresResource;
import dev.gustavo.stewer.resource.ImagemResource;
import dev.gustavo.stewer.resource.PingResource;
import dev.gustavo.stewer.resource.ProfissionalResource;
import dev.gustavo.stewer.resource.ServicoResource;
import dev.gustavo.stewer.resource.UsuarioResource;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.security.Authenticated;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Seguranca, caixa branca + preta: le as anotacoes de todos os endpoints (o que o codigo
 * promete) e confere de fora, por HTTP, que a API cumpre.
 *
 * <ul>
 * <li>todo endpoint que nao e publico responde 401 sem token;</li>
 * <li>todo endpoint fechado para um papel responde 403 para ele, antes de ler o corpo
 * ou procurar o registro (nao vaza nem se o id existe);</li>
 * <li>a lista de endpoints publicos e exatamente a esperada: um {@code @PermitAll} novo
 * sem passar por aqui quebra o teste.</li>
 * </ul>
 */
@QuarkusTest
@DisplayName("seguranca -- matriz de acesso de todos os endpoints")
class MatrizDeAcessoTest {

    private static final List<Class<?>> RECURSOS = List.of(AgendaProfissionalResource.class,
            AgendamentoResource.class, AuditoriaResource.class, AuthResource.class, ClienteResource.class,
            EstudioResource.class, HorariosLivresResource.class, ImagemResource.class, PingResource.class,
            ProfissionalResource.class, ServicoResource.class, UsuarioResource.class);

    /** Os unicos endpoints abertos sem login. */
    private static final Set<String> PUBLICOS = Set.of(
            "GET /ping", "POST /auth/login", "POST /auth/registrar", "POST /auth/senha/esqueci",
            "POST /auth/senha/redefinir", "GET /estudio", "GET /imagens/{id}");

    record Endpoint(String metodo, String caminho, Set<String> papeis, boolean publico) {
        String chave() {
            return metodo + " " + caminho;
        }

        String url() {
            return caminho.replaceAll("\\{posicao}", "0").replaceAll("\\{[^}]+}", UUID.randomUUID().toString());
        }
    }

    @Inject
    DadosDeTeste dados;

    private Map<Role, String> tokens;

    @BeforeEach
    void preparar() {
        dados.limpar();
        Tenant t = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        dados.usuario(t.getId(), "Cliente", "cliente@x.dev", Role.CLIENTE);
        dados.usuario(t.getId(), "Profissional", "prof@x.dev", Role.PROFISSIONAL);
        dados.usuario(t.getId(), "Admin", "admin@x.dev", Role.ADMIN);
        tokens = Map.of(Role.CLIENTE, login("cliente@x.dev"), Role.PROFISSIONAL, login("prof@x.dev"),
                Role.ADMIN, login("admin@x.dev"));
    }

    static List<Endpoint> endpoints() {
        List<Endpoint> lista = new ArrayList<>();
        for (Class<?> c : RECURSOS) {
            String base = c.getAnnotation(Path.class).value();
            for (Method m : c.getDeclaredMethods()) {
                String metodo = verbo(m);
                if (metodo == null) {
                    continue;
                }
                Path p = m.getAnnotation(Path.class);
                String caminho = (base + (p == null ? "" : "/" + p.value())).replaceAll("/+", "/");
                if (caminho.length() > 1 && caminho.endsWith("/")) {
                    caminho = caminho.substring(0, caminho.length() - 1);
                }
                RolesAllowed r = m.getAnnotation(RolesAllowed.class);
                if (r == null) {
                    r = c.getAnnotation(RolesAllowed.class);
                }
                boolean publico = m.isAnnotationPresent(PermitAll.class);
                Set<String> papeis = r != null ? Set.of(r.value())
                        : (m.isAnnotationPresent(Authenticated.class) || c.isAnnotationPresent(Authenticated.class))
                                ? Set.of("CLIENTE", "PROFISSIONAL", "ADMIN")
                                : Set.of();
                lista.add(new Endpoint(metodo, caminho, papeis, publico));
            }
        }
        return lista;
    }

    private static String verbo(Method m) {
        for (Class<? extends Annotation> a : List.of(GET.class, POST.class, PUT.class, PATCH.class, DELETE.class)) {
            if (m.isAnnotationPresent(a)) {
                return a.getSimpleName();
            }
        }
        return null;
    }

    @Test
    @DisplayName("todo endpoint declara quem pode usar, e os publicos sao so os esperados")
    void todoEndpointDeclaraAcesso() {
        List<Endpoint> todos = endpoints();
        assertTrue(todos.size() > 50, "esperava mais de 50 endpoints, achou " + todos.size());
        for (Endpoint e : todos) {
            assertTrue(e.publico() || !e.papeis().isEmpty(), e.chave() + " nao declara quem pode usar");
        }
        Set<String> publicos = Set.copyOf(todos.stream().filter(Endpoint::publico).map(Endpoint::chave).toList());
        assertEquals(PUBLICOS, publicos);
    }

    @Test
    @DisplayName("sem token, todo endpoint fechado responde 401")
    void semToken() {
        for (Endpoint e : endpoints()) {
            if (e.publico()) {
                continue;
            }
            int status = chamar(e, null);
            assertEquals(401, status, e.chave() + " sem token");
        }
    }

    @Test
    @DisplayName("papel sem permissao recebe 403 em todo endpoint fechado para ele")
    void papelErrado() {
        int checados = 0;
        for (Endpoint e : endpoints()) {
            if (e.publico()) {
                continue;
            }
            for (Role papel : Role.values()) {
                if (e.papeis().contains(papel.name())) {
                    continue;
                }
                assertEquals(403, chamar(e, tokens.get(papel)), e.chave() + " como " + papel);
                checados++;
            }
        }
        assertTrue(checados > 40, "poucas combinacoes checadas: " + checados);
    }

    private int chamar(Endpoint e, String token) {
        var req = given().contentType(ContentType.JSON).body("{}");
        if (token != null) {
            req = req.header("Authorization", "Bearer " + token);
        }
        return req.when().request(e.metodo(), e.url()).then().extract().statusCode();
    }

    private String login(String email) {
        return given().contentType(ContentType.JSON)
                .body(Map.of("email", email, "senha", DadosDeTeste.SENHA))
                .when().post("/auth/login")
                .then().statusCode(200).extract().path("token");
    }

}
