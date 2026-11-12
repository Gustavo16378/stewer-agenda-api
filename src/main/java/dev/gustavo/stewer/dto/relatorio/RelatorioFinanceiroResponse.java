package dev.gustavo.stewer.dto.relatorio;

import java.time.LocalDate;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * O balanco de um periodo. Faturado e o que foi atendido (CONCLUIDO), pelo preco do dia
 * em que foi marcado; a receber e o que esta marcado e ainda nao teve baixa.
 */
@Schema(name = "RelatorioFinanceiro")
public record RelatorioFinanceiroResponse(
        LocalDate de,
        LocalDate ate,
        @Schema(description = "DIA, SEMANA ou MES") String agrupamento,
        Totais totais,
        @Schema(description = "O mesmo periodo imediatamente antes, para comparar") Totais anterior,
        List<Ponto> serie,
        List<Fatia> porServico,
        List<Fatia> porProfissional,
        @Schema(description = "Segunda (1) a domingo (7)") List<Fatia> porDiaDaSemana) {

    @Schema(name = "TotaisFinanceiros")
    public record Totais(
            long faturadoCentavos,
            long aReceberCentavos,
            long ticketMedioCentavos,
            int atendimentos,
            int agendados,
            int faltas,
            int cancelados,
            int clientesAtendidos,
            int clientesNovos,
            @Schema(description = "Faltas sobre faltas mais atendimentos, em %") int taxaDeFaltas) {
    }

    @Schema(name = "PontoFinanceiro")
    public record Ponto(LocalDate inicio, long faturadoCentavos, long aReceberCentavos, int atendimentos,
            int faltas) {
    }

    @Schema(name = "FatiaFinanceira")
    public record Fatia(String nome, int atendimentos, long faturadoCentavos) {
    }
}
