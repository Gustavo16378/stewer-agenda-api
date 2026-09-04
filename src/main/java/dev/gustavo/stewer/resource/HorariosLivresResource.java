package dev.gustavo.stewer.resource;

import java.time.LocalDate;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.dto.agendamento.HorariosLivresResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.HorariosLivresService;

import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Consulta de horarios livres.
 *
 * <p>Fica numa classe propria, e nao dentro de {@code AgendaProfissionalResource},
 * porque a natureza e outra: la sao os dados que o profissional mantem; aqui e
 * um calculo derivado que o cliente consome para escolher o horario.
 */
@Path("/profissionais/{profissionalId}/horarios-livres")
@Tag(name = "Agendamentos", description = "Horarios livres e reservas")
@Produces(MediaType.APPLICATION_JSON)
public class HorariosLivresResource {

    @Inject
    HorariosLivresService horariosLivresService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @Authenticated
    @Operation(summary = "Slots livres do profissional em um dia, fatiados pela duracao do servico")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200",
                    description = "Lista de slots; vazia quando o profissional nao atende no dia"),
            @APIResponse(responseCode = "404", description = "Profissional ou servico nao encontrado"),
            @APIResponse(responseCode = "422", description = "Servico ou profissional inativo")
    })
    public HorariosLivresResponse horariosLivres(
            @PathParam("profissionalId") UUID profissionalId,

            @Parameter(description = "Dia a consultar, no formato YYYY-MM-DD", example = "2026-03-02")
            @QueryParam("data")
            @NotNull(message = "data e obrigatoria") LocalDate data,

            @Parameter(description = "Servico desejado; a duracao dele define o tamanho do slot")
            @QueryParam("servicoId")
            @NotNull(message = "servicoId e obrigatorio") UUID servicoId) {

        return horariosLivresService.calcular(usuarioLogado.tenantId(), profissionalId, data,
                servicoId);
    }
}
