package dev.gustavo.stewer.dto.usuario;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Troca de senha com a sessao aberta: pede a atual para que um celular esquecido desbloqueado nao baste. */
@Schema(name = "TrocarSenhaRequest")
public record TrocarSenhaRequest(
        @NotBlank(message = "senhaAtual e obrigatoria")
        String senhaAtual,

        @NotBlank(message = "novaSenha e obrigatoria")
        @Size(min = 8, max = 72, message = "novaSenha deve ter entre 8 e 72 caracteres")
        String novaSenha) {
}
