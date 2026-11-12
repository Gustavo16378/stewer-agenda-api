package dev.gustavo.stewer.resource;

import java.util.List;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.AuthService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

/**
 * Pessoas do estabelecimento. Existe para o admin escolher por nome quem vira
 * profissional, em vez de colar um id interno que o app nao mostra a ninguem.
 */
@Path("/usuarios")
@Tag(name = "Usuarios", description = "Pessoas do estabelecimento (somente ADMIN)")
@Produces(MediaType.APPLICATION_JSON)
public class UsuarioResource {

    @Inject
    AuthService authService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @RolesAllowed({Role.ADMIN_STR})
    @Operation(summary = "Busca pessoas por nome ou e-mail, de qualquer papel")
    @SecurityRequirement(name = "bearerAuth")
    public List<UsuarioResponse> buscar(
            @Parameter(description = "Trecho do nome ou do e-mail") @QueryParam("busca") String busca,
            @QueryParam("pagina") @DefaultValue("0") @Min(0) int pagina,
            @QueryParam("tamanho") @DefaultValue("20") @Min(1) @Max(100) int tamanho) {
        return authService.buscarPessoas(usuarioLogado.tenantId(), busca, pagina, tamanho);
    }
}
