package dev.gustavo.stewer.exception;

import jakarta.ws.rs.core.Response;

/** 403 - autenticado, mas sem permissao sobre este recurso. */
public class AcessoNegadoException extends ApiException {

    public AcessoNegadoException(String mensagem) {
        super(Response.Status.FORBIDDEN.getStatusCode(), "ACESSO_NEGADO", mensagem);
    }
}
