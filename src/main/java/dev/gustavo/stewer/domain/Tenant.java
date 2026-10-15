package dev.gustavo.stewer.domain;

import java.time.ZoneId;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Estabelecimento. Um por enquanto, mas o schema ja e multi-tenant. */
@Entity
@Table(name = "tenant")
public class Tenant extends BaseEntity {

    /**
     * Fuso usado quando o estabelecimento nao diz outro.
     *
     * <p>Palmas (TO), onde fica o estudio. Nao e {@code America/Sao_Paulo} de
     * proposito: se o horario de verao voltar, Sao Paulo muda de offset e
     * Araguaina nao, e a agenda daqui sairia uma hora errada metade do ano.
     */
    public static final ZoneId FUSO_PADRAO = ZoneId.of("America/Araguaina");

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    /**
     * ZoneId IANA em que a hora de PAREDE da grade semanal e interpretada.
     *
     * <p>Guardado como texto (e nao como offset fixo) porque offset muda com o
     * calendario -- um pais que adote horario de verao passa a ter dois. O
     * {@code ZoneId} resolve o offset correto para cada data.
     */
    @Column(name = "fuso", nullable = false, length = 64)
    private String fuso = FUSO_PADRAO.getId();

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

    public String getFuso() {
        return fuso;
    }

    public void setFuso(String fuso) {
        this.fuso = fuso;
    }

    /**
     * O fuso ja resolvido. Cai no padrao quando a coluna esta vazia ou guarda
     * um identificador que o Java nao conhece -- a agenda continua de pe com o
     * fuso de casa em vez de estourar no meio da requisicao.
     */
    public ZoneId zoneId() {
        if (fuso == null || fuso.isBlank()) {
            return FUSO_PADRAO;
        }
        try {
            return ZoneId.of(fuso.trim());
        } catch (RuntimeException e) {
            return FUSO_PADRAO;
        }
    }
}
