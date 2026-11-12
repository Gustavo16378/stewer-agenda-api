-- =====================================================================
-- V9 - foto de perfil da conta
--
-- O cliente e a equipe podem por uma foto na propria conta. A do profissional
-- aparece para o cliente na hora de escolher com quem marcar.
-- Aditiva: so acrescenta colunas.
-- =====================================================================

ALTER TABLE usuario ADD COLUMN IF NOT EXISTS foto bytea NULL;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS foto_tipo varchar(40) NULL;
ALTER TABLE usuario ADD COLUMN IF NOT EXISTS foto_atualizada_em timestamptz NULL;
