package dev.gustavo.stewer.dto.agendamento;

import java.time.OffsetDateTime;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Um slot livre da agenda, no formato meio-aberto {@code [inicio, fim)}.
 *
 * <p>O {@code fim} vai junto para o front nao precisar repetir a conta da
 * duracao do servico ao montar o botao do horario.
 */
@Schema(name = "HorarioLivre", description = "Janela livre pronta para ser reservada")
public record HorarioLivreResponse(
        OffsetDateTime inicio,
        OffsetDateTime fim) {
}
