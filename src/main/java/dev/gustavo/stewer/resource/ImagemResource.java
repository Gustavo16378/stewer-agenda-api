package dev.gustavo.stewer.resource;

import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import dev.gustavo.stewer.domain.ImagemPublica;
import dev.gustavo.stewer.service.EstudioService;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;

/**
 * Fotos publicas servidas como imagem de verdade, para o app usar direto numa tag de
 * imagem. Cada foto nova tem id novo, entao o cache pode durar um ano.
 */
@Path("/imagens")
@Tag(name = "Estudio")
public class ImagemResource {

    @Inject
    EstudioService estudioService;

    @GET
    @Path("/{id}")
    @PermitAll
    @Produces({"image/jpeg", "image/png", "image/webp"})
    @Operation(summary = "A foto (retrato, trabalho ou exemplo de servico)")
    public Response imagem(@PathParam("id") UUID id) {
        ImagemPublica imagem = estudioService.conteudo(id);
        return Response.ok(imagem.getConteudo(), imagem.getTipoConteudo())
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .build();
    }
}
