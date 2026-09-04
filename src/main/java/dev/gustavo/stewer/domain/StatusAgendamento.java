package dev.gustavo.stewer.domain;

/** Ciclo de vida de um agendamento. */
public enum StatusAgendamento {

    /** Reservado e ocupando a agenda do profissional. */
    AGENDADO,
    /** Atendimento realizado. */
    CONCLUIDO,
    /** Desmarcado; libera o horario. */
    CANCELADO,
    /** Cliente nao apareceu; libera o horario. */
    NAO_COMPARECEU;

    /** Somente o status AGENDADO ocupa slot no calculo de horarios livres. */
    public boolean ocupaAgenda() {
        return this == AGENDADO;
    }

    public boolean isFinal() {
        return this != AGENDADO;
    }
}
