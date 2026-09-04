package dev.gustavo.stewer.exception;

/**
 * Base das falhas previstas de negocio.
 *
 * <p>Carrega o status HTTP e um codigo estavel para o cliente da API tratar o
 * erro sem depender do texto da mensagem.
 *
 * <p>O status e um {@code int}, e nao um {@code Response.Status}: aquele enum
 * nao tem 422 (Unprocessable Entity), e {@code Response.Status.fromStatusCode(422)}
 * devolve {@code null} silenciosamente -- o que virava NullPointerException
 * dentro do ExceptionMapper e transformava um 422 honesto em 500.
 */
public abstract class ApiException extends RuntimeException {

    private final int status;
    private final String codigo;

    protected ApiException(int status, String codigo, String mensagem) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
    }

    public int getStatus() {
        return status;
    }

    public String getCodigo() {
        return codigo;
    }
}
