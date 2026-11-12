package dev.gustavo.stewer.service;

import java.util.UUID;

/**
 * Algo mudou na agenda. Disparado dentro da transacao; quem avisa pessoas
 * (e-mail, notificacao no celular) observa so depois do commit, para nunca
 * avisar sobre um agendamento que acabou desfeito por rollback.
 */
public record AgendamentoEvento(UUID agendamentoId, Tipo tipo) {

    public enum Tipo {
        CRIADO,
        REMARCADO,
        CANCELADO
    }
}
