package dev.gustavo.stewer.dto.profissional;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Profissional pronto para o front.
 *
 * <p>Nome, e-mail e telefone vem desnormalizados do {@code Usuario}: as telas de
 * equipe e de escolha de profissional so precisam desses tres campos e nao
 * teriam por que fazer uma segunda chamada em {@code /usuarios} para monta-las.
 */
@Schema(name = "Profissional")
public record ProfissionalResponse(
        UUID id,
        UUID tenantId,
        UUID usuarioId,
        String nome,
        String email,
        String telefone,
        String especialidade,
        String bio,
        String fotoUrl,
        boolean ativo,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {
}
