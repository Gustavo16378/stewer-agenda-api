package dev.gustavo.stewer.domain;

import java.time.Duration;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Um dos servicos de um agendamento.
 *
 * <p>Duracao e preco sao copiados do servico no momento da marcacao: mudar o
 * catalogo depois nao reescreve o que o cliente reservou nem quanto vai pagar.
 */
@Entity
@Table(name = "agendamento_servico")
public class ServicoDoAgendamento extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "agendamento_id", nullable = false, updatable = false)
    private Agendamento agendamento;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "servico_id", nullable = false, updatable = false)
    private Servico servico;

    @Column(name = "ordem", nullable = false)
    private short ordem;

    @Column(name = "duracao_minutos", nullable = false)
    private int duracaoMinutos;

    @Column(name = "preco_centavos", nullable = false)
    private long precoCentavos;

    public static ServicoDoAgendamento de(Agendamento agendamento, Servico servico, int ordem) {
        ServicoDoAgendamento item = new ServicoDoAgendamento();
        item.setTenantId(agendamento.getTenantId());
        item.agendamento = agendamento;
        item.servico = servico;
        item.ordem = (short) ordem;
        item.duracaoMinutos = servico.getDuracaoMinutos();
        item.precoCentavos = servico.getPrecoCentavos();
        return item;
    }

    public Agendamento getAgendamento() {
        return agendamento;
    }

    public Servico getServico() {
        return servico;
    }

    public short getOrdem() {
        return ordem;
    }

    public int getDuracaoMinutos() {
        return duracaoMinutos;
    }

    public long getPrecoCentavos() {
        return precoCentavos;
    }

    public Duration duracao() {
        return Duration.ofMinutes(duracaoMinutos);
    }
}
