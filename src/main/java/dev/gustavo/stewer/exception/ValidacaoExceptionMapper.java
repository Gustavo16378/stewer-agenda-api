package dev.gustavo.stewer.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Bean Validation -> 400 com a lista de campos que falharam.
 *
 * <p>O Quarkus ja traduz {@code ConstraintViolationException} sozinho, mas o
 * formato dele nao e o mesmo do resto da API; este mapper unifica tudo.
 */
@Provider
public class ValidacaoExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException excecao) {
        List<Map<String, String>> violacoes = excecao.getConstraintViolations().stream()
                .map(v -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("campo", ultimoTrecho(v.getPropertyPath().toString()));
                    item.put("mensagem", v.getMessage());
                    return item;
                })
                .toList();

        return Response.status(Response.Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErroResposta.validacao("Requisicao invalida", violacoes))
                .build();
    }

    /** "criar.dto.email" -> "email": o cliente da API nao precisa do caminho todo. */
    private static String ultimoTrecho(String caminho) {
        int corte = caminho.lastIndexOf('.');
        return corte < 0 ? caminho : caminho.substring(corte + 1);
    }
}
