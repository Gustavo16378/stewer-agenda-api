package dev.gustavo.stewer.dto.disponibilidade;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Grade semanal completa de um profissional.
 *
 * <p>O PUT substitui a semana inteira em vez de aplicar um diff: a tela de
 * agenda edita a grade como um todo e reenviar o estado final elimina a
 * necessidade de endpoints separados para incluir e remover faixa.
 *
 * <p>Lista vazia e um valor legitimo -- significa "nao atendo em nenhum dia" --
 * por isso {@code @NotNull} e nao {@code @NotEmpty}.
 */
@Schema(name = "GradeSemanal", description = "Faixas de atendimento da semana inteira")
public record GradeSemanalRequest(

        @NotNull(message = "faixas e obrigatorio")
        @Valid
        @Schema(description = "Faixas da semana. Lista vazia zera a agenda do profissional.")
        List<FaixaHorariaRequest> faixas) {
}
