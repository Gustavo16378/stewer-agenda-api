-- =====================================================================
-- V11 - foto propria do profissional
--
-- A ficha da equipe usa a foto de perfil da conta. Esta coluna guarda uma foto
-- diferente, quando o admin quer outra so para a equipe (a que o cliente ve na
-- hora de escolher com quem marcar). Ela mora em imagem_publica, tipo
-- PROFISSIONAL, servida pela mesma URL publica das fotos do estudio.
-- Aditiva: so acrescenta coluna e alarga o CHECK do tipo.
-- =====================================================================

ALTER TABLE profissional ADD COLUMN IF NOT EXISTS foto_imagem_id uuid NULL;

ALTER TABLE imagem_publica DROP CONSTRAINT IF EXISTS ck_imagem_tipo;
ALTER TABLE imagem_publica ADD CONSTRAINT ck_imagem_tipo
    CHECK (tipo IN ('RETRATO', 'TRABALHO', 'SERVICO', 'PROFISSIONAL'));
