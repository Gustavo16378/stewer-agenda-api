package dev.gustavo.stewer.domain;

import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Servico oferecido pelo estudio (corte, consultoria de imagem, barba...). */
@Entity
@Table(name = "servico")
public class Servico extends TenantOwnedEntity {

    @Column(name = "nome", nullable = false, length = 140)
    private String nome;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    /** Preco em centavos: evita qualquer surpresa de arredondamento. */
    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo = true;

    public Duration duracao() {
        return Duration.ofMinutes(duracaoMinutos);
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public void setDuracaoMinutos(int duracaoMinutos) {
        this.duracaoMinutos = duracaoMinutos;
    }

    public long getPrecoCentavos() {
        return precoCentavos;
    }

    public void setPrecoCentavos(long precoCentavos) {
        this.precoCentavos = precoCentavos;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }
}
