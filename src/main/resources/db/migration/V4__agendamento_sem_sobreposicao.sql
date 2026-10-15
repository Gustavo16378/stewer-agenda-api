-- =====================================================================
-- V4 - um profissional nao pode ter dois atendimentos no mesmo horario
--
-- Ate aqui a regra existia so no codigo (AgendamentoService.criar consulta
-- existeSobreposicao e depois insere). Entre a consulta e o insert cabe a
-- transacao do vizinho: em READ COMMITTED as duas leem o estado de antes,
-- nenhuma enxerga a outra, e as duas inserem. Oito pedidos simultaneos no
-- mesmo slot ja produziram oito agendamentos.
--
-- A unica forma de fechar isso e no banco. A restricao abaixo torna a
-- sobreposicao impossivel por construcao, independente de ordem de execucao,
-- de numero de instancias da API e de nivel de isolamento.
--
-- Aditiva: so acrescenta extensao e constraint, nao altera nem apaga dado.
-- =====================================================================

-- GiST sozinho nao indexa uuid com o operador "=", entao nao daria para
-- combinar profissional_id com o intervalo numa mesma restricao. btree_gist
-- acrescenta justamente essas classes de operador.
CREATE EXTENSION IF NOT EXISTS btree_gist;

-- Intervalo MEIO-ABERTO [inicio, fim), igual a regra que o codigo ja usa:
-- atendimento que termina 10:00 e outro que comeca 10:00 nao conflitam.
-- tstzrange(a, b) e meio-aberto por padrao, entao os dois lados concordam.
--
-- O WHERE restringe a restricao ao que de fato ocupa a agenda: so AGENDADO
-- vivo. CANCELADO, CONCLUIDO e NAO_COMPARECEU liberam o horario -- e um
-- agendamento soft-deletado tambem.
ALTER TABLE agendamento
    ADD CONSTRAINT ux_agendamento_sem_sobreposicao
    EXCLUDE USING gist (
        profissional_id WITH =,
        tstzrange(inicio, fim) WITH &&
    )
    WHERE (status = 'AGENDADO' AND deleted_at IS NULL);

COMMENT ON CONSTRAINT ux_agendamento_sem_sobreposicao ON agendamento IS
    'Dois atendimentos AGENDADO vivos do mesmo profissional nao podem se sobrepor. '
    'Violacao (SQLSTATE 23P01) vira 409 HORARIO_OCUPADO em AgendamentoService.';
