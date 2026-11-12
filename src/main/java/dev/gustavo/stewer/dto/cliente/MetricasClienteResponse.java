package dev.gustavo.stewer.dto.cliente;

import java.time.OffsetDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Os numeros do cliente, calculados do historico a cada consulta. */
@Schema(name = "MetricasCliente")
public record MetricasClienteResponse(
        @Schema(description = "Atendimentos concluidos") int visitas,
        @Schema(description = "Soma do valor dos atendimentos concluidos, no preco do dia") long totalGastoCentavos,
        @Schema(description = "Media por atendimento concluido") long ticketMedioCentavos,
        @Schema(description = "De quantos em quantos dias ele volta; nulo com menos de 2 visitas")
        Integer intervaloMedioDias,
        @Schema(description = "Dias desde a ultima visita concluida; nulo se nunca veio")
        Integer diasDesdeUltimaVisita,
        @Schema(description = "Vezes que nao compareceu") int faltas,
        @Schema(description = "Horarios cancelados") int cancelamentos,
        @Schema(description = "Cancelados com menos de 24 horas de antecedencia")
        int cancelamentosEmCimaDaHora,
        @Schema(description = "Servico que ele mais faz") String servicoPreferido,
        @Schema(description = "Com quem ele mais atende") String profissionalPreferido,
        @Schema(description = "Proximo horario marcado; nulo se nao tem")
        OffsetDateTime proximoHorario) {
}
