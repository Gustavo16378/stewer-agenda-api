-- =====================================================================
-- V5 - nome de servico unico por estabelecimento
--
-- A regra existia so no codigo (ServicoService.exigirNomeLivre consulta e
-- depois insere). Duas criacoes simultaneas leem o mesmo "nome livre" e as
-- duas inserem: oito pedidos no mesmo instante ja produziram dois servicos
-- "Corte + Visagismo" no catalogo, indistinguiveis na tela do cliente.
--
-- Mesma forma do indice que ja protege o e-mail do usuario
-- (ux_usuario_tenant_email): lower() para "Barba" e "barba" colidirem, e
-- parcial em deleted_at IS NULL para que remover um servico devolva o nome.
--
-- Aditiva: so acrescenta indice.
-- =====================================================================

CREATE UNIQUE INDEX IF NOT EXISTS ux_servico_tenant_nome
    ON servico (tenant_id, lower(nome))
    WHERE deleted_at IS NULL;

COMMENT ON INDEX ux_servico_tenant_nome IS
    'Nome de servico unico entre os vivos do mesmo estabelecimento. '
    'Violacao (SQLSTATE 23505) vira 409 NOME_EM_USO em ServicoService.';
