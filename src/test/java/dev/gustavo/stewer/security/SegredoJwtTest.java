package dev.gustavo.stewer.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * O segredo HMAC nao pode ter valor padrao valido fora de dev e de teste.
 *
 * <p>Com um default sem prefixo de profile, um deploy que esquecesse a variavel
 * {@code JWT_SECRET} subia assinando tokens com uma string que esta no
 * repositorio -- e o repositorio e publico. Qualquer um forjaria um token com
 * {@code groups:["ADMIN"]} e o {@code tenantId} do estudio.
 *
 * <p>Teste de arquivo, nao de runtime, de proposito: o que precisa ser travado
 * e a CONFIGURACAO. Um teste que so exercitasse a classe deixaria passar alguem
 * reintroduzindo o default no {@code application.properties}.
 */
@DisplayName("Segredo JWT: sem valor padrao em producao")
class SegredoJwtTest {

    private static final Path RAIZ = Path.of("src", "main", "resources");

    /**
     * O segredo que vazou junto com o codigo quando o repositorio foi publicado.
     * Nao pode voltar a aparecer em lugar nenhum -- nem como default de dev.
     */
    private static final String SEGREDO_COMPROMETIDO =
            "dev-secret-stewer-agenda-api-nao-use-isto-em-producao";

    @Test
    @DisplayName("application.properties nao define stewer.jwt.secret com default fora de dev/test")
    void semDefaultForaDeDevETest() throws IOException {
        List<String> definicoes = linhas(RAIZ.resolve("application.properties")).stream()
                .map(String::trim)
                .filter(l -> !l.startsWith("#"))
                .filter(l -> l.replace(" ", "").contains("stewer.jwt.secret="))
                .toList();

        assertFalse(definicoes.isEmpty(),
                "a propriedade stewer.jwt.secret precisa continuar existindo");

        for (String linha : definicoes) {
            boolean deDevOuTeste = linha.startsWith("%dev.") || linha.startsWith("%test.");
            boolean temDefaultUsavel = temDefaultNaoVazio(linha);
            assertFalse(temDefaultUsavel && !deDevOuTeste,
                    "esta linha da um segredo utilizavel a qualquer profile, inclusive prod: "
                            + linha);
        }
    }

    @Test
    @DisplayName("o segredo comprometido nao aparece em nenhum recurso do main")
    void segredoComprometidoNaoAparece() throws IOException {
        try (Stream<Path> arquivos = Files.walk(RAIZ)) {
            List<Path> comVazamento = arquivos
                    .filter(Files::isRegularFile)
                    .filter(p -> conteudo(p).contains(SEGREDO_COMPROMETIDO))
                    .toList();
            assertTrue(comVazamento.isEmpty(),
                    "o segredo vazado continua no repositorio: " + comVazamento);
        }
    }

    @Test
    @DisplayName(".env.example nao traz segredo utilizavel, so instrucao de como gerar")
    void envExampleNaoTrazSegredoReal() throws IOException {
        Path env = Path.of(".env.example");
        String texto = conteudo(env);

        assertFalse(texto.contains(SEGREDO_COMPROMETIDO),
                ".env.example ainda repete o segredo comprometido");
        assertTrue(texto.contains("openssl rand"),
                ".env.example precisa ensinar a gerar o segredo de producao");
    }

    // ------------------------------------------------------------------

    /** {@code ${JWT_SECRET:algo}} tem default usavel; {@code ${JWT_SECRET:}} nao. */
    private static boolean temDefaultNaoVazio(String linha) {
        int abre = linha.indexOf("${");
        if (abre < 0) {
            // valor literal direto, sem variavel de ambiente: e um default usavel
            return !linha.substring(linha.indexOf('=') + 1).isBlank();
        }
        int doisPontos = linha.indexOf(':', abre);
        int fecha = linha.indexOf('}', abre);
        if (doisPontos < 0 || doisPontos > fecha) {
            return false; // ${JWT_SECRET} sem default
        }
        return !linha.substring(doisPontos + 1, fecha).isBlank();
    }

    private static List<String> linhas(Path arquivo) throws IOException {
        return Files.readAllLines(arquivo, StandardCharsets.UTF_8);
    }

    private static String conteudo(Path arquivo) {
        try {
            return Files.readString(arquivo, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
