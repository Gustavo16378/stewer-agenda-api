package dev.gustavo.stewer.resource;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.cliente.AnotacaoRequest;
import dev.gustavo.stewer.dto.cliente.AnotacaoResponse;
import dev.gustavo.stewer.dto.cliente.ClienteDetalheResponse;
import dev.gustavo.stewer.dto.cliente.ClienteResumoResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.ClienteService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Base de clientes na visao de quem atende. O CLIENTE nao entra aqui: ele
 * enxerga a propria agenda pelos endpoints de agendamento, nunca a lista de
 * quem mais frequenta o estudio nem as anotacoes feitas sobre ele.
 */
@Path("/clientes")
@Tag(name = "Clientes", description = "Base de clientes e anotacoes de visagismo")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClienteResource {

    @Inject
    ClienteService clienteService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Lista os clientes do estabelecimento",
            description = "A busca casa trecho de nome, e-mail ou telefone. "
                    + "O tamanho da pagina e limitado a 100 itens.")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pagina de clientes"),
            @APIResponse(responseCode = "403", description = "Somente profissional ou admin")
    })
    public List<ClienteResumoResponse> listar(
            @QueryParam("busca") String busca,
            @QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return clienteService.listar(usuarioLogado.tenantId(), busca, pagina, tamanho);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Ficha do cliente com o historico de atendimentos")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Cliente e historico"),
            @APIResponse(responseCode = "404", description = "Nao existe cliente com este id no estabelecimento")
    })
    public ClienteDetalheResponse detalhar(@PathParam("id") UUID id) {
        return clienteService.detalhar(usuarioLogado.tenantId(), id);
    }

    @POST
    @Path("/{id}/anotacoes")
    @RolesAllowed({ Role.PROFISSIONAL_STR })
    @Operation(summary = "Registra uma anotacao de visagismo sobre o cliente",
            description = "A anotacao e assinada pelo profissional do token; admin nao anota.")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Anotacao criada"),
            @APIResponse(responseCode = "404", description = "Cliente inexistente no estabelecimento"),
            @APIResponse(responseCode = "422", description = "Autor sem ficha de profissional ou agendamento invalido")
    })
    public Response criarAnotacao(@PathParam("id") UUID id, @Valid AnotacaoRequest dto,
            @Context UriInfo uriInfo) {
        AnotacaoResponse criada = clienteService.criarAnotacao(
                usuarioLogado.tenantId(), usuarioLogado.usuarioId(), id, dto);

        URI local = uriInfo.getAbsolutePathBuilder().path(criada.id().toString()).build();
        return Response.created(local).entity(criada).build();
    }

    @GET
    @Path("/{id}/anotacoes")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Anotacoes do cliente, da mais nova para a mais antiga")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Anotacoes do cliente"),
            @APIResponse(responseCode = "404", description = "Cliente inexistente no estabelecimento")
    })
    public List<AnotacaoResponse> listarAnotacoes(@PathParam("id") UUID id) {
        return clienteService.listarAnotacoes(usuarioLogado.tenantId(), id);
    }
}
