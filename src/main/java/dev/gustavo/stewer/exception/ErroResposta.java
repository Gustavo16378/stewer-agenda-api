package dev.gustavo.stewer.exception;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/** Corpo padrao de erro da API. */
@Schema(name = "Erro", description = "Resposta de erro padronizada")
public record ErroResposta(
        @Schema(description = "Codigo estavel do erro", example = "RECURSO_NAO_ENCONTRADO")
        String codigo,
        @Schema(description = "Mensagem legivel", example = "Servico nao encontrado")
        String mensagem,
        @Schema(description = "Status HTTP", example = "404")
        int status,
        @Schema(description = "Momento do erro (UTC)")
        OffsetDateTime timestamp,
        @Schema(description = "Detalhes por campo, quando houver falha de validacao")
        List<Map<String, String>> violacoes) {

    public static ErroResposta de(String codigo, String mensagem, int status) {
        return new ErroResposta(codigo, mensagem, status, OffsetDateTime.now(ZoneOffset.UTC), null);
    }

    public static ErroResposta validacao(String mensagem, List<Map<String, String>> violacoes) {
        return new ErroResposta("VALIDACAO", mensagem, 400,
                OffsetDateTime.now(ZoneOffset.UTC), violacoes);
    }
}
