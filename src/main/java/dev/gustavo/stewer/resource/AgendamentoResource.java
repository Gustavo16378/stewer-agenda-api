package dev.gustavo.stewer.resource;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.agendamento.AgendamentoRequest;
import dev.gustavo.stewer.dto.agendamento.AgendamentoResponse;
import dev.gustavo.stewer.dto.agendamento.AtualizarStatusRequest;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.AgendamentoService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/agendamentos")
@Tag(name = "Agendamentos", description = "Horarios livres e reservas")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AgendamentoResource {

    @Inject
    AgendamentoService agendamentoService;

    @Inject
    UsuarioLogado usuarioLogado;

    @POST
    @RolesAllowed({Role.CLIENTE_STR})
    @Operation(summary = "Cliente reserva um horario para si")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Agendamento criado"),
            @APIResponse(responseCode = "404", description = "Profissional ou servico nao encontrado"),
            @APIResponse(responseCode = "409", description = "Horario ocupado ou bloqueado"),
            @APIResponse(responseCode = "422", description = "Horario no passado ou fora da grade")
    })
    public Response criar(@Valid @NotNull(message = "corpo da requisicao e obrigatorio") AgendamentoRequest dto) {
        AgendamentoResponse criado = agendamentoService.criar(
                usuarioLogado.tenantId(), usuarioLogado.usuarioId(), dto);
        return Response.created(URI.create("/agendamentos/" + criado.id()))
                .entity(criado)
                .build();
    }

    @GET
    @Path("/meus")
    @RolesAllowed({Role.CLIENTE_STR})
    @Operation(summary = "Agendamentos do cliente logado")
    @SecurityRequirement(name = "bearerAuth")
    public List<AgendamentoResponse> meus(
            @Parameter(description = "futuros, historico ou todos")
            @QueryParam("periodo") @DefaultValue("todos") String periodo) {

        return agendamentoService.meus(usuarioLogado.tenantId(), usuarioLogado.usuarioId(),
                AgendamentoService.Periodo.de(periodo));
    }

    @GET
    @Path("/agenda")
    @RolesAllowed({Role.PROFISSIONAL_STR, Role.ADMIN_STR})
    @Operation(summary = "Agenda de um dia, com todos os status")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Agenda do dia"),
            @APIResponse(responseCode = "422", description = "ADMIN chamou sem informar profissionalId")
    })
    public List<AgendamentoResponse> agenda(
            @Parameter(description = "Dia a consultar, no formato YYYY-MM-DD")
            @QueryParam("data") @NotNull(message = "data e obrigatoria") LocalDate data,

            @Parameter(description = "Obrigatorio para ADMIN; ignorado para PROFISSIONAL")
            @QueryParam("profissionalId") UUID profissionalId) {

        return agendamentoService.agendaDoDia(usuarioLogado.tenantId(), data, profissionalId);
    }

    @PATCH
    @Path("/{id}/status")
    @RolesAllowed({Role.CLIENTE_STR, Role.PROFISSIONAL_STR, Role.ADMIN_STR})
    @Operation(summary = "Concluir, cancelar ou marcar nao comparecimento")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Status atualizado"),
            @APIResponse(responseCode = "403", description = "Sem permissao sobre este agendamento"),
            @APIResponse(responseCode = "404", description = "Agendamento nao encontrado"),
            @APIResponse(responseCode = "409", description = "Agendamento ja esta num status final")
    })
    public AgendamentoResponse atualizarStatus(@PathParam("id") UUID id,
            @Valid @NotNull(message = "corpo da requisicao e obrigatorio") AtualizarStatusRequest dto) {
        return agendamentoService.atualizarStatus(usuarioLogado.tenantId(), id, dto.status());
    }
}
