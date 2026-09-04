package dev.gustavo.stewer.exception;

import jakarta.ws.rs.core.Response;

/** 401 - credenciais invalidas ou token ausente/ilegivel. */
public class AutenticacaoException extends ApiException {

    public AutenticacaoException(String mensagem) {
        super(Response.Status.UNAUTHORIZED.getStatusCode(), "NAO_AUTENTICADO", mensagem);
    }
}
