package dev.gustavo.stewer.dto.cliente;

import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import dev.gustavo.stewer.domain.FotoCliente;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Foto nova. O conteudo vem em base64 (o app ja reduziu a imagem). Limite de
 * 2 MB em base64, cerca de 1,5 MB de imagem.
 */
@Schema(name = "FotoRequest")
public record FotoRequest(
        @NotNull(message = "diga se a foto e de antes, de depois ou outra")
        FotoCliente.Momento momento,
        @Size(max = 200, message = "legenda com no maximo 200 caracteres")
        String legenda,
        @Schema(description = "Atendimento a que a foto pertence (opcional)")
        UUID agendamentoId,
        @NotBlank(message = "o tipo da imagem e obrigatorio")
        @Schema(examples = "image/jpeg")
        String tipoConteudo,
        @NotBlank(message = "a imagem e obrigatoria")
        @Size(max = 2_100_000, message = "imagem grande demais; reduza antes de enviar")
        String conteudoBase64) {
}
