package dev.gustavo.stewer.domain;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * O que o estudio sabe de um cliente alem do cadastro: aniversario, como ele
 * chegou, etiquetas livres (VIP, indica amigos...) e o perfil de visagismo,
 * que e o que o profissional consulta antes de pegar na tesoura.
 *
 * <p>Uma por cliente (1:1). Nasce na primeira vez que alguem da equipe salva.
 */
@Entity
@Table(name = "ficha_cliente")
public class FichaCliente extends TenantOwnedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "origem", length = 40)
    private String origem;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "etiquetas", nullable = false, columnDefinition = "varchar(40)[]")
    private List<String> etiquetas = new ArrayList<>();

    @Column(name = "formato_rosto", length = 40)
    private String formatoRosto;

    @Column(name = "cabelo", length = 80)
    private String cabelo;

    @Column(name = "barba", length = 80)
    private String barba;

    @Column(name = "estilo", length = 120)
    private String estilo;

    @Column(name = "manter", length = 1000)
    private String manter;

    @Column(name = "evitar", length = 1000)
    private String evitar;

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public List<String> getEtiquetas() {
        return etiquetas;
    }

    public void setEtiquetas(List<String> etiquetas) {
        this.etiquetas = etiquetas;
    }

    public String getFormatoRosto() {
        return formatoRosto;
    }

    public void setFormatoRosto(String formatoRosto) {
        this.formatoRosto = formatoRosto;
    }

    public String getCabelo() {
        return cabelo;
    }

    public void setCabelo(String cabelo) {
        this.cabelo = cabelo;
    }

    public String getBarba() {
        return barba;
    }

    public void setBarba(String barba) {
        this.barba = barba;
    }

    public String getEstilo() {
        return estilo;
    }

    public void setEstilo(String estilo) {
        this.estilo = estilo;
    }

    public String getManter() {
        return manter;
    }

    public void setManter(String manter) {
        this.manter = manter;
    }

    public String getEvitar() {
        return evitar;
    }

    public void setEvitar(String evitar) {
        this.evitar = evitar;
    }
}
