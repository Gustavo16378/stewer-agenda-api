package dev.gustavo.stewer.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Uma faixa da grade semanal recorrente de um profissional.
 *
 * <p>{@code diaSemana} segue a convencao do enunciado: 0 = domingo ...
 * 6 = sabado. A conversao para {@link DayOfWeek} (1 = segunda ... 7 = domingo)
 * fica isolada aqui para nao vazar pelo resto do codigo.
 */
@Entity
@Table(name = "disponibilidade")
public class Disponibilidade extends TenantOwnedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profissional_id", nullable = false)
    private Profissional profissional;

    @Column(name = "dia_semana", nullable = false)
    private short diaSemana;

    @Column(name = "hora_inicio", nullable = false)
    private LocalTime horaInicio;

    @Column(name = "hora_fim", nullable = false)
    private LocalTime horaFim;

    /** Converte um {@link DayOfWeek} para a convencao 0..6 (domingo..sabado). */
    public static short toDiaSemana(DayOfWeek dayOfWeek) {
        return (short) (dayOfWeek.getValue() % 7);
    }

    public DayOfWeek diaDaSemana() {
        return diaSemana == 0 ? DayOfWeek.SUNDAY : DayOfWeek.of(diaSemana);
    }

    public Profissional getProfissional() {
        return profissional;
    }

    public void setProfissional(Profissional profissional) {
        this.profissional = profissional;
    }

    public short getDiaSemana() {
        return diaSemana;
    }

    public void setDiaSemana(short diaSemana) {
        this.diaSemana = diaSemana;
    }

    public LocalTime getHoraInicio() {
        return horaInicio;
    }

    public void setHoraInicio(LocalTime horaInicio) {
        this.horaInicio = horaInicio;
    }

    public LocalTime getHoraFim() {
        return horaFim;
    }

    public void setHoraFim(LocalTime horaFim) {
        this.horaFim = horaFim;
    }
}
