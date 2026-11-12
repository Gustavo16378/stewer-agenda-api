package dev.gustavo.stewer.domain;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Foto que qualquer um pode ver: o retrato do Stewer, as fotos do trabalho e as fotos de
 * exemplo de cada servico. Servida por URL propria (GET /imagens/{id}) com cache longo.
 */
@Entity
@Table(name = "imagem_publica")
public class ImagemPublica extends TenantOwnedEntity {

    public enum Tipo {
        RETRATO,
        TRABALHO,
        SERVICO
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 12)
    private Tipo tipo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_id")
    private Servico servico;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "legenda", length = 200)
    private String legenda;

    @Column(name = "tipo_conteudo", nullable = false, length = 40)
    private String tipoConteudo;

    @Column(name = "tamanho_bytes", nullable = false)
    private int tamanhoBytes;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "conteudo", nullable = false)
    private byte[] conteudo;

    public Tipo getTipo() {
        return tipo;
    }

    public void setTipo(Tipo tipo) {
        this.tipo = tipo;
    }

    public Servico getServico() {
        return servico;
    }

    public void setServico(Servico servico) {
        this.servico = servico;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public String getLegenda() {
        return legenda;
    }

    public void setLegenda(String legenda) {
        this.legenda = legenda;
    }

    public String getTipoConteudo() {
        return tipoConteudo;
    }

    public void setTipoConteudo(String tipoConteudo) {
        this.tipoConteudo = tipoConteudo;
    }

    public int getTamanhoBytes() {
        return tamanhoBytes;
    }

    public void setTamanhoBytes(int tamanhoBytes) {
        this.tamanhoBytes = tamanhoBytes;
    }

    public byte[] getConteudo() {
        return conteudo;
    }

    public void setConteudo(byte[] conteudo) {
        this.conteudo = conteudo;
    }
}
