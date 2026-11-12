package dev.gustavo.stewer.dto.usuario;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Foto de perfil, ja reduzida pelo app (ate 600 KB de imagem). */
@Schema(name = "FotoPerfilRequest")
public record FotoPerfilRequest(
        @NotBlank(message = "o tipo da imagem e obrigatorio")
        @Schema(examples = "image/jpeg")
        String tipoConteudo,
        @NotBlank(message = "a imagem e obrigatoria")
        @Size(max = 900_000, message = "imagem grande demais; reduza antes de enviar")
        String conteudoBase64) {
}
