package dev.gustavo.stewer.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.dto.cliente.MetricasClienteResponse;

/**
 * Os numeros de um cliente, tirados do historico dele. Funcao pura (sem banco,
 * sem relogio escondido): o "agora" entra como parametro, para os testes
 * fixarem o tempo.
 */
public final class MetricasDoCliente {

    /** Cancelar com menos que isto de antecedencia conta como "em cima da hora". */
    static final Duration EM_CIMA_DA_HORA = Duration.ofHours(24);

    private MetricasDoCliente() {
    }

    public static MetricasClienteResponse calcular(List<Agendamento> historico, OffsetDateTime agora) {
        List<Agendamento> concluidos = historico.stream()
                .filter(a -> a.getStatus() == StatusAgendamento.CONCLUIDO)
                .sorted(Comparator.comparing(Agendamento::getInicio))
                .toList();

        int visitas = concluidos.size();
        long total = concluidos.stream().mapToLong(Agendamento::precoEfetivo).sum();
        long ticket = visitas == 0 ? 0 : Math.round((double) total / visitas);

        Integer intervalo = null;
        if (visitas >= 2) {
            long dias = Duration.between(concluidos.get(0).getInicio(),
                    concluidos.get(visitas - 1).getInicio()).toDays();
            intervalo = (int) Math.round((double) dias / (visitas - 1));
        }

        Integer desdeUltima = visitas == 0 ? null
                : (int) Math.max(0, Duration.between(concluidos.get(visitas - 1).getInicio(), agora).toDays());

        int faltas = contar(historico, StatusAgendamento.NAO_COMPARECEU);
        int cancelamentos = contar(historico, StatusAgendamento.CANCELADO);
        int emCimaDaHora = (int) historico.stream()
                .filter(a -> a.getStatus() == StatusAgendamento.CANCELADO)
                .filter(MetricasDoCliente::canceladoEmCimaDaHora)
                .count();

        OffsetDateTime proximo = historico.stream()
                .filter(a -> a.getStatus() == StatusAgendamento.AGENDADO && a.getInicio().isAfter(agora))
                .map(Agendamento::getInicio)
                .min(Comparator.naturalOrder())
                .orElse(null);

        return new MetricasClienteResponse(visitas, total, ticket, intervalo, desdeUltima, faltas,
                cancelamentos, emCimaDaHora,
                maisFrequente(concluidos, a -> a.getServico().getNome()),
                maisFrequente(concluidos, a -> a.getProfissional().getUsuario().getNome()),
                proximo);
    }

    /**
     * O cancelamento e o ultimo update de um agendamento cancelado (status
     * final nao muda mais), entao {@code updatedAt} marca quando cancelaram.
     */
    static boolean canceladoEmCimaDaHora(Agendamento a) {
        OffsetDateTime quando = a.getUpdatedAt();
        if (quando == null || quando.isAfter(a.getInicio())) {
            return false;
        }
        return Duration.between(quando, a.getInicio()).compareTo(EM_CIMA_DA_HORA) < 0;
    }

    private static int contar(List<Agendamento> historico, StatusAgendamento status) {
        return (int) historico.stream().filter(a -> a.getStatus() == status).count();
    }

    /** O valor mais comum; empate fica com o mais recente. */
    private static String maisFrequente(List<Agendamento> concluidos, Function<Agendamento, String> chave) {
        if (concluidos.isEmpty()) {
            return null;
        }
        Map<String, Long> contagem = concluidos.stream()
                .map(chave)
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        long max = contagem.values().stream().mapToLong(Long::longValue).max().orElse(0);
        for (int i = concluidos.size() - 1; i >= 0; i--) {
            String v = chave.apply(concluidos.get(i));
            if (v != null && contagem.getOrDefault(v, 0L) == max) {
                return v;
            }
        }
        return null;
    }
}
