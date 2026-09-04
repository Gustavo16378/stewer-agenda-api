package dev.gustavo.stewer.exception;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/** Traduz as falhas de negocio previstas para o corpo padrao de erro. */
@Provider
public class ApiExceptionMapper implements ExceptionMapper<ApiException> {

    @Override
    public Response toResponse(ApiException excecao) {
        int status = excecao.getStatus();
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErroResposta.de(excecao.getCodigo(), excecao.getMessage(), status))
                .build();
    }
}
