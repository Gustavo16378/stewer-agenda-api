-- =====================================================================
-- V8 - conhecer e guardar a clientela
--
--   * ficha_cliente: o que o Stewer sabe de cada pessoa alem do cadastro
--     (aniversario, como conheceu, etiquetas e o perfil de visagismo);
--   * foto_cliente: antes e depois de cada atendimento, guardadas no banco
--     (o app reduz a foto antes de subir; ~150 KB cada);
--   * evento_auditoria: quem fez o que e quando. Somente insercao: nao tem
--     updated_at nem deleted_at, porque um registro de auditoria nao muda;
--   * agendamento.preco_centavos: o valor do servico no dia em que foi
--     marcado. Sem isso, mudar o preco do corte reescreveria quanto cada
--     cliente ja gastou.
--
-- Aditiva: so cria tabelas e uma coluna.
-- =====================================================================

CREATE TABLE IF NOT EXISTS ficha_cliente (
    id               uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid          NOT NULL REFERENCES tenant (id),
    cliente_id       uuid          NOT NULL REFERENCES usuario (id),
    data_nascimento  date          NULL,
    origem           varchar(40)   NULL,
    etiquetas        varchar(40)[] NOT NULL DEFAULT '{}',
    formato_rosto    varchar(40)   NULL,
    cabelo           varchar(80)   NULL,
    barba            varchar(80)   NULL,
    estilo           varchar(120)  NULL,
    manter           varchar(1000) NULL,
    evitar           varchar(1000) NULL,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    updated_at       timestamptz   NOT NULL DEFAULT now(),
    deleted_at       timestamptz   NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ficha_cliente
    ON ficha_cliente (tenant_id, cliente_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS foto_cliente (
    id              uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       uuid         NOT NULL REFERENCES tenant (id),
    cliente_id      uuid         NOT NULL REFERENCES usuario (id),
    agendamento_id  uuid         NULL REFERENCES agendamento (id),
    autor_id        uuid         NULL REFERENCES usuario (id),
    momento         varchar(10)  NOT NULL,
    legenda         varchar(200) NULL,
    tipo_conteudo   varchar(40)  NOT NULL,
    tamanho_bytes   integer      NOT NULL,
    conteudo        bytea        NOT NULL,
    created_at      timestamptz  NOT NULL DEFAULT now(),
    updated_at      timestamptz  NOT NULL DEFAULT now(),
    deleted_at      timestamptz  NULL,
    CONSTRAINT ck_foto_momento CHECK (momento IN ('ANTES', 'DEPOIS', 'OUTRA'))
);

CREATE INDEX IF NOT EXISTS ix_foto_cliente
    ON foto_cliente (tenant_id, cliente_id, created_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS evento_auditoria (
    id           uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid         NOT NULL REFERENCES tenant (id),
    criado_em    timestamptz  NOT NULL DEFAULT now(),
    autor_id     uuid         NULL,
    autor_nome   varchar(120) NULL,
    autor_role   varchar(20)  NULL,
    acao         varchar(40)  NOT NULL,
    entidade     varchar(30)  NOT NULL,
    entidade_id  uuid         NULL,
    cliente_id   uuid         NULL,
    resumo       varchar(400) NOT NULL
);

CREATE INDEX IF NOT EXISTS ix_auditoria_recentes
    ON evento_auditoria (tenant_id, criado_em DESC);
CREATE INDEX IF NOT EXISTS ix_auditoria_cliente
    ON evento_auditoria (tenant_id, cliente_id, criado_em DESC) WHERE cliente_id IS NOT NULL;

ALTER TABLE agendamento ADD COLUMN IF NOT EXISTS preco_centavos bigint NULL;

UPDATE agendamento a
   SET preco_centavos = s.preco_centavos
  FROM servico s
 WHERE s.id = a.servico_id AND a.preco_centavos IS NULL;
