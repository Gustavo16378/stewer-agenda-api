package dev.gustavo.stewer.dto.estudio;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

/** O que o admin edita sobre o estudio. Campo vazio volta ao conteudo padrao do app. */
@Schema(name = "EstudioRequest")
public record EstudioRequest(
        @Size(max = 80) @Schema(example = "Conciso") String nomeLocal,
        @Size(max = 120) @Schema(example = "Studio de imagem masculina") String descricao,
        @Size(max = 240) @Schema(example = "204 Sul, Alameda 10, QI 5, Sala 05, Palmas") String endereco,
        @Size(max = 80) @Schema(example = "Plano Diretor Sul, Palmas") String regiao,
        @Size(max = 120) @Schema(example = "Segunda a sábado, das 9h às 19h") String horario,
        @DecimalMin("-90") @DecimalMax("90") Double latitude,
        @DecimalMin("-180") @DecimalMax("180") Double longitude,
        @Size(max = 500) @Schema(description = "Link do Google Maps; se tiver coordenadas, elas preenchem o mapa")
        String linkMapa,
        @Size(max = 30) @Schema(example = "63985116783") String whatsapp,
        @Size(max = 60) @Schema(example = "stewergabriel") String instagram,
        @Size(max = 200) @Schema(example = "https://stewergabriel.com") String site,
        @Size(max = 240) @Schema(description = "Frase que aparece na tela de entrar") String frase) {
}
