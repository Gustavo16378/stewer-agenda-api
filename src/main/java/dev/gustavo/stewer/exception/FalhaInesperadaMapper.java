package dev.gustavo.stewer.exception;

import org.jboss.logging.Logger;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Rede de seguranca: qualquer excecao nao prevista vira 500 com corpo padrao,
 * e o stack trace fica no log em vez de vazar para o cliente.
 */
@Provider
public class FalhaInesperadaMapper implements ExceptionMapper<Throwable> {

    private static final Logger LOG = Logger.getLogger(FalhaInesperadaMapper.class);

    @Override
    public Response toResponse(Throwable falha) {
        // Deixa passar o que o proprio JAX-RS ja resolveu (404 de rota, 405, 401 do JWT...).
        if (falha instanceof WebApplicationException web) {
            Response original = web.getResponse();
            if (original.getEntity() != null) {
                return original;
            }
            int status = original.getStatus();
            return Response.status(status)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(ErroResposta.de(codigoPara(status), mensagemPara(status, web), status))
                    .build();
        }

        LOG.error("Falha nao tratada ao processar a requisicao", falha);
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(ErroResposta.de("ERRO_INTERNO", "Erro interno inesperado", 500))
                .build();
    }

    private static String codigoPara(int status) {
        return switch (status) {
            case 401 -> "NAO_AUTENTICADO";
            case 403 -> "ACESSO_NEGADO";
            case 404 -> "RECURSO_NAO_ENCONTRADO";
            case 405 -> "METODO_NAO_PERMITIDO";
            default -> "ERRO";
        };
    }

    private static String mensagemPara(int status, WebApplicationException web) {
        return switch (status) {
            case 401 -> "Autenticacao obrigatoria";
            case 403 -> "Acesso negado";
            case 404 -> "Recurso nao encontrado";
            default -> web.getMessage() == null ? "Erro" : web.getMessage();
        };
    }
}
