package dev.gustavo.stewer.resource;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.security.SecurityRequirement;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.dto.auth.LoginRequest;
import dev.gustavo.stewer.dto.auth.RegistrarRequest;
import dev.gustavo.stewer.dto.auth.TokenResponse;
import dev.gustavo.stewer.dto.usuario.UsuarioResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.AuthService;

import io.quarkus.security.Authenticated;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/auth")
@Tag(name = "Auth", description = "Cadastro e autenticacao")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class AuthResource {

    @Inject
    AuthService authService;

    @Inject
    UsuarioLogado usuarioLogado;

    @POST
    @Path("/registrar")
    @PermitAll
    @Operation(summary = "Cria uma conta de CLIENTE e ja devolve o token")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Conta criada"),
            @APIResponse(responseCode = "409", description = "E-mail ja cadastrado no estabelecimento")
    })
    public Response registrar(@Valid RegistrarRequest dto) {
        TokenResponse resposta = authService.registrar(dto);
        return Response.status(Response.Status.CREATED).entity(resposta).build();
    }

    @GET
    @Path("/eu")
    @Authenticated
    @Operation(summary = "Dados do usuario do token atual")
    @SecurityRequirement(name = "bearerAuth")
    public UsuarioResponse eu() {
        return authService.porId(usuarioLogado.tenantId(), usuarioLogado.usuarioId());
    }

    @POST
    @Path("/login")
    @PermitAll
    @Operation(summary = "Autentica e devolve um JWT com role e tenant")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Autenticado"),
            @APIResponse(responseCode = "401", description = "E-mail ou senha invalidos")
    })
    public TokenResponse login(@Valid LoginRequest dto) {
        return authService.login(dto);
    }
}
