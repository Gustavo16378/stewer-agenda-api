package dev.gustavo.stewer.resource;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.servico.ServicoRequest;
import dev.gustavo.stewer.dto.servico.ServicoResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.ServicoService;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

@Path("/servicos")
@Tag(name = "Servicos", description = "Catalogo de servicos do estabelecimento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ServicoResource {

    @Inject
    ServicoService servicoService;

    @Inject
    UsuarioLogado usuarioLogado;

    @Inject
    dev.gustavo.stewer.service.EstudioService estudioService;

    @GET
    @Authenticated
    @Operation(summary = "Lista os servicos do estabelecimento, ordenados por nome")
    @SecurityRequirement(name = "bearerAuth")
    public List<ServicoResponse> listar(
            @Parameter(description = "Traz tambem os servicos desativados. "
                    + "Somente ADMIN; para os demais o parametro e ignorado.")
            @QueryParam("incluirInativos") @DefaultValue("false") boolean incluirInativos) {
        // Para cliente e profissional o catalogo e o que da para agendar hoje:
        // servico desativado nao aparece nem se o parametro vier na URL.
        return servicoService.listar(usuarioLogado.tenantId(),
                incluirInativos && usuarioLogado.isAdmin());
    }

    @GET
    @Path("/{id}")
    @Authenticated
    @Operation(summary = "Detalhe de um servico")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Servico encontrado"),
            @APIResponse(responseCode = "404", description = "Servico inexistente ou removido")
    })
    public ServicoResponse porId(@PathParam("id") UUID id) {
        return servicoService.porId(usuarioLogado.tenantId(), id);
    }

    @POST
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Cadastra um servico no catalogo")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Servico criado"),
            @APIResponse(responseCode = "403", description = "Somente ADMIN gerencia o catalogo"),
            @APIResponse(responseCode = "409", description = "Ja existe servico com este nome")
    })
    public Response criar(@Valid @NotNull(message = "corpo da requisicao e obrigatorio") ServicoRequest dto, @Context UriInfo uriInfo) {
        ServicoResponse criado = servicoService.criar(usuarioLogado.tenantId(), dto);
        URI local = uriInfo.getAbsolutePathBuilder().path(criado.id().toString()).build();
        return Response.created(local).entity(criado).build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Atualiza um servico do catalogo")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Servico atualizado"),
            @APIResponse(responseCode = "403", description = "Somente ADMIN gerencia o catalogo"),
            @APIResponse(responseCode = "404", description = "Servico inexistente ou removido"),
            @APIResponse(responseCode = "409", description = "Ja existe servico com este nome")
    })
    public ServicoResponse atualizar(@PathParam("id") UUID id, @Valid @NotNull(message = "corpo da requisicao e obrigatorio") ServicoRequest dto) {
        return servicoService.atualizar(usuarioLogado.tenantId(), id, dto);
    }

    @POST
    @Path("/{id}/fotos")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Poe uma foto de exemplo no servico (ate 6)")
    @SecurityRequirement(name = "bearerAuth")
    public Response adicionarFoto(@PathParam("id") UUID id,
            @Valid @NotNull dev.gustavo.stewer.dto.estudio.ImagemRequest dto) {
        return Response.status(Response.Status.CREATED)
                .entity(estudioService.adicionarFotoDoServico(usuarioLogado.tenantId(), id, dto)).build();
    }

    @DELETE
    @Path("/{id}/fotos/{fotoId}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Apaga uma foto de exemplo do servico")
    @SecurityRequirement(name = "bearerAuth")
    public Response removerFoto(@PathParam("id") UUID id, @PathParam("fotoId") UUID fotoId) {
        estudioService.removerFotoDoServico(usuarioLogado.tenantId(), id, fotoId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Remove um servico do catalogo (soft delete)")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Servico removido"),
            @APIResponse(responseCode = "403", description = "Somente ADMIN gerencia o catalogo"),
            @APIResponse(responseCode = "404", description = "Servico inexistente ou removido")
    })
    public Response remover(@PathParam("id") UUID id) {
        servicoService.remover(usuarioLogado.tenantId(), id);
        return Response.noContent().build();
    }
}
