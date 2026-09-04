package dev.gustavo.stewer.domain;

/** Papeis de acesso. Viram o claim {@code groups} do JWT. */
public enum Role {
    CLIENTE,
    PROFISSIONAL,
    ADMIN;

    public static final String CLIENTE_STR = "CLIENTE";
    public static final String PROFISSIONAL_STR = "PROFISSIONAL";
    public static final String ADMIN_STR = "ADMIN";
}
