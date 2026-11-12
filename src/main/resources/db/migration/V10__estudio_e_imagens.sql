-- =====================================================================
-- V10 - o estudio editavel pelo app
--
--   * estudio: endereco, mapa, horario e contatos que o cliente ve no app.
--     Uma linha por estabelecimento. Sem linha, o app usa o conteudo que
--     ja vem embutido nele;
--   * imagem_publica: fotos que qualquer um pode ver (retrato do Stewer,
--     fotos do trabalho, fotos de exemplo de cada servico). Ficam no banco
--     como as fotos dos clientes, mas sao servidas por URL propria, com
--     cache longo: o id muda a cada foto nova.
--
-- Aditiva: so cria tabelas.
-- =====================================================================

CREATE TABLE IF NOT EXISTS estudio (
    id           uuid          PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    uuid          NOT NULL REFERENCES tenant (id),
    nome_local   varchar(80)   NULL,
    descricao    varchar(120)  NULL,
    endereco     varchar(240)  NULL,
    regiao       varchar(80)   NULL,
    horario      varchar(120)  NULL,
    latitude     double precision NULL,
    longitude    double precision NULL,
    link_mapa    varchar(500)  NULL,
    whatsapp     varchar(30)   NULL,
    instagram    varchar(60)   NULL,
    site         varchar(200)  NULL,
    frase        varchar(240)  NULL,
    created_at   timestamptz   NOT NULL DEFAULT now(),
    updated_at   timestamptz   NOT NULL DEFAULT now(),
    deleted_at   timestamptz   NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_estudio_tenant
    ON estudio (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS imagem_publica (
    id             uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id      uuid         NOT NULL REFERENCES tenant (id),
    tipo           varchar(12)  NOT NULL,
    servico_id     uuid         NULL REFERENCES servico (id),
    ordem          integer      NOT NULL DEFAULT 0,
    legenda        varchar(200) NULL,
    tipo_conteudo  varchar(40)  NOT NULL,
    tamanho_bytes  integer      NOT NULL,
    conteudo       bytea        NOT NULL,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    deleted_at     timestamptz  NULL,
    CONSTRAINT ck_imagem_tipo CHECK (tipo IN ('RETRATO', 'TRABALHO', 'SERVICO')),
    CONSTRAINT ck_imagem_servico CHECK ((tipo = 'SERVICO') = (servico_id IS NOT NULL))
);

CREATE INDEX IF NOT EXISTS ix_imagem_publica_tipo
    ON imagem_publica (tenant_id, tipo, ordem) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_imagem_publica_servico
    ON imagem_publica (servico_id, ordem) WHERE deleted_at IS NULL AND servico_id IS NOT NULL;
