package dev.gustavo.stewer.resource;

import java.time.LocalDate;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.relatorio.RelatorioFinanceiroResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.RelatorioService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/** Relatorios do estudio, calculados a partir da agenda. So o admin ve. */
@Path("/relatorios")
@Tag(name = "Relatorios", description = "Financeiro e numeros do estudio")
@Produces(MediaType.APPLICATION_JSON)
public class RelatorioResource {

    @Inject
    RelatorioService relatorioService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @Path("/financeiro")
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Faturado, a receber, faltas e a serie do periodo (ate 400 dias)",
            description = "Compara com o mesmo tamanho de periodo logo antes. Sem agrupar: ate 31 dias "
                    + "por dia, ate 120 por semana, depois por mes.")
    @SecurityRequirement(name = "bearerAuth")
    public RelatorioFinanceiroResponse financeiro(
            @Parameter(description = "Primeiro dia, YYYY-MM-DD") @QueryParam("de")
            @NotNull(message = "de e obrigatoria") LocalDate de,
            @Parameter(description = "Ultimo dia (incluido), YYYY-MM-DD") @QueryParam("ate")
            @NotNull(message = "ate e obrigatoria") LocalDate ate,
            @Parameter(description = "DIA, SEMANA ou MES") @QueryParam("agrupar") String agrupar,
            @Parameter(description = "So de um profissional") @QueryParam("profissionalId") UUID profissionalId) {
        return relatorioService.financeiro(usuarioLogado.tenantId(), de, ate, agrupar, profissionalId);
    }
}
