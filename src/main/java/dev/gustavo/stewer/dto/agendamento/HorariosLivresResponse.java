package dev.gustavo.stewer.dto.agendamento;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Resultado da consulta de horarios livres de um dia.
 *
 * <p>Devolve o contexto do calculo (dia, profissional, servico e duracao)
 * junto com a lista: assim a tela consegue renderizar tudo sem guardar o que
 * mandou na query, e a resposta continua legivel num log.
 *
 * <p>Dia sem grade nao e erro: vem {@code horarios} vazio.
 */
@Schema(name = "HorariosLivres", description = "Slots livres de um profissional em um dia")
public record HorariosLivresResponse(
        LocalDate data,
        UUID profissionalId,
        UUID servicoId,
        int duracaoMinutos,
        List<HorarioLivreResponse> horarios) {
}
