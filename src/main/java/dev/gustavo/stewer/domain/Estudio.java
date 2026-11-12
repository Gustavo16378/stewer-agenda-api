package dev.gustavo.stewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * O que o cliente ve sobre o estudio: onde fica, quando abre, como falar. Uma linha por
 * estabelecimento; campo vazio faz o app cair no conteudo que ja vem embutido nele.
 */
@Entity
@Table(name = "estudio")
public class Estudio extends TenantOwnedEntity {

    @Column(name = "nome_local")
    private String nomeLocal;

    @Column(name = "descricao")
    private String descricao;

    @Column(name = "endereco")
    private String endereco;

    @Column(name = "regiao")
    private String regiao;

    @Column(name = "horario")
    private String horario;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "link_mapa")
    private String linkMapa;

    @Column(name = "whatsapp")
    private String whatsapp;

    @Column(name = "instagram")
    private String instagram;

    @Column(name = "site")
    private String site;

    @Column(name = "frase")
    private String frase;

    public String getNomeLocal() {
        return nomeLocal;
    }

    public void setNomeLocal(String nomeLocal) {
        this.nomeLocal = nomeLocal;
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getRegiao() {
        return regiao;
    }

    public void setRegiao(String regiao) {
        this.regiao = regiao;
    }

    public String getHorario() {
        return horario;
    }

    public void setHorario(String horario) {
        this.horario = horario;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getLinkMapa() {
        return linkMapa;
    }

    public void setLinkMapa(String linkMapa) {
        this.linkMapa = linkMapa;
    }

    public String getWhatsapp() {
        return whatsapp;
    }

    public void setWhatsapp(String whatsapp) {
        this.whatsapp = whatsapp;
    }

    public String getInstagram() {
        return instagram;
    }

    public void setInstagram(String instagram) {
        this.instagram = instagram;
    }

    public String getSite() {
        return site;
    }

    public void setSite(String site) {
        this.site = site;
    }

    public String getFrase() {
        return frase;
    }

    public void setFrase(String frase) {
        this.frase = frase;
    }
}
