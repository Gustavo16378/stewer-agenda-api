package dev.gustavo.stewer.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.dto.relatorio.RelatorioFinanceiroResponse;
import dev.gustavo.stewer.dto.relatorio.RelatorioFinanceiroResponse.Fatia;
import dev.gustavo.stewer.dto.relatorio.RelatorioFinanceiroResponse.Ponto;
import dev.gustavo.stewer.dto.relatorio.RelatorioFinanceiroResponse.Totais;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.repository.AgendamentoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * O financeiro do estudio, calculado a partir da agenda: nao existe lancamento a parte.
 * Faturado e o que teve baixa (CONCLUIDO), pelo preco gravado no dia em que foi marcado;
 * a receber e o que esta marcado e ainda nao teve baixa. Os dias sao os do fuso do
 * estabelecimento.
 */
@ApplicationScoped
public class RelatorioService {

    public enum Agrupamento {
        DIA,
        SEMANA,
        MES
    }

    static final int DIAS_MAXIMOS = 400;

    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    HorariosLivresService horariosLivresService;

    @Transactional
    public RelatorioFinanceiroResponse financeiro(UUID tenantId, LocalDate de, LocalDate ate, String agrupar,
            UUID profissionalId) {
        if (ate.isBefore(de)) {
            throw new RegraNegocioException("PERIODO_INVALIDO", "A data final vem antes da inicial");
        }
        long dias = ChronoUnit.DAYS.between(de, ate) + 1;
        if (dias > DIAS_MAXIMOS) {
            throw new RegraNegocioException("PERIODO_LONGO", "O relatorio vai ate " + DIAS_MAXIMOS + " dias");
        }
        Agrupamento agrupamento = agrupamento(agrupar, dias);
        ZoneId fuso = horariosLivresService.fusoDo(tenantId);

        List<Agendamento> atual = buscar(tenantId, de, ate, fuso, profissionalId);
        LocalDate antesAte = de.minusDays(1);
        LocalDate antesDe = antesAte.minusDays(dias - 1);
        List<Agendamento> anterior = buscar(tenantId, antesDe, antesAte, fuso, profissionalId);

        Totais totais = totais(tenantId, atual, de.atStartOfDay(fuso).toOffsetDateTime());
        Totais totaisAntes = totais(tenantId, anterior, antesDe.atStartOfDay(fuso).toOffsetDateTime());

        return new RelatorioFinanceiroResponse(de, ate, agrupamento.name(), totais, totaisAntes,
                serie(atual, de, ate, agrupamento, fuso),
                fatias(atual, a -> a.getServico().getNome()),
                fatias(atual, a -> a.getProfissional().getUsuario().getNome()),
                porDiaDaSemana(atual, fuso));
    }

    private List<Agendamento> buscar(UUID tenantId, LocalDate de, LocalDate ate, ZoneId fuso, UUID prof) {
        OffsetDateTime inicio = de.atStartOfDay(fuso).toOffsetDateTime();
        OffsetDateTime fim = ate.plusDays(1).atStartOfDay(fuso).toOffsetDateTime();
        return agendamentoRepository.naJanela(tenantId, inicio, fim).stream()
                .filter(a -> prof == null || a.getProfissional().getId().equals(prof))
                .toList();
    }

    static Agrupamento agrupamento(String pedido, long dias) {
        if (pedido != null && !pedido.isBlank()) {
            try {
                return Agrupamento.valueOf(pedido.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new RegraNegocioException("AGRUPAMENTO_INVALIDO", "Agrupe por DIA, SEMANA ou MES");
            }
        }
        return dias <= 31 ? Agrupamento.DIA : dias <= 120 ? Agrupamento.SEMANA : Agrupamento.MES;
    }

    private Totais totais(UUID tenantId, List<Agendamento> lista, OffsetDateTime inicio) {
        long faturado = 0;
        long aReceber = 0;
        int atendimentos = 0;
        int agendados = 0;
        int faltas = 0;
        int cancelados = 0;
        Set<UUID> atendidos = new HashSet<>();
        for (Agendamento a : lista) {
            switch (a.getStatus()) {
                case CONCLUIDO -> {
                    faturado += a.precoEfetivo();
                    atendimentos++;
                    atendidos.add(a.getCliente().getId());
                }
                case AGENDADO -> {
                    aReceber += a.precoEfetivo();
                    agendados++;
                }
                case NAO_COMPARECEU -> faltas++;
                case CANCELADO -> cancelados++;
            }
        }
        // novo e quem nunca tinha sido atendido antes do comeco do periodo
        Set<UUID> jaVieram = atendidos.isEmpty() ? Set.of()
                : agendamentoRepository.clientesComVisitaAntes(tenantId, atendidos, inicio);
        int novos = (int) atendidos.stream().filter(id -> !jaVieram.contains(id)).count();
        int taxa = atendimentos + faltas == 0 ? 0 : Math.round(100f * faltas / (atendimentos + faltas));
        return new Totais(faturado, aReceber, atendimentos == 0 ? 0 : faturado / atendimentos, atendimentos,
                agendados, faltas, cancelados, atendidos.size(), novos, taxa);
    }

    static LocalDate inicioDoGrupo(LocalDate dia, Agrupamento g) {
        return switch (g) {
            case DIA -> dia;
            case SEMANA -> dia.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MES -> dia.withDayOfMonth(1);
        };
    }

    private List<Ponto> serie(List<Agendamento> lista, LocalDate de, LocalDate ate, Agrupamento g, ZoneId fuso) {
        // todos os grupos do periodo, mesmo os vazios: o grafico mostra o buraco
        Map<LocalDate, long[]> grupos = new LinkedHashMap<>();
        for (LocalDate d = inicioDoGrupo(de, g); !d.isAfter(ate); d = switch (g) {
            case DIA -> d.plusDays(1);
            case SEMANA -> d.plusWeeks(1);
            case MES -> d.plusMonths(1);
        }) {
            grupos.put(d, new long[4]);
        }
        for (Agendamento a : lista) {
            long[] v = grupos.get(inicioDoGrupo(a.getInicio().atZoneSameInstant(fuso).toLocalDate(), g));
            if (v == null) {
                continue;
            }
            if (a.getStatus() == StatusAgendamento.CONCLUIDO) {
                v[0] += a.precoEfetivo();
                v[2]++;
            } else if (a.getStatus() == StatusAgendamento.AGENDADO) {
                v[1] += a.precoEfetivo();
            } else if (a.getStatus() == StatusAgendamento.NAO_COMPARECEU) {
                v[3]++;
            }
        }
        List<Ponto> serie = new ArrayList<>();
        grupos.forEach((d, v) -> serie.add(new Ponto(d, v[0], v[1], (int) v[2], (int) v[3])));
        return serie;
    }

    private static List<Fatia> fatias(List<Agendamento> lista, Function<Agendamento, String> chave) {
        Map<String, long[]> por = new LinkedHashMap<>();
        for (Agendamento a : lista) {
            if (a.getStatus() != StatusAgendamento.CONCLUIDO) {
                continue;
            }
            long[] v = por.computeIfAbsent(chave.apply(a), k -> new long[2]);
            v[0]++;
            v[1] += a.precoEfetivo();
        }
        return por.entrySet().stream()
                .map(e -> new Fatia(e.getKey(), (int) e.getValue()[0], e.getValue()[1]))
                .sorted(Comparator.comparingLong(Fatia::faturadoCentavos).reversed())
                .toList();
    }

    private static List<Fatia> porDiaDaSemana(List<Agendamento> lista, ZoneId fuso) {
        long[][] v = new long[8][2];
        for (Agendamento a : lista) {
            if (a.getStatus() != StatusAgendamento.CONCLUIDO) {
                continue;
            }
            int d = a.getInicio().atZoneSameInstant(fuso).getDayOfWeek().getValue();
            v[d][0]++;
            v[d][1] += a.precoEfetivo();
        }
        List<Fatia> dias = new ArrayList<>();
        for (int d = 1; d <= 7; d++) {
            dias.add(new Fatia(String.valueOf(d), (int) v[d][0], v[d][1]));
        }
        return dias;
    }
}
