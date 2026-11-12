-- =====================================================================
-- V7 - celulares que recebem notificacao (Expo Push)
--
-- Um usuario pode ter mais de um aparelho; um token pertence a um usuario so
-- (se outra pessoa entrar no mesmo celular, o token muda de dono).
-- Aditiva: so cria tabela.
-- =====================================================================

CREATE TABLE IF NOT EXISTS dispositivo (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid         NOT NULL REFERENCES tenant (id),
    usuario_id  uuid         NOT NULL REFERENCES usuario (id),
    token       varchar(255) NOT NULL,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz  NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dispositivo_token
    ON dispositivo (token) WHERE deleted_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_dispositivo_usuario ON dispositivo (usuario_id);
