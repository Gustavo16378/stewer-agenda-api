package dev.gustavo.stewer.resource;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.estudio.EstudioRequest;
import dev.gustavo.stewer.dto.estudio.EstudioResponse;
import dev.gustavo.stewer.dto.estudio.ImagemRequest;
import dev.gustavo.stewer.dto.estudio.ImagemResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.EstudioService;
import dev.gustavo.stewer.service.TenantService;

import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/** O estudio: o cliente le (sem login, a tela de entrar tambem usa); o admin edita. */
@Path("/estudio")
@Tag(name = "Estudio", description = "Endereco, contatos e fotos do estudio")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class EstudioResource {

    @Inject
    EstudioService estudioService;

    @Inject
    TenantService tenantService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @PermitAll
    @Operation(summary = "O estudio como o cliente ve",
            description = "Campos nulos: o app usa o conteudo que ja vem nele.")
    public EstudioResponse ver(
            @Parameter(description = "Slug do estabelecimento; omitido, usa o padrao")
            @QueryParam("tenantSlug") String tenantSlug) {
        return estudioService.ver(tenantService.resolver(tenantSlug).getId());
    }

    @PUT
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Atualiza endereco, mapa, horario e contatos")
    @SecurityRequirement(name = "bearerAuth")
    public EstudioResponse salvar(@Valid @NotNull EstudioRequest dto) {
        return estudioService.salvar(usuarioLogado.tenantId(), dto);
    }

    @POST
    @Path("/fotos")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Poe o retrato (troca o atual) ou uma foto do trabalho")
    @SecurityRequirement(name = "bearerAuth")
    public Response adicionarFoto(@Valid @NotNull ImagemRequest dto) {
        return Response.status(Response.Status.CREATED)
                .entity(estudioService.adicionarFoto(usuarioLogado.tenantId(), dto)).build();
    }

    @PUT
    @Path("/fotos/{id}/posicao/{posicao}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Muda a ordem de uma foto do trabalho (0 = primeira)")
    @SecurityRequirement(name = "bearerAuth")
    public List<ImagemResponse> moverFoto(@PathParam("id") UUID id, @PathParam("posicao") @Min(0) int posicao) {
        return estudioService.moverFoto(usuarioLogado.tenantId(), id, posicao);
    }

    @DELETE
    @Path("/fotos/{id}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Apaga o retrato ou uma foto do trabalho")
    @SecurityRequirement(name = "bearerAuth")
    public Response removerFoto(@PathParam("id") UUID id) {
        estudioService.removerFoto(usuarioLogado.tenantId(), id);
        return Response.noContent().build();
    }
}
