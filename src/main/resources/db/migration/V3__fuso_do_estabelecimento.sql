-- =====================================================================
-- V3 - fuso horario do estabelecimento
--
-- A grade semanal (disponibilidade.hora_inicio / hora_fim) e hora de PAREDE:
-- "09:00" significa nove da manha no relogio de quem atende. Ate aqui a API
-- montava o instante com atOffset(UTC), entao 09:00 virava 09:00Z -- seis da
-- manha num estabelecimento em UTC-3. Esta coluna da a cada tenant o fuso em
-- que a hora de parede dele deve ser interpretada.
--
-- Padrao America/Araguaina: o estudio fica em Palmas (TO). NAO usar
-- America/Sao_Paulo -- se o horario de verao brasileiro voltar, so Sao Paulo
-- muda de offset e a agenda de Palmas sairia errada metade do ano.
--
-- Aditiva: coluna nova com DEFAULT, nenhum DROP, nenhum ALTER destrutivo.
-- Os tenants que ja existem herdam o default.
-- =====================================================================

ALTER TABLE tenant
    ADD COLUMN IF NOT EXISTS fuso varchar(64) NOT NULL DEFAULT 'America/Araguaina';

COMMENT ON COLUMN tenant.fuso IS
    'ZoneId IANA em que a hora de parede da grade semanal e interpretada (ex.: America/Araguaina).';
