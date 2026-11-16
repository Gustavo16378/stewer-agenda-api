-- =====================================================================
-- V13 - cliente cadastrado pela equipe
--
-- O Stewer recebe pedido de horario pelo WhatsApp de quem nunca abriu o app:
-- tem o nome e o telefone, as vezes nem o e-mail. Para marcar o horario no
-- nome dessa pessoa, a equipe agora cadastra o cliente pelo painel.
--
-- Esse cliente nao tem conta para entrar: o e-mail pode faltar e a senha e
-- um valor aleatorio que ninguem conhece. Se um dia ele criar a conta pelo
-- app com o mesmo e-mail, a conta nova assume este cadastro (e o historico
-- dele) em vez de virar um cliente duplicado -- e por isso a marca abaixo.
--
-- Aditiva: so relaxa um NOT NULL e acrescenta coluna com DEFAULT.
-- =====================================================================

ALTER TABLE usuario ALTER COLUMN email DROP NOT NULL;

ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS cadastrado_pela_equipe boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN usuario.cadastrado_pela_equipe IS
    'Cliente que a equipe cadastrou e que ainda nao criou a propria conta. '
    'Registrar com o mesmo e-mail assume este cadastro.';
