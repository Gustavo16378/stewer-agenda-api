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
import dev.gustavo.stewer.dto.disponibilidade.BloqueioRequest;
import dev.gustavo.stewer.dto.disponibilidade.BloqueioResponse;
import dev.gustavo.stewer.dto.disponibilidade.DisponibilidadeResponse;
import dev.gustavo.stewer.dto.disponibilidade.GradeSemanalRequest;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.DisponibilidadeService;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * Agenda de um profissional: a grade semanal e os bloqueios pontuais.
 *
 * <p>Vive fora do {@code ProfissionalResource} de proposito -- la mora o CRUD da
 * ficha, que e assunto exclusivo do ADMIN; aqui o dono da agenda tambem escreve.
 * Ler a agenda, por outro lado, e liberado a qualquer autenticado: o cliente
 * precisa disso para saber quando o profissional atende.
 */
@Path("/profissionais/{profissionalId}")
@Tag(name = "Agenda do profissional", description = "Grade semanal de atendimento e bloqueios")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AgendaProfissionalResource {

    @Inject
    DisponibilidadeService disponibilidadeService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @Path("/disponibilidade")
    @Authenticated
    @Operation(summary = "Grade semanal vigente, ordenada por dia e hora de inicio")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Grade do profissional"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido")
    })
    public List<DisponibilidadeResponse> grade(@PathParam("profissionalId") UUID profissionalId) {
        return disponibilidadeService.grade(usuarioLogado.tenantId(), profissionalId);
    }

    @PUT
    @Path("/disponibilidade")
    @RolesAllowed({Role.PROFISSIONAL_STR, Role.ADMIN_STR})
    @Operation(summary = "Substitui a grade semanal inteira pelas faixas enviadas")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Grade substituida"),
            @APIResponse(responseCode = "403", description = "Profissional tentando editar agenda alheia"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido"),
            @APIResponse(responseCode = "422", description = "Faixa invertida ou faixas sobrepostas no mesmo dia")
    })
    public List<DisponibilidadeResponse> substituirGrade(
            @PathParam("profissionalId") UUID profissionalId,
            @Valid @NotNull(message = "corpo da requisicao e obrigatorio") GradeSemanalRequest dto) {
        return disponibilidadeService.substituirGrade(usuarioLogado.tenantId(), profissionalId, dto);
    }

    @POST
    @Path("/bloqueios")
    @RolesAllowed({Role.PROFISSIONAL_STR, Role.ADMIN_STR})
    @Operation(summary = "Bloqueia uma janela da agenda (folga, curso, compromisso)")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Bloqueio criado"),
            @APIResponse(responseCode = "403", description = "Profissional tentando bloquear agenda alheia"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido"),
            @APIResponse(responseCode = "422", description = "Fim do bloqueio anterior ou igual ao inicio")
    })
    public Response criarBloqueio(@PathParam("profissionalId") UUID profissionalId,
            @Valid @NotNull(message = "corpo da requisicao e obrigatorio") BloqueioRequest dto) {
        BloqueioResponse criado =
                disponibilidadeService.criarBloqueio(usuarioLogado.tenantId(), profissionalId, dto);
        return Response
                .created(URI.create("/profissionais/" + profissionalId + "/bloqueios/" + criado.id()))
                .entity(criado)
                .build();
    }

    @GET
    @Path("/bloqueios")
    @Authenticated
    @Operation(summary = "Bloqueios vigentes do profissional")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Bloqueios do profissional"),
            @APIResponse(responseCode = "404", description = "Profissional inexistente ou removido")
    })
    public List<BloqueioResponse> listarBloqueios(
            @PathParam("profissionalId") UUID profissionalId) {
        return disponibilidadeService.listarBloqueios(usuarioLogado.tenantId(), profissionalId);
    }

    @DELETE
    @Path("/bloqueios/{bloqueioId}")
    @RolesAllowed({Role.PROFISSIONAL_STR, Role.ADMIN_STR})
    @Operation(summary = "Libera a janela bloqueada (soft delete)")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "204", description = "Bloqueio removido"),
            @APIResponse(responseCode = "403", description = "Profissional tentando mexer em agenda alheia"),
            @APIResponse(responseCode = "404", description = "Bloqueio inexistente ou de outro profissional")
    })
    public Response removerBloqueio(@PathParam("profissionalId") UUID profissionalId,
            @PathParam("bloqueioId") UUID bloqueioId) {
        disponibilidadeService.removerBloqueio(usuarioLogado.tenantId(), profissionalId, bloqueioId);
        return Response.noContent().build();
    }
}
