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
import dev.gustavo.stewer.dto.profissional.ProfissionalRequest;
import dev.gustavo.stewer.dto.profissional.ProfissionalResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.ProfissionalService;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
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
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/profissionais")
@Tag(name = "Profissionais", description = "Equipe que atende no estabelecimento")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ProfissionalResource {

    @Inject
    ProfissionalService profissionalService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @Authenticated
    @Operation(summary = "Lista os profissionais do estabelecimento")
    @SecurityRequirement(name = "bearerAuth")
    public List<ProfissionalResponse> listar(
            @Parameter(description = "Traz tambem os inativos. Ignorado para quem nao e ADMIN.")
            @QueryParam("incluirInativos") @DefaultValue("false") boolean incluirInativos) {
        // Cliente escolhendo com quem marcar nao pode nem saber que existe
        // profissional desativado, entao a flag so vale para o ADMIN.
        boolean inativos = incluirInativos && usuarioLogado.isAdmin();
        return profissionalService.listar(usuarioLogado.tenantId(), inativos);
    }

    @GET
    @Path("/{id}")
    @Authenticated
    @Operation(summary = "Detalha um profissional")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Profissional encontrado"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido")
    })
    public ProfissionalResponse porId(@PathParam("id") UUID id) {
        return profissionalService.porId(usuarioLogado.tenantId(), id);
    }

    @POST
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Transforma um usuario do estabelecimento em profissional")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Profissional cadastrado"),
            @APIResponse(responseCode = "404", description = "Usuario informado nao existe no estabelecimento"),
            @APIResponse(responseCode = "409", description = "O usuario ja e profissional")
    })
    public Response criar(@Valid ProfissionalRequest dto) {
        ProfissionalResponse criado = profissionalService.criar(usuarioLogado.tenantId(), dto);
        return Response.created(URI.create("/profissionais/" + criado.id()))
                .entity(criado)
                .build();
    }

    @PUT
    @Path("/{id}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Atualiza a ficha do profissional")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Ficha atualizada"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido"),
            @APIResponse(responseCode = "422", description = "Tentativa de trocar o usuario da ficha")
    })
    public ProfissionalResponse atualizar(@PathParam("id") UUID id, @Valid ProfissionalRequest dto) {
        return profissionalService.atualizar(usuarioLogado.tenantId(), id, dto);
    }

    @DELETE
    @Path("/{id}")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Remove o profissional da equipe (soft delete)")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Profissional removido"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido")
    })
    public Response remover(@PathParam("id") UUID id) {
        profissionalService.remover(usuarioLogado.tenantId(), id);
        return Response.noContent().build();
    }
}
