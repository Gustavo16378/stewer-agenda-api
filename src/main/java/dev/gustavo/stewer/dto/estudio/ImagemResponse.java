package dev.gustavo.stewer.dto.estudio;

import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Uma foto publica: o app monta a URL com GET /imagens/{id}. */
@Schema(name = "Imagem")
public record ImagemResponse(UUID id, String legenda, int ordem) {
}
