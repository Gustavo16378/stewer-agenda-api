package dev.gustavo.stewer.domain;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

/**
 * Reserva de um ou mais servicos com um profissional em uma janela de tempo.
 *
 * <p>{@code servico} e o primeiro da lista ({@link #getServicos()}); fica aqui
 * para quem so precisa de "um" servico continuar funcionando.
 */
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

    /**
     * Todos os servicos do atendimento, na ordem em que serao feitos.
     *
     * <p>BatchSize: a agenda e o relatorio carregam dezenas de agendamentos de
     * uma vez, e sem ele cada um buscaria a propria lista numa consulta.
     */
    @OneToMany(mappedBy = "agendamento", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    @OrderBy("ordem")
    @BatchSize(size = 64)
    private List<ServicoDoAgendamento> servicos = new ArrayList<>();

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

    public List<ServicoDoAgendamento> getServicos() {
        return servicos;
    }

    /**
     * Define os servicos do atendimento, nessa ordem. O primeiro vira o
     * {@code servico} principal e o preco guardado passa a ser a soma.
     */
    public void definirServicos(List<Servico> lista) {
        if (lista == null || lista.isEmpty()) {
            throw new IllegalArgumentException("Agendamento precisa de pelo menos um servico");
        }
        servicos.clear();
        for (int i = 0; i < lista.size(); i++) {
            servicos.add(ServicoDoAgendamento.de(this, lista.get(i), i));
        }
        servico = lista.get(0);
        precoCentavos = servicos.stream().mapToLong(ServicoDoAgendamento::getPrecoCentavos).sum();
    }

    /** "Corte + Barba": o nome para mostrar em agenda, aviso e auditoria. */
    public String nomeDosServicos() {
        if (servicos.isEmpty()) {
            return servico.getNome();
        }
        return servicos.stream().map(i -> i.getServico().getNome())
                .collect(Collectors.joining(" + "));
    }

    /** O tempo reservado na agenda: e o intervalo que foi de fato marcado. */
    public Duration duracao() {
        return Duration.between(inicio, fim);
    }
}
