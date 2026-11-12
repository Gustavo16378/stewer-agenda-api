package dev.gustavo.stewer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.repository.DisponibilidadeRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.ServicoRepository;
import dev.gustavo.stewer.repository.TenantRepository;
import dev.gustavo.stewer.util.DadosDeTeste;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Primeira subida em producao: o dono nasce admin E profissional, com grade,
 * para que o primeiro cliente ja consiga agendar sem ninguem mexer no banco.
 */
@QuarkusTest
@TestProfile(BootstrapDoDonoTest.ComDono.class)
@DisplayName("bootstrap -- o dono do estudio nasce atendendo")
class BootstrapDoDonoTest {

    public static class ComDono implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "stewer.bootstrap.habilitado", "true",
                    "stewer.bootstrap.admin.email", "stewer@stewer.dev",
                    "stewer.bootstrap.admin.senha", "senha-forte-123",
                    "stewer.bootstrap.admin.nome", "Stewer Gabriel",
                    "stewer.bootstrap.admin.especialidade", "Visagismo");
        }
    }

    @Inject
    BootstrapEstabelecimento bootstrap;

    @Inject
    DadosDeTeste dados;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    DisponibilidadeRepository disponibilidadeRepository;

    @Inject
    ServicoRepository servicoRepository;

    @BeforeEach
    void preparar() {
        dados.limpar();
    }

    /** O que importa de cada ficha, lido dentro de uma transacao (usuario e lazy). */
    record Ficha(String nome, String email, Role role, String especialidade, long faixas) {
    }

    @Test
    @DisplayName("banco vazio: cria admin com ficha de profissional e grade de segunda a sabado")
    void criaDonoAtendendo() {
        bootstrap.executar();

        List<Ficha> fichas = fichas();
        assertEquals(1, fichas.size());
        Ficha ficha = fichas.get(0);
        assertEquals("Stewer Gabriel", ficha.nome());
        assertEquals(Role.ADMIN, ficha.role());
        assertEquals("Visagismo", ficha.especialidade());
        assertEquals(6, ficha.faixas());
    }

    @Test
    @DisplayName("rodar de novo nao duplica a ficha nem a grade")
    void idempotente() {
        bootstrap.executar();
        bootstrap.executar();

        List<Ficha> fichas = fichas();
        assertEquals(1, fichas.size());
        assertEquals(6, fichas.get(0).faixas());
    }

    @Test
    @DisplayName("estudio que ja tem profissional nao ganha ficha nova para o admin")
    void naoMexeEmEstudioComEquipe() {
        Tenant tenant = dados.tenant("Stewer Gabriel", "stewer-gabriel");
        Usuario outro = dados.usuario(tenant.getId(), "Gabriel", "gabriel@stewer.dev", Role.PROFISSIONAL);
        dados.profissional(tenant.getId(), outro.getId(), "Corte");

        bootstrap.executar();

        List<Ficha> fichas = fichas();
        assertEquals(1, fichas.size());
        assertTrue(fichas.get(0).email().startsWith("gabriel"));
    }

    private List<Ficha> fichas() {
        return QuarkusTransaction.requiringNew().call(() -> {
            Tenant tenant = tenantRepository.porSlug("stewer-gabriel").orElseThrow();
            return profissionalRepository.listar(tenant.getId(), false).stream()
                    .map((Profissional f) -> new Ficha(f.getUsuario().getNome(),
                            f.getUsuario().getEmail(), f.getUsuario().getRole(),
                            f.getEspecialidade(),
                            disponibilidadeRepository.count("profissional.id", f.getId())))
                    .toList();
        });
    }

    @Test
    @DisplayName("estudio novo nasce com o catalogo do Stewer; quem ja teve servico nao ganha de novo")
    void catalogoInicial() {
        bootstrap.executar();
        List<String> nomes = QuarkusTransaction.requiringNew().call(() -> servicoRepository.listAll().stream()
                .sorted(java.util.Comparator.comparingLong(Servico::getPrecoCentavos))
                .map(s -> s.getNome() + " " + s.getDuracaoMinutos() + " " + s.getPrecoCentavos())
                .toList());
        assertEquals(List.of("Manutenção de barba 30 7000", "Corte (manutenção) 60 9000",
                "Consultoria de visagismo 90 25000"), nomes);

        // apagou tudo de proposito: reiniciar nao traz o catalogo de volta
        QuarkusTransaction.requiringNew().run(() -> servicoRepository.listAll()
                .forEach(servicoRepository::softDelete));
        bootstrap.executar();
        assertEquals(3L, (long) QuarkusTransaction.requiringNew().call(() -> servicoRepository.count()));
        assertEquals(0L, (long) QuarkusTransaction.requiringNew()
                .call(() -> servicoRepository.count("deletedAt is null")));
    }
}
