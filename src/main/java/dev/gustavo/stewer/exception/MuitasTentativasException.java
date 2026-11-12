package dev.gustavo.stewer.exception;

/** 429: freio contra adivinhar senha ou codigo por forca bruta. */
public class MuitasTentativasException extends ApiException {

    public MuitasTentativasException(String mensagem) {
        super(429, "MUITAS_TENTATIVAS", mensagem);
    }
}
