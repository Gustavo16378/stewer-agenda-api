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
 * Foto do cliente (antes, depois ou outra), opcionalmente presa a um
 * atendimento. O conteudo vai no proprio banco: o app reduz a imagem antes de
 * subir, e o volume de um estudio cabe com folga no plano gratuito.
 */
@Entity
@Table(name = "foto_cliente")
public class FotoCliente extends TenantOwnedEntity {

    public enum Momento {
        ANTES,
        DEPOIS,
        OUTRA
    }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agendamento_id")
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "autor_id")
    private Usuario autor;

    @Enumerated(EnumType.STRING)
    @Column(name = "momento", nullable = false, length = 10)
    private Momento momento;

    @Column(name = "legenda", length = 200)
    private String legenda;

    @Column(name = "tipo_conteudo", nullable = false, length = 40)
    private String tipoConteudo;

    @Column(name = "tamanho_bytes", nullable = false)
    private int tamanhoBytes;

    /** Lazy: a lista de fotos nao carrega os bytes de todas de uma vez. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "conteudo", nullable = false)
    private byte[] conteudo;

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }

    public void setAgendamento(Agendamento agendamento) {
        this.agendamento = agendamento;
    }

    public Usuario getAutor() {
        return autor;
    }

    public void setAutor(Usuario autor) {
        this.autor = autor;
    }

    public Momento getMomento() {
        return momento;
    }

    public void setMomento(Momento momento) {
        this.momento = momento;
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
