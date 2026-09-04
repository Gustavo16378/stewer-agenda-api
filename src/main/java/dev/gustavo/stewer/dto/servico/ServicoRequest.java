package dev.gustavo.stewer.dto.servico;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Cadastro e edicao de servico do catalogo.
 *
 * <p>O {@code tenantId} nunca vem daqui: quem manda e o token. Preco em
 * centavos pelo mesmo motivo da entidade -- nada de ponto flutuante em dinheiro.
 */
@Schema(name = "ServicoRequest")
public record ServicoRequest(
        @NotBlank(message = "nome e obrigatorio")
        @Size(max = 140, message = "nome deve ter no maximo 140 caracteres")
        @Schema(example = "Consultoria de imagem")
        String nome,

        @Size(max = 2000, message = "descricao deve ter no maximo 2000 caracteres")
        @Schema(example = "Analise de visagismo com proposta de corte e coloracao")
        String descricao,

        @NotNull(message = "duracaoMinutos e obrigatorio")
        @Min(value = 5, message = "duracaoMinutos deve ser no minimo 5")
        @Max(value = 600, message = "duracaoMinutos deve ser no maximo 600")
        @Schema(example = "60")
        Integer duracaoMinutos,

        @NotNull(message = "precoCentavos e obrigatorio")
        @PositiveOrZero(message = "precoCentavos nao pode ser negativo")
        @Schema(description = "Preco em centavos", example = "25000")
        Long precoCentavos,

        @Schema(description = "Servico visivel para agendamento. Omitido, assume true.",
                example = "true")
        Boolean ativo) {

    /**
     * Ausencia de {@code ativo} significa servico disponivel: e o caso comum
     * tanto no cadastro quanto na edicao, e evita que um front antigo, que nem
     * conhece o campo, desative o catalogo inteiro sem querer.
     */
    public boolean ativoOuPadrao() {
        return ativo == null || ativo;
    }
}
