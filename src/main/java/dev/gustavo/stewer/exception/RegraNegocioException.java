package dev.gustavo.stewer.exception;

/** 422 - requisicao bem formada, mas invalida frente as regras do dominio. */
public class RegraNegocioException extends ApiException {

    public RegraNegocioException(String mensagem) {
        this("REGRA_NEGOCIO", mensagem);
    }

    public RegraNegocioException(String codigo, String mensagem) {
        // 422 nao existe em Response.Status; usamos o codigo numerico direto.
        super(422, codigo, mensagem);
    }
}
