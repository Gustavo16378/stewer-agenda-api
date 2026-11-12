package dev.gustavo.stewer.dto.estudio;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Foto nova do estudio ou de um servico. O app ja manda reduzida. */
@Schema(name = "ImagemRequest")
public record ImagemRequest(
        @Schema(description = "RETRATO (troca o atual) ou TRABALHO. Ignorado nas fotos de servico.")
        String tipo,
        @Size(max = 200) String legenda,
        @NotBlank @Schema(example = "image/jpeg") String tipoConteudo,
        @NotBlank @Size(max = 2_100_000, message = "foto grande demais; o limite e 1,5 MB")
        @Schema(description = "A imagem em base64 (JPEG, PNG ou WebP, ate 1,5 MB)")
        String conteudoBase64) {
}
