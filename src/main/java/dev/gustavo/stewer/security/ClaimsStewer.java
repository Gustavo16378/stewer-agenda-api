package dev.gustavo.stewer.security;

/** Claims proprios da aplicacao, alem dos padroes do JWT. */
public final class ClaimsStewer {

    /** UUID do tenant dono da sessao. */
    public static final String TENANT_ID = "tenantId";

    /** Papel do usuario (tambem vai em "groups", que e o que o @RolesAllowed le). */
    public static final String ROLE = "role";

    /** Nome de exibicao, so para o front nao precisar de outra chamada. */
    public static final String NOME = "nome";

    private ClaimsStewer() {
    }
}
