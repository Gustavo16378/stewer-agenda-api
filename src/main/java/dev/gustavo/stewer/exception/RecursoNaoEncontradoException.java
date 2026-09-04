package dev.gustavo.stewer.exception;

import jakarta.ws.rs.core.Response;

/** 404 - o recurso nao existe, foi soft-deletado ou e de outro tenant. */
public class RecursoNaoEncontradoException extends ApiException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(Response.Status.NOT_FOUND.getStatusCode(), "RECURSO_NAO_ENCONTRADO", mensagem);
    }

    public static RecursoNaoEncontradoException de(String recurso, Object id) {
        return new RecursoNaoEncontradoException(recurso + " nao encontrado: " + id);
    }
}
