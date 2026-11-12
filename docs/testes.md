# Plano e relatório de testes · API da agenda

Como a API é verificada, o que cada suíte cobre e como rodar. Os testes do app (telas,
jornadas no navegador, Lighthouse) ficam no repositório
[stewer-agenda-app](https://github.com/Gustavo16378/stewer-agenda-app), em `docs/testes.md`.

## Resumo

| suíte | técnica | o que roda | testes |
|---|---|---|---|
| Regras e recursos | caixa preta e caixa cinza | a API real (Quarkus) com PostgreSQL de verdade, pedido a pedido | 225 |
| Segurança | caixa branca, preta e cinza | matriz de acesso, tokens, injeção, dados dos outros, upload, cabeçalhos | 20 (centenas de chamadas) |
| Configuração | caixa branca | segredo do JWT, URL do banco, bootstrap do dono | 24 |
| Unitários | caixa branca | regras puras (métricas do cliente, leitura do link do mapa) | 4 |
| **Total automatizado** | | | **273** |
| Carga e estresse | caixa preta | autocannon contra a API empacotada, até 100 conexões | 7 cenários |

Cobertura (JaCoCo, na CI a cada push): **92% das linhas**, 94% dos métodos, 99% das
classes, 69% dos ramos.

## As três técnicas

**Caixa branca** conhece o código. Os testes de `config/` e `security/` exercitam cada
ramo da validação do segredo do JWT e da URL do banco sem subir a API.
[`MatrizDeAcessoTest`](../src/test/java/dev/gustavo/stewer/seguranca/MatrizDeAcessoTest.java)
lê as anotações de todos os endpoints (o que o código promete) para depois cobrar de fora.

**Caixa preta** só conhece a interface: manda o pedido HTTP e confere status e corpo. É a
maior parte de `resource/` e todo o teste de carga.

**Caixa cinza** faz o pedido pela API, como o app, e depois abre o banco para conferir o
que foi gravado: que a senha virou bcrypt, que a auditoria registrou quem fez, que o
e-mail foi guardado em minúsculas, que o soft delete deixou a linha com a data, que
nenhuma tabela mudou depois dos ataques de SQL injection, que o HTML de um nome foi
guardado como texto. Ficam em
[`CaixaCinzaTest`](../src/test/java/dev/gustavo/stewer/seguranca/CaixaCinzaTest.java),
[`InjecaoTest`](../src/test/java/dev/gustavo/stewer/seguranca/InjecaoTest.java) e em
vários testes de `resource/` (reserva concorrente, unicidade, soft delete).

## Segurança

Organizada pelo OWASP Top 10. O resumo por risco está em [`SECURITY.md`](../SECURITY.md).

| risco | teste | o que prova |
|---|---|---|
| A01 Controle de acesso | `MatrizDeAcessoTest` | todos os endpoints (mais de 50) respondem 401 sem token e 403 para o papel errado; os públicos são exatamente 7 |
| A01 Dados dos outros | `AcessoAosDadosDosOutrosTest` | cliente não remarca nem cancela o horário de outro, não vê a foto de outro; admin de outro estúdio não acha nada deste |
| A02 / A07 Autenticação | `TokensTest` | token adulterado, assinado com outra chave, de outro emissor, vencido ou com `alg: none` é recusado; senha errada não diz se o e-mail existe |
| A03 Injeção | `InjecaoTest` | 16 ataques (SQL, script, JNDI, template, caminho) na busca, no login e no id: nada de 500 e nenhuma tabela muda |
| A04 Upload | `UploadECabecalhosTest` | HTML e SVG disfarçados de JPEG, PNG ou WebP são recusados (a API confere os primeiros bytes) |
| A05 Configuração | `UploadECabecalhosTest` | `nosniff`, `DENY`, CSP, HSTS e `no-store` em toda resposta; CORS fechado para site de fora; erro sem pilha |
| A07 Força bruta | `SenhaEmailEContaTest` e a carga | cinco senhas erradas travam o login com 429; 60 tentativas seguidas, a trava segura e a API continua de pé |

## Carga e estresse

[`carga/carga.mjs`](../carga/carga.mjs) sobe pedidos com o autocannon contra a API
empacotada, com os dados de demonstração. Reprova com qualquer 5xx, conexão perdida,
latência acima do teto ou trava de login que não segure. Roda na CI (job `carga`) e o
relatório aparece no resumo do job.

Última rodada (máquina de desenvolvimento, 8 s por cenário):

| cenário | conexões | vazão | p50 | p97,5 | erros |
|---|---|---|---|---|---|
| ping | 20 | 12.800/s | 0 ms | 6 ms | 0 |
| estúdio (público, banco) | 20 | 509/s | 29 ms | 84 ms | 0 |
| catálogo de serviços | 20 | 994/s | 18 ms | 43 ms | 0 |
| meus horários | 20 | 1.350/s | 13 ms | 32 ms | 0 |
| horários livres | 20 | 307/s | 58 ms | 136 ms | 0 |
| horários livres, estresse | 100 | 320/s | 194 ms | 2.284 ms | 0 |
| força bruta no login | 5 | | | | 51 de 60 barrados com 429 |

Horários livres é a rota mais pesada (cruza grade, bloqueios e agendamentos do dia).
Mesmo com 100 conexões ao mesmo tempo não houve erro; a fila só deixa mais lento.

## Ambiente

Nenhum teste toca o banco de produção. Na CI cada job sobe um PostgreSQL 16 próprio
(service container). Cada teste limpa as tabelas antes de começar
([`DadosDeTeste`](../src/test/java/dev/gustavo/stewer/util/DadosDeTeste.java)).

## Como rodar

```bash
./mvnw test                                   # tudo, com cobertura em target/jacoco-report
./mvnw test -Dtest='dev.gustavo.stewer.seguranca.*Test'   # só a segurança

# carga: com a API no ar (perfil dev, banco local)
cd carga && npm install && API_URL=http://localhost:8080 node carga.mjs
```

Sem Docker, aponte para um PostgreSQL local:

```bash
./mvnw test -Dquarkus.devservices.enabled=false \
  -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/stewer_test \
  -Dquarkus.datasource.username=stewer -Dquarkus.datasource.password=stewer
```
