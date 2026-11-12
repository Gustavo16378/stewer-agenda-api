package dev.gustavo.stewer.resource;

import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.auditoria.EventoAuditoriaResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.AuditoriaService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

@Path("/auditoria")
@Tag(name = "Auditoria", description = "Quem fez o que e quando")
@Produces(MediaType.APPLICATION_JSON)
public class AuditoriaResource {

    @Inject
    AuditoriaService auditoriaService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Eventos de auditoria, do mais novo para o mais antigo",
            description = "ADMIN ve tudo. Profissional so com `clienteId` (a linha do tempo daquele cliente).")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pagina de eventos"),
            @APIResponse(responseCode = "403", description = "Profissional pedindo a auditoria completa")
    })
    public List<EventoAuditoriaResponse> buscar(
            @Parameter(description = "Filtra por tipo: AGENDAMENTO, SERVICO, PROFISSIONAL, HORARIOS, "
                    + "BLOQUEIO, FICHA, ANOTACAO, FOTO, CLIENTE (pode repetir)")
            @QueryParam("entidade") List<String> entidades,
            @QueryParam("clienteId") UUID clienteId,
            @QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("tamanho") @DefaultValue("30") int tamanho) {
        return auditoriaService.buscar(usuarioLogado.tenantId(), entidades, clienteId, pagina, tamanho);
    }
}
