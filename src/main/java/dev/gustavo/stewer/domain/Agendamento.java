package dev.gustavo.stewer.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Reserva de um servico com um profissional em uma janela de tempo. */
@Entity
@Table(name = "agendamento")
public class Agendamento extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Usuario cliente;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false)
    private Servico servico;

    @Column(name = "inicio", nullable = false)
    private OffsetDateTime inicio;

    @Column(name = "fim", nullable = false)
    private OffsetDateTime fim;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusAgendamento status = StatusAgendamento.AGENDADO;

    @Column(name = "observacao")
    private String observacao;

    /**
     * Preco do servico no dia em que foi marcado. Mudar o preco depois nao
     * reescreve quanto o cliente ja gastou. Nulo so em registro antigo sem
     * migrar; quem le cai no preco atual do servico.
     */
    @Column(name = "preco_centavos")
    private Long precoCentavos;

    /** Quando o lembrete por e-mail saiu; nulo enquanto nao saiu. Remarcar zera. */
    @Column(name = "lembrete_enviado_em")
    private OffsetDateTime lembreteEnviadoEm;

    public OffsetDateTime getLembreteEnviadoEm() {
        return lembreteEnviadoEm;
    }

    public void setLembreteEnviadoEm(OffsetDateTime lembreteEnviadoEm) {
        this.lembreteEnviadoEm = lembreteEnviadoEm;
    }

    public Usuario getCliente() {
        return cliente;
    }

    public void setCliente(Usuario cliente) {
        this.cliente = cliente;
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public void setProfissional(Profissional profissional) {
        this.profissional = profissional;
    }

    public Servico getServico() {
        return servico;
    }

    public void setServico(Servico servico) {
        this.servico = servico;
    }

    public OffsetDateTime getInicio() {
        return inicio;
    }

    public void setInicio(OffsetDateTime inicio) {
        this.inicio = inicio;
    }

    public OffsetDateTime getFim() {
        return fim;
    }

    public void setFim(OffsetDateTime fim) {
        this.fim = fim;
    }

    public StatusAgendamento getStatus() {
        return status;
    }

    public void setStatus(StatusAgendamento status) {
        this.status = status;
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    public Long getPrecoCentavos() {
        return precoCentavos;
    }

    public void setPrecoCentavos(Long precoCentavos) {
        this.precoCentavos = precoCentavos;
    }

    /** O valor que vale para este atendimento: o do dia da marcacao. */
    public long precoEfetivo() {
        return precoCentavos != null ? precoCentavos : servico.getPrecoCentavos();
    }
}
