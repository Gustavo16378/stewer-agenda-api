package dev.gustavo.stewer.exception;

import jakarta.ws.rs.core.Response;

/** 409 - conflito com o estado atual (e-mail duplicado, horario ocupado). */
public class ConflitoException extends ApiException {

    public ConflitoException(String mensagem) {
        this("CONFLITO", mensagem);
    }

    public ConflitoException(String codigo, String mensagem) {
        super(Response.Status.CONFLICT.getStatusCode(), codigo, mensagem);
    }
}
