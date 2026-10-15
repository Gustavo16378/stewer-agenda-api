package dev.gustavo.stewer.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Sinal de vida que NAO encosta no banco.
 *
 * <p>Existe por causa de uma conta: o Web Service gratuito do Render dorme
 * depois de 15 minutos sem trafego e leva cerca de um minuto para acordar,
 * entao um ping externo bate aqui de 10 em 10 minutos para manter a API de pe.
 * O compute do Neon gratuito, do outro lado, dorme depois de 5 minutos sem
 * consulta e so tem 100 CU-hours por mes.
 *
 * <p>Se esse ping consultasse o banco, ele sozinho manteria o Neon acordado 24
 * horas por dia e as CU-hours acabariam na primeira quinzena. Dai a regra: este
 * endpoint responde com uma string constante e nada mais. Nenhuma injecao de
 * datasource, nenhum repositorio, nenhuma transacao.
 *
 * <p>Quem precisa saber se o banco responde usa {@code GET /q/health/ready},
 * que e para uso humano e acorda o Neon de proposito.
 */
@Path("/ping")
@Tag(name = "Operacao", description = "Sinais de vida da API")
public class PingResource {

    @GET
    @PermitAll
    @Produces(MediaType.TEXT_PLAIN)
    @Operation(summary = "Responde 200 sem tocar no banco",
            description = "Usado como health check do Render e pelo ping que impede a "
                    + "instancia gratuita de dormir. Nao abre conexao com o banco de "
                    + "proposito: o compute do Neon precisa poder dormir.")
    @APIResponse(responseCode = "200", description = "A API esta de pe")
    public String ping() {
        return "pong";
    }
}
