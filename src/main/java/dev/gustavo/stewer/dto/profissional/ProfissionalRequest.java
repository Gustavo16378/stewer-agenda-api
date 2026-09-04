package dev.gustavo.stewer.dto.profissional;

import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e edicao da ficha de um profissional.
 *
 * <p>O profissional nunca nasce do nada: ele sempre espelha um {@code Usuario}
 * que ja existe no estabelecimento. Por isso {@code usuarioId} e obrigatorio
 * tambem no PUT -- ali ele funciona como confirmacao de quem e o dono da ficha,
 * e mandar outro valor e recusado em vez de trocar a pessoa em silencio.
 */
@Schema(name = "ProfissionalRequest")
public record ProfissionalRequest(

        @NotNull(message = "usuarioId e obrigatorio")
        @Schema(description = "Usuario do estabelecimento que passa a atender",
                example = "3f4a1c8e-6b2d-4f11-9c7a-0d5e8b2a1f34")
        UUID usuarioId,

        @Size(max = 120, message = "especialidade deve ter no maximo 120 caracteres")
        @Schema(example = "Visagismo e consultoria de imagem")
        String especialidade,

        @Size(max = 4000, message = "bio deve ter no maximo 4000 caracteres")
        @Schema(example = "Dez anos de estudio, especialista em colorimetria.")
        String bio,

        @Size(max = 500, message = "fotoUrl deve ter no maximo 500 caracteres")
        @Schema(example = "https://cdn.stewer.dev/fotos/gabriel.webp")
        String fotoUrl,

        @Schema(description = "Se aparece na agenda. Omitido na criacao, assume true.",
                example = "true")
        Boolean ativo) {
}
