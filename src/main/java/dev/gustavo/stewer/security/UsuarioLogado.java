package dev.gustavo.stewer.security;

import java.util.UUID;

import org.eclipse.microprofile.jwt.JsonWebToken;

import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.exception.AcessoNegadoException;
import dev.gustavo.stewer.exception.AutenticacaoException;

import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Fachada sobre o JWT da requisicao.
 *
 * <p>Existe para que services e resources nunca precisem mexer em claim solto:
 * pedem {@code tenantId()}, {@code usuarioId()} ou {@code role()} e recebem
 * valores ja validados. Se o token nao trouxer o que a API exige, estoura 401
 * em vez de deixar um {@code null} circular pelo dominio.
 */
@RequestScoped
public class UsuarioLogado {

    @Inject
    JsonWebToken jwt;

    public boolean autenticado() {
        return jwt != null && jwt.getRawToken() != null && jwt.getClaimNames() != null;
    }

    public UUID tenantId() {
        return uuidObrigatorio(ClaimsStewer.TENANT_ID, jwt.getClaim(ClaimsStewer.TENANT_ID));
    }

    public UUID usuarioId() {
        String sub = jwt.getSubject();
        if (sub == null || sub.isBlank()) {
            throw new AutenticacaoException("Token sem identificacao do usuario");
        }
        return uuidObrigatorio("sub", sub);
    }

    public String email() {
        return jwt.getClaim("upn");
    }

    public String nome() {
        return jwt.getClaim(ClaimsStewer.NOME);
    }

    public Role role() {
        String bruto = jwt.getClaim(ClaimsStewer.ROLE);
        if (bruto == null) {
            throw new AutenticacaoException("Token sem papel de acesso");
        }
        try {
            return Role.valueOf(bruto);
        } catch (IllegalArgumentException e) {
            throw new AutenticacaoException("Papel de acesso desconhecido: " + bruto);
        }
    }

    public boolean temRole(Role esperada) {
        return role() == esperada;
    }

    public boolean isAdmin() {
        return temRole(Role.ADMIN);
    }

    /**
     * Garante que quem chamou e ADMIN ou o proprio dono do recurso.
     * Usado nos endpoints em que o profissional so pode mexer na propria agenda.
     */
    public void exigirAdminOuDono(UUID usuarioDonoId) {
        if (isAdmin()) {
            return;
        }
        if (!usuarioId().equals(usuarioDonoId)) {
            throw new AcessoNegadoException("Voce so pode alterar os proprios dados");
        }
    }

    private static UUID uuidObrigatorio(String claim, Object valor) {
        if (valor == null) {
            throw new AutenticacaoException("Token sem o claim obrigatorio: " + claim);
        }
        try {
            return UUID.fromString(valor.toString());
        } catch (IllegalArgumentException e) {
            throw new AutenticacaoException("Claim " + claim + " nao e um UUID valido");
        }
    }
}
