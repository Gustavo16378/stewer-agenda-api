-- =====================================================================
-- Seed de DESENVOLVIMENTO.
--
-- Este arquivo vive em db/seed e so entra no classpath de migrations do
-- profile dev (%dev.quarkus.flyway.locations). Em test e prod o Flyway
-- enxerga apenas db/migration, entao a V2 nunca e aplicada la.
--
-- Senha de TODOS os usuarios: dev12345
-- Hash BCrypt (custo 10, salt fixo para o checksum da migration ser estavel).
-- =====================================================================

INSERT INTO tenant (id, nome, slug, ativo)
VALUES ('11111111-1111-1111-1111-111111111111', 'Stewer Gabriel', 'stewer-gabriel', true)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- usuarios (senha: dev12345)
-- ---------------------------------------------------------------------
INSERT INTO usuario (id, tenant_id, nome, email, telefone, senha_hash, role)
VALUES
    ('22222222-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Gabriel Admin', 'admin@stewer.dev', '+5511999990001',
     '$2a$10$S1Pjb0TwOUbjZkPfPET0GO2XaZS8//YwbiyMnC5cHEqg9hqTkKbAO', 'ADMIN'),
    ('22222222-0000-0000-0000-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'Stewer Gabriel', 'stewer@stewer.dev', '+5511999990002',
     '$2a$10$S1Pjb0TwOUbjZkPfPET0GO2XaZS8//YwbiyMnC5cHEqg9hqTkKbAO', 'PROFISSIONAL'),
    ('22222222-0000-0000-0000-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'Joana Ribeiro', 'joana@exemplo.dev', '+5511999990003',
     '$2a$10$S1Pjb0TwOUbjZkPfPET0GO2XaZS8//YwbiyMnC5cHEqg9hqTkKbAO', 'CLIENTE'),
    ('22222222-0000-0000-0000-000000000004',
     '11111111-1111-1111-1111-111111111111',
     'Marcos Tavares', 'marcos@exemplo.dev', '+5511999990004',
     '$2a$10$S1Pjb0TwOUbjZkPfPET0GO2XaZS8//YwbiyMnC5cHEqg9hqTkKbAO', 'CLIENTE')
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- profissional
-- ---------------------------------------------------------------------
INSERT INTO profissional (id, tenant_id, usuario_id, especialidade, bio, foto_url, ativo)
VALUES ('33333333-0000-0000-0000-000000000001',
        '11111111-1111-1111-1111-111111111111',
        '22222222-0000-0000-0000-000000000002',
        'Visagismo',
        'Visagista e consultor de imagem. Trabalha formato de rosto, proporcao e identidade antes da tesoura.',
        NULL, true)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- servicos
-- ---------------------------------------------------------------------
INSERT INTO servico (id, tenant_id, nome, descricao, duracao_minutos, preco_centavos, ativo)
VALUES
    ('44444444-0000-0000-0000-000000000001',
     '11111111-1111-1111-1111-111111111111',
     'Corte + Visagismo',
     'Analise de formato de rosto e corte desenhado para a estrutura do cliente.',
     60, 18000, true),
    ('44444444-0000-0000-0000-000000000002',
     '11111111-1111-1111-1111-111111111111',
     'Consultoria de Imagem',
     'Sessao completa: coloracao pessoal, estilo, barba, sobrancelha e orientacao de manutencao.',
     90, 32000, true),
    ('44444444-0000-0000-0000-000000000003',
     '11111111-1111-1111-1111-111111111111',
     'Barba',
     'Desenho e alinhamento de barba com toalha quente.',
     30, 7000, true)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------
-- disponibilidade: segunda a sabado, 09:00 as 19:00
-- (dia_semana: 0 = domingo ... 6 = sabado)
-- ---------------------------------------------------------------------
INSERT INTO disponibilidade (id, tenant_id, profissional_id, dia_semana, hora_inicio, hora_fim)
VALUES
    ('55555555-0000-0000-0000-000000000001', '11111111-1111-1111-1111-111111111111',
     '33333333-0000-0000-0000-000000000001', 1, '09:00', '19:00'),
    ('55555555-0000-0000-0000-000000000002', '11111111-1111-1111-1111-111111111111',
     '33333333-0000-0000-0000-000000000001', 2, '09:00', '19:00'),
    ('55555555-0000-0000-0000-000000000003', '11111111-1111-1111-1111-111111111111',
     '33333333-0000-0000-0000-000000000001', 3, '09:00', '19:00'),
    ('55555555-0000-0000-0000-000000000004', '11111111-1111-1111-1111-111111111111',
     '33333333-0000-0000-0000-000000000001', 4, '09:00', '19:00'),
    ('55555555-0000-0000-0000-000000000005', '11111111-1111-1111-1111-111111111111',
     '33333333-0000-0000-0000-000000000001', 5, '09:00', '19:00'),
    ('55555555-0000-0000-0000-000000000006', '11111111-1111-1111-1111-111111111111',
     '33333333-0000-0000-0000-000000000001', 6, '09:00', '19:00')
ON CONFLICT (id) DO NOTHING;
