package dev.gustavo.stewer.security;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.ext.Provider;

/**
 * Cabecalhos de seguranca em toda resposta da API.
 *
 * <ul>
 * <li>{@code nosniff}: o navegador nao "adivinha" o tipo. Uma foto enviada como JPEG sai
 * como JPEG, nunca vira HTML executavel;</li>
 * <li>{@code DENY} e {@code frame-ancestors 'none'}: ninguem embute a API num iframe;</li>
 * <li>CSP {@code default-src 'none'}: resposta da API nao carrega nada, nem script;</li>
 * <li>HSTS: so HTTPS daqui para frente (o Render ja serve so HTTPS);</li>
 * <li>{@code no-store}: dado pessoal nao fica em cache de navegador nem de proxy. As
 * fotos publicas ({@code /imagens}) mantem o cache longo que o recurso definiu.</li>
 * </ul>
 */
@Provider
public class CabecalhosDeSeguranca implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext pedido, ContainerResponseContext resposta) {
        MultivaluedMap<String, Object> h = resposta.getHeaders();
        h.putSingle("X-Content-Type-Options", "nosniff");
        h.putSingle("X-Frame-Options", "DENY");
        h.putSingle("Referrer-Policy", "no-referrer");
        h.putSingle("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        h.putSingle("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        h.putSingle("Permissions-Policy", "camera=(), microphone=(), geolocation=()");
        if (!h.containsKey("Cache-Control")) {
            h.putSingle("Cache-Control", "no-store");
        }
    }
}
