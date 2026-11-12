package dev.gustavo.stewer.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.cliente.MetricasClienteResponse;

/** Os numeros do cliente, sem banco: o historico entra montado e o "agora" fixo. */
@DisplayName("metricas do cliente -- gasto, ritmo, faltas e preferidos")
class MetricasDoClienteTest {

    private static final OffsetDateTime AGORA = OffsetDateTime.of(2026, 11, 12, 15, 0, 0, 0, ZoneOffset.UTC);

    private final Servico corte = servico("Corte", 9000);
    private final Servico barba = servico("Barba", 5000);
    private final Profissional stewer = profissional("Stewer Gabriel");
    private final Profissional colega = profissional("Colega");

    @Test
    @DisplayName("sem historico, tudo zerado e nada inventado")
    void vazio() {
        MetricasClienteResponse m = MetricasDoCliente.calcular(List.of(), AGORA);
        assertEquals(0, m.visitas());
        assertEquals(0, m.totalGastoCentavos());
        assertEquals(0, m.ticketMedioCentavos());
        assertNull(m.intervaloMedioDias());
        assertNull(m.diasDesdeUltimaVisita());
        assertNull(m.servicoPreferido());
        assertNull(m.proximoHorario());
    }

    @Test
    @DisplayName("gasto usa o preco do dia; ticket e intervalo sao medias das concluidas")
    void gastoERitmo() {
        Agendamento a = ag(corte, stewer, AGORA.minusDays(70), StatusAgendamento.CONCLUIDO);
        a.setPrecoCentavos(8000L); // corte custava menos quando ele veio
        List<Agendamento> h = List.of(
                a,
                ag(corte, stewer, AGORA.minusDays(40), StatusAgendamento.CONCLUIDO),
                ag(barba, colega, AGORA.minusDays(10), StatusAgendamento.CONCLUIDO),
                ag(corte, stewer, AGORA.minusDays(5), StatusAgendamento.CANCELADO));

        MetricasClienteResponse m = MetricasDoCliente.calcular(h, AGORA);
        assertEquals(3, m.visitas());
        assertEquals(8000 + 9000 + 5000, m.totalGastoCentavos());
        assertEquals(7333, m.ticketMedioCentavos());
        assertEquals(30, m.intervaloMedioDias()); // 60 dias entre a 1a e a 3a, 2 intervalos
        assertEquals(10, m.diasDesdeUltimaVisita());
        assertEquals("Corte", m.servicoPreferido());
        assertEquals("Stewer Gabriel", m.profissionalPreferido());
        assertEquals(1, m.cancelamentos());
    }

    @Test
    @DisplayName("faltas, proximo horario e empate no preferido fica com o mais recente")
    void faltasEProximo() {
        OffsetDateTime proximo = AGORA.plusDays(3);
        List<Agendamento> h = List.of(
                ag(corte, stewer, AGORA.minusDays(30), StatusAgendamento.CONCLUIDO),
                ag(barba, colega, AGORA.minusDays(15), StatusAgendamento.CONCLUIDO),
                ag(corte, stewer, AGORA.minusDays(2), StatusAgendamento.NAO_COMPARECEU),
                ag(corte, stewer, AGORA.plusDays(20), StatusAgendamento.AGENDADO),
                ag(barba, stewer, proximo, StatusAgendamento.AGENDADO));

        MetricasClienteResponse m = MetricasDoCliente.calcular(h, AGORA);
        assertEquals(1, m.faltas());
        assertEquals(proximo, m.proximoHorario());
        assertEquals("Barba", m.servicoPreferido());
        assertEquals("Colega", m.profissionalPreferido());
    }

    @Test
    @DisplayName("cancelar com menos de 24h de antecedencia conta como em cima da hora")
    void emCimaDaHora() {
        Agendamento cedo = ag(corte, stewer, AGORA.plusDays(5), StatusAgendamento.CANCELADO);
        cedo.setUpdatedAt(AGORA.plusDays(1));
        Agendamento tarde = ag(corte, stewer, AGORA.plusDays(5), StatusAgendamento.CANCELADO);
        tarde.setUpdatedAt(AGORA.plusDays(5).minusHours(3));

        assertFalse(MetricasDoCliente.canceladoEmCimaDaHora(cedo));
        assertTrue(MetricasDoCliente.canceladoEmCimaDaHora(tarde));
        assertEquals(1, MetricasDoCliente.calcular(List.of(cedo, tarde), AGORA).cancelamentosEmCimaDaHora());
    }

    private static Servico servico(String nome, long preco) {
        Servico s = new Servico();
        s.setNome(nome);
        s.setPrecoCentavos(preco);
        s.setDuracaoMinutos(60);
        return s;
    }

    private static Profissional profissional(String nome) {
        Usuario u = new Usuario();
        u.setNome(nome);
        Profissional p = new Profissional();
        p.setUsuario(u);
        return p;
    }

    private static Agendamento ag(Servico s, Profissional p, OffsetDateTime inicio, StatusAgendamento st) {
        Agendamento a = new Agendamento();
        a.setServico(s);
        a.setProfissional(p);
        a.setInicio(inicio);
        a.setFim(inicio.plusHours(1));
        a.setStatus(st);
        a.setUpdatedAt(inicio.minusDays(3));
        return a;
    }
}
