package dev.gustavo.stewer.dto.cliente;

import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * A imagem como data URL. Vai em JSON, e nao como arquivo, porque precisa do
 * token de quem pede: uma tag de imagem comum nao manda cabecalho de login.
 */
@Schema(name = "FotoConteudo")
public record FotoConteudoResponse(UUID id, @Schema(description = "data:image/jpeg;base64,...") String dataUrl) {
}
