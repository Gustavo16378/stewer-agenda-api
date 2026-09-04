package dev.gustavo.stewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Estabelecimento. Um por enquanto, mas o schema ja e multi-tenant. */
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getSlug() {
        return slug;
    }

    public void setSlug(String slug) {
        this.slug = slug;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
