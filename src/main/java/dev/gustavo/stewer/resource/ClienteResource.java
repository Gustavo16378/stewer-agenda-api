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
import dev.gustavo.stewer.dto.cliente.AniversarianteResponse;
import dev.gustavo.stewer.dto.cliente.AnotacaoRequest;
import dev.gustavo.stewer.dto.cliente.AnotacaoResponse;
import dev.gustavo.stewer.dto.cliente.ClienteDetalheResponse;
import dev.gustavo.stewer.dto.cliente.ClienteResumoResponse;
import dev.gustavo.stewer.dto.cliente.ClienteSumidoResponse;
import dev.gustavo.stewer.dto.cliente.FichaRequest;
import dev.gustavo.stewer.dto.cliente.FichaResponse;
import dev.gustavo.stewer.dto.cliente.FotoConteudoResponse;
import dev.gustavo.stewer.dto.cliente.FotoRequest;
import dev.gustavo.stewer.dto.cliente.FotoResponse;
import dev.gustavo.stewer.security.UsuarioLogado;
import dev.gustavo.stewer.service.ClienteService;
import dev.gustavo.stewer.service.FichaService;

import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

/**
 * Base de clientes na visao de quem atende. O CLIENTE nao entra aqui: ele
 * enxerga a propria agenda pelos endpoints de agendamento, nunca a lista de
 * quem mais frequenta o estudio nem as anotacoes feitas sobre ele.
 */
@Path("/clientes")
@Tag(name = "Clientes", description = "Base de clientes e anotacoes de visagismo")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class ClienteResource {

    @Inject
    ClienteService clienteService;

    @Inject
    FichaService fichaService;

    @Inject
    UsuarioLogado usuarioLogado;

    @GET
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Lista os clientes do estabelecimento",
            description = "A busca casa trecho de nome, e-mail ou telefone. "
                    + "O tamanho da pagina e limitado a 100 itens.")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Pagina de clientes"),
            @APIResponse(responseCode = "403", description = "Somente profissional ou admin")
    })
    public List<ClienteResumoResponse> listar(
            @QueryParam("busca") String busca,
            @QueryParam("pagina") @DefaultValue("0") int pagina,
            @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return clienteService.listar(usuarioLogado.tenantId(), busca, pagina, tamanho);
    }

    @GET
    @Path("/aniversariantes")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Clientes que fazem aniversario no mes",
            description = "Sem `mes`, usa o mes de hoje no fuso do estabelecimento.")
    @SecurityRequirement(name = "bearerAuth")
    public List<AniversarianteResponse> aniversariantes(@QueryParam("mes") Integer mes) {
        return fichaService.aniversariantes(usuarioLogado.tenantId(), mes);
    }

    @GET
    @Path("/sumidos")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Clientes que passaram do proprio ritmo sem voltar",
            description = "Quem nao tem horario marcado e esta ha mais de 1,5x o intervalo medio dele "
                    + "sem vir (no minimo 21 dias); com uma visita so, mais de 60 dias.")
    @SecurityRequirement(name = "bearerAuth")
    public List<ClienteSumidoResponse> sumidos() {
        return fichaService.sumidos(usuarioLogado.tenantId());
    }

    @GET
    @Path("/{id}")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Ficha do cliente com o historico de atendimentos")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Cliente e historico"),
            @APIResponse(responseCode = "404", description = "Nao existe cliente com este id no estabelecimento")
    })
    public ClienteDetalheResponse detalhar(@PathParam("id") UUID id) {
        return clienteService.detalhar(usuarioLogado.tenantId(), id);
    }

    @PUT
    @Path("/{id}/ficha")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Salva a ficha do cliente (aniversario, origem, etiquetas, visagismo)",
            description = "Substitui a ficha inteira: campo vazio apaga o valor.")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Ficha salva"),
            @APIResponse(responseCode = "404", description = "Cliente inexistente no estabelecimento")
    })
    public FichaResponse salvarFicha(@PathParam("id") UUID id,
            @Valid @NotNull(message = "corpo da requisicao e obrigatorio") FichaRequest dto) {
        return fichaService.salvarFicha(usuarioLogado.tenantId(), id, dto);
    }

    @GET
    @Path("/{id}/fotos")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Fotos do cliente (sem a imagem), da mais nova para a mais antiga")
    @SecurityRequirement(name = "bearerAuth")
    public List<FotoResponse> listarFotos(@PathParam("id") UUID id) {
        return fichaService.listarFotos(usuarioLogado.tenantId(), id);
    }

    @POST
    @Path("/{id}/fotos")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Adiciona uma foto de antes, depois ou outra",
            description = "A imagem vai em base64 (JPEG, PNG ou WebP, ate 1,5 MB).")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Foto guardada"),
            @APIResponse(responseCode = "422", description = "Tipo invalido, imagem vazia ou grande demais")
    })
    public Response adicionarFoto(@PathParam("id") UUID id,
            @Valid @NotNull(message = "corpo da requisicao e obrigatorio") FotoRequest dto,
            @Context UriInfo uriInfo) {
        FotoResponse criada = fichaService.adicionarFoto(usuarioLogado.tenantId(), id, dto);
        URI local = uriInfo.getAbsolutePathBuilder().path(criada.id().toString()).build();
        return Response.created(local).entity(criada).build();
    }

    @GET
    @Path("/{id}/fotos/{fotoId}")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "A imagem da foto, como data URL")
    @SecurityRequirement(name = "bearerAuth")
    public FotoConteudoResponse conteudoDaFoto(@PathParam("id") UUID id, @PathParam("fotoId") UUID fotoId) {
        return fichaService.conteudoDaFoto(usuarioLogado.tenantId(), id, fotoId);
    }

    @DELETE
    @Path("/{id}/fotos/{fotoId}")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Apaga uma foto")
    @SecurityRequirement(name = "bearerAuth")
    public Response removerFoto(@PathParam("id") UUID id, @PathParam("fotoId") UUID fotoId) {
        fichaService.removerFoto(usuarioLogado.tenantId(), id, fotoId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{id}/anotacoes")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Registra uma anotacao de visagismo sobre o cliente",
            description = "A anotacao e assinada pelo profissional do token; admin nao anota.")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Anotacao criada"),
            @APIResponse(responseCode = "404", description = "Cliente inexistente no estabelecimento"),
            @APIResponse(responseCode = "422", description = "Autor sem ficha de profissional ou agendamento invalido")
    })
    public Response criarAnotacao(@PathParam("id") UUID id, @Valid @NotNull(message = "corpo da requisicao e obrigatorio") AnotacaoRequest dto,
            @Context UriInfo uriInfo) {
        AnotacaoResponse criada = clienteService.criarAnotacao(
                usuarioLogado.tenantId(), usuarioLogado.usuarioId(), id, dto);

        URI local = uriInfo.getAbsolutePathBuilder().path(criada.id().toString()).build();
        return Response.created(local).entity(criada).build();
    }

    @GET
    @Path("/{id}/anotacoes")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Anotacoes do cliente, da mais nova para a mais antiga")
    @SecurityRequirement(name = "bearerAuth")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Anotacoes do cliente"),
            @APIResponse(responseCode = "404", description = "Cliente inexistente no estabelecimento")
    })
    public List<AnotacaoResponse> listarAnotacoes(@PathParam("id") UUID id) {
        return clienteService.listarAnotacoes(usuarioLogado.tenantId(), id);
    }

    @PUT
    @Path("/{id}/anotacoes/{anotacaoId}")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Corrige o texto de uma anotacao (so quem escreveu, ou o ADMIN)")
    @SecurityRequirement(name = "bearerAuth")
    public AnotacaoResponse editarAnotacao(@PathParam("id") UUID id,
            @PathParam("anotacaoId") UUID anotacaoId,
            @Valid @NotNull(message = "corpo da requisicao e obrigatorio") AnotacaoRequest dto) {
        return clienteService.editarAnotacao(usuarioLogado.tenantId(), id, anotacaoId, dto);
    }

    @DELETE
    @Path("/{id}/anotacoes/{anotacaoId}")
    @RolesAllowed({ Role.PROFISSIONAL_STR, Role.ADMIN_STR })
    @Operation(summary = "Apaga uma anotacao (so quem escreveu, ou o ADMIN)")
    @SecurityRequirement(name = "bearerAuth")
    public Response apagarAnotacao(@PathParam("id") UUID id, @PathParam("anotacaoId") UUID anotacaoId) {
        clienteService.apagarAnotacao(usuarioLogado.tenantId(), id, anotacaoId);
        return Response.noContent().build();
    }
}
