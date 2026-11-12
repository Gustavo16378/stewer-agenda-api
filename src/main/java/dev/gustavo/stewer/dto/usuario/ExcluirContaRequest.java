package dev.gustavo.stewer.dto.usuario;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;

/** Excluir a conta pede a senha: um celular desbloqueado na mao de outra pessoa nao basta. */
@Schema(name = "ExcluirContaRequest")
public record ExcluirContaRequest(
        @NotBlank(message = "senha e obrigatoria")
        String senha) {
}
