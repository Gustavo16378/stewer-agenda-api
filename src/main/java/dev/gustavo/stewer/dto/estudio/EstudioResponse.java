package dev.gustavo.stewer.dto.estudio;

import java.time.OffsetDateTime;
import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * O estudio como o cliente ve. Campos nulos: o app usa o conteudo embutido nele, entao
 * um estudio que ainda nao foi editado continua com endereco, mapa e fotos.
 */
@Schema(name = "Estudio")
public record EstudioResponse(
        String nomeLocal,
        String descricao,
        String endereco,
        String regiao,
        String horario,
        Double latitude,
        Double longitude,
        String linkMapa,
        String whatsapp,
        String instagram,
        String site,
        String frase,
        @Schema(description = "Retrato do Stewer; nulo usa o que vem no app") ImagemResponse retrato,
        @Schema(description = "Fotos do trabalho, na ordem") List<ImagemResponse> fotos,
        OffsetDateTime atualizadoEm) {
}
