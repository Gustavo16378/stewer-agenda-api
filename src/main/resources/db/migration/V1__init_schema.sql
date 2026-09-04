-- =====================================================================
-- Stewer Agenda API - schema inicial
--
-- Regras que valem para TODAS as migrations deste projeto:
--   * migrations sao SEMPRE aditivas (nunca DROP / ALTER destrutivo);
--   * toda tabela de negocio tem created_at, updated_at e deleted_at;
--   * soft delete: deleted_at NULL = registro vivo;
--   * IDs sao UUID; timestamps sao timestamptz (UTC).
-- =====================================================================

-- ---------------------------------------------------------------------
-- tenant
-- ---------------------------------------------------------------------
CREATE TABLE tenant (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    nome        varchar(120) NOT NULL,
    slug        varchar(80)  NOT NULL,
    ativo       boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz  NULL
);

-- slug unico entre os tenants vivos
CREATE UNIQUE INDEX ux_tenant_slug ON tenant (slug) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- usuario
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid         NOT NULL REFERENCES tenant (id),
    nome        varchar(120) NOT NULL,
    email       varchar(180) NOT NULL,
    telefone    varchar(30)  NULL,
    senha_hash  varchar(120) NOT NULL,
    role        varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz  NULL,
    CONSTRAINT ck_usuario_role CHECK (role IN ('CLIENTE', 'PROFISSIONAL', 'ADMIN'))
);

CREATE INDEX ix_usuario_tenant ON usuario (tenant_id);
-- email unico por tenant (case-insensitive), considerando apenas usuarios vivos
CREATE UNIQUE INDEX ux_usuario_tenant_email
    ON usuario (tenant_id, lower(email)) WHERE deleted_at IS NULL;
-- indice de apoio ao login / listagem de clientes
CREATE INDEX ix_usuario_tenant_email ON usuario (tenant_id, email);

-- ---------------------------------------------------------------------
-- profissional
-- ---------------------------------------------------------------------
CREATE TABLE profissional (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid         NOT NULL REFERENCES tenant (id),
    usuario_id     uuid         NOT NULL REFERENCES usuario (id),
    especialidade  varchar(120) NULL,
    bio            text         NULL,
    foto_url       varchar(500) NULL,
    ativo          boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL
);

CREATE INDEX ix_profissional_tenant ON profissional (tenant_id);
CREATE UNIQUE INDEX ux_profissional_usuario
    ON profissional (tenant_id, usuario_id) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------------
-- servico
-- ---------------------------------------------------------------------
CREATE TABLE servico (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    nome             varchar(140) NOT NULL,
    descricao        text         NULL,
    duracao_minutos  integer      NOT NULL,
    preco_centavos   bigint       NOT NULL,
    ativo            boolean      NOT NULL DEFAULT true,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz  NULL,
    CONSTRAINT ck_servico_duracao CHECK (duracao_minutos > 0),
    CONSTRAINT ck_servico_preco   CHECK (preco_centavos >= 0)
);

CREATE INDEX ix_servico_tenant ON servico (tenant_id);

-- ---------------------------------------------------------------------
-- disponibilidade (grade semanal recorrente do profissional)
-- ---------------------------------------------------------------------
CREATE TABLE disponibilidade (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    profissional_id  uuid        NOT NULL REFERENCES profissional (id),
    dia_semana       smallint    NOT NULL,
    hora_inicio      time        NOT NULL,
    hora_fim         time        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz NULL,
    CONSTRAINT ck_disponibilidade_dia   CHECK (dia_semana BETWEEN 0 AND 6),
    CONSTRAINT ck_disponibilidade_faixa CHECK (hora_fim > hora_inicio)
);

CREATE INDEX ix_disponibilidade_tenant ON disponibilidade (tenant_id);
CREATE INDEX ix_disponibilidade_prof_dia ON disponibilidade (profissional_id, dia_semana);

-- ---------------------------------------------------------------------
-- bloqueio (folgas / compromissos pontuais)
-- ---------------------------------------------------------------------
CREATE TABLE bloqueio (
    id               uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid         NOT NULL REFERENCES tenant (id),
    profissional_id  uuid         NOT NULL REFERENCES profissional (id),
    inicio           timestamptz  NOT NULL,
    fim              timestamptz  NOT NULL,
    motivo           varchar(240) NULL,
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    deleted_at       timestamptz  NULL,
    CONSTRAINT ck_bloqueio_faixa CHECK (fim > inicio)
);

CREATE INDEX ix_bloqueio_tenant ON bloqueio (tenant_id);
CREATE INDEX ix_bloqueio_prof_inicio ON bloqueio (profissional_id, inicio);

-- ---------------------------------------------------------------------
-- agendamento
-- ---------------------------------------------------------------------
CREATE TABLE agendamento (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    cliente_id       uuid        NOT NULL REFERENCES usuario (id),
    profissional_id  uuid        NOT NULL REFERENCES profissional (id),
    servico_id       uuid        NOT NULL REFERENCES servico (id),
    inicio           timestamptz NOT NULL,
    fim              timestamptz NOT NULL,
    status           varchar(20) NOT NULL DEFAULT 'AGENDADO',
    observacao       text        NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz NULL,
    CONSTRAINT ck_agendamento_faixa  CHECK (fim > inicio),
    CONSTRAINT ck_agendamento_status CHECK (status IN ('AGENDADO', 'CONCLUIDO', 'CANCELADO', 'NAO_COMPARECEU'))
);

CREATE INDEX ix_agendamento_tenant ON agendamento (tenant_id);
CREATE INDEX ix_agendamento_prof_inicio ON agendamento (profissional_id, inicio);
CREATE INDEX ix_agendamento_cliente_inicio ON agendamento (cliente_id, inicio);
-- apoio direto ao calculo de horarios livres / checagem de sobreposicao
CREATE INDEX ix_agendamento_prof_ativo
    ON agendamento (profissional_id, inicio, fim)
    WHERE deleted_at IS NULL AND status = 'AGENDADO';

-- ---------------------------------------------------------------------
-- anotacao_cliente (anotacoes de visagismo do profissional sobre o cliente)
-- ---------------------------------------------------------------------
CREATE TABLE anotacao_cliente (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id        uuid        NOT NULL REFERENCES tenant (id),
    cliente_id       uuid        NOT NULL REFERENCES usuario (id),
    profissional_id  uuid        NOT NULL REFERENCES profissional (id),
    agendamento_id   uuid        NULL REFERENCES agendamento (id),
    texto            text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    deleted_at       timestamptz NULL
);

CREATE INDEX ix_anotacao_tenant ON anotacao_cliente (tenant_id);
CREATE INDEX ix_anotacao_cliente ON anotacao_cliente (cliente_id, created_at DESC);
CREATE INDEX ix_anotacao_profissional ON anotacao_cliente (profissional_id);
