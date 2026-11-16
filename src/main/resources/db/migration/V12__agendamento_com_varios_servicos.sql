-- =====================================================================
-- V12 - um agendamento pode ter mais de um servico
--
-- Ate aqui cada agendamento apontava para UM servico (agendamento.servico_id)
-- e o cliente que queria corte e barba tinha de marcar duas vezes. Agora o
-- agendamento guarda a lista de servicos aqui, em ordem, com a duracao e o
-- preco de cada um no dia em que foi marcado.
--
-- agendamento.servico_id continua existindo e passa a ser o PRIMEIRO servico
-- da lista: quem ainda le so ele (app antigo, relatorio) continua funcionando.
-- agendamento.preco_centavos passa a ser o TOTAL da lista, e o intervalo
-- [inicio, fim) a soma das duracoes -- a restricao de sobreposicao da V4
-- continua valendo sem mudar nada.
--
-- Aditiva: tabela nova e copia dos dados que ja existem, nenhum DROP.
-- =====================================================================

CREATE TABLE agendamento_servico (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    agendamento_id   uuid        NOT NULL REFERENCES agendamento (id),
    servico_id       uuid        NOT NULL REFERENCES servico (id),
    ordem            smallint    NOT NULL,
    duracao_minutos  integer     NOT NULL,
    preco_centavos   bigint      NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz NULL,
    CONSTRAINT ck_agendamento_servico_ordem   CHECK (ordem >= 0),
    CONSTRAINT ck_agendamento_servico_duracao CHECK (duracao_minutos > 0),
    CONSTRAINT ck_agendamento_servico_preco   CHECK (preco_centavos >= 0),
    CONSTRAINT ux_agendamento_servico_ordem   UNIQUE (agendamento_id, ordem),
    CONSTRAINT ux_agendamento_servico_unico   UNIQUE (agendamento_id, servico_id)
);

CREATE INDEX ix_agendamento_servico_tenant ON agendamento_servico (tenant_id);
CREATE INDEX ix_agendamento_servico_servico ON agendamento_servico (servico_id);

-- Todo agendamento que ja existe vira uma lista de um item so. A duracao sai
-- do proprio intervalo (e o que de fato foi reservado), o preco do valor
-- guardado no dia da marcacao, ou do preco atual quando nao havia.
INSERT INTO agendamento_servico (tenant_id, agendamento_id, servico_id, ordem,
                                 duracao_minutos, preco_centavos)
SELECT a.tenant_id,
       a.id,
       a.servico_id,
       0,
       GREATEST(1, ROUND(EXTRACT(EPOCH FROM (a.fim - a.inicio)) / 60)::integer),
       COALESCE(a.preco_centavos, s.preco_centavos)
FROM agendamento a
JOIN servico s ON s.id = a.servico_id;

COMMENT ON TABLE agendamento_servico IS
    'Servicos de um agendamento, em ordem. agendamento.servico_id e o de ordem 0; '
    'agendamento.preco_centavos e a soma dos precos daqui.';
