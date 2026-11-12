package dev.gustavo.stewer.service;

import java.util.UUID;

/**
 * Algo mudou na agenda. Disparado dentro da transacao; quem avisa pessoas
 * (notificacao no celular) observa so depois do commit, para nunca avisar
 * sobre um agendamento que acabou desfeito por rollback.
 *
 * @param autorId quem fez a mudanca: o aviso vai para o outro lado
 */
public record AgendamentoEvento(UUID agendamentoId, Tipo tipo, UUID autorId) {

    public enum Tipo {
        CRIADO,
        REMARCADO,
        CANCELADO
    }
}
