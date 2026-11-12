-- =====================================================================
-- V6 - e-mail (redefinir senha, lembrete de horario) e exclusao de conta
--
-- Aditiva: so acrescenta colunas com default e uma tabela nova.
-- =====================================================================

-- O cliente escolhe se quer o lembrete por e-mail. Liga por padrao: e o que
-- reduz falta, e quem nao quer desliga no Perfil.
ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS lembrete_email boolean NOT NULL DEFAULT true;

-- Marca de envio do lembrete: o agendador roda de tempos em tempos e so
-- manda para quem ainda nao recebeu. Remarcar limpa a marca.
ALTER TABLE agendamento
    ADD COLUMN IF NOT EXISTS lembrete_enviado_em timestamptz NULL;

CREATE INDEX IF NOT EXISTS ix_agendamento_lembrete_pendente
    ON agendamento (inicio)
    WHERE status = 'AGENDADO' AND lembrete_enviado_em IS NULL AND deleted_at IS NULL;

-- Codigo de 6 digitos para redefinir a senha. Guardado so o hash: quem ler o
-- banco nao consegue usar um codigo valido.
CREATE TABLE IF NOT EXISTS codigo_redefinicao_senha (
    id          uuid         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id   uuid         NOT NULL REFERENCES tenant (id),
    usuario_id  uuid         NOT NULL REFERENCES usuario (id),
    codigo_hash varchar(120) NOT NULL,
    expira_em   timestamptz  NOT NULL,
    usado_em    timestamptz  NULL,
    tentativas  integer      NOT NULL DEFAULT 0,
    created_at  timestamptz  NOT NULL DEFAULT now(),
    updated_at  timestamptz  NOT NULL DEFAULT now(),
    deleted_at  timestamptz  NULL
);

CREATE INDEX IF NOT EXISTS ix_codigo_redefinicao_usuario
    ON codigo_redefinicao_senha (usuario_id, created_at DESC);
