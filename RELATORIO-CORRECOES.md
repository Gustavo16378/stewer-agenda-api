# Relatório de correções — outubro de 2026

Ciclo de correção posterior à auditoria que encontrou 94 achados nos dois repositórios
([`stewer-agenda-api`](https://github.com/Gustavo16378/stewer-agenda-api) e
[`stewer-agenda-app`](https://github.com/Gustavo16378/stewer-agenda-app)).

Foram corrigidos os **6 obrigatórios** e os **5 da segunda etapa**. Cada item virou dois
commits: um com o teste que reproduz o defeito (vermelho) e outro com a correção (verde).

**Método, em uma linha:** nenhum defeito foi dado como corrigido sem um teste que falhasse
antes e passasse depois, e sem reprodução contra a API real com o seed de desenvolvimento.

| | Antes | Depois |
|---|---|---|
| Testes da API | 126 | 167 |
| Testes do app | 136 | 164 |
| Telas com teste | 0 | 2 |
| Migrations | 1 (`V1`) | 3 (`V1`, `V3`, `V4`, `V5`) |

---

## O que foi corrigido

### Etapa 1 — os seis obrigatórios

#### 1. Fuso horário do estabelecimento

`1d414a6` (teste) · `001d975` (correção) · `77fee4c` (documentação no app)

A grade semanal é hora de **parede**, mas a API montava os instantes com `atOffset(UTC)`. A
faixa 09:00–19:00 virava `09:00Z–19:00Z`, que em Palmas é 06:00–16:00: o cliente escolhia
horário três horas deslocado, o bloqueio da tarde não derrubava slot nenhum e a agenda do dia
cortava às 21:00 locais.

- `V3` adiciona `tenant.fuso` (`varchar`, default `America/Araguaina`).
- Os **quatro** pontos que montavam instante a partir de hora ou data local passam a usar esse
  fuso: `inicioDaFaixa`/`fimDaFaixa`, `ocupacoesDoDia`, `cabeNaGrade` e a janela de
  `agendaDoDia`. `temBloqueio` também resolve o dia no calendário local.
- A conversão é sempre por `ZoneId`, nunca por offset fixo: offset muda com a data onde existe
  horário de verão.

Medido depois, contra a API real: `09:00:00-03:00` até `18:30:00-03:00`, e um bloqueio de
16:00 às 18:00 locais removendo exatamente 4 slots (antes removia zero).

#### 2. Segredo JWT sem valor padrão em produção

`2bd41eb` (teste) · `ad0b491` (correção)

O default sem prefixo de profile fazia um deploy que esquecesse `JWT_SECRET` subir assinando
token com uma string publicada no GitHub. Qualquer leitor do repositório forjaria um token com
`groups:["ADMIN"]` e o `tenantId` do estúdio.

`TokenService.validarSegredo` agora derruba o boot em quatro casos, com a mensagem do que
fazer: ausente, menor que 32 bytes, o segredo vazado (em **qualquer** profile) e, só em
produção, qualquer valor de exemplo do repositório. Produção é detectada por
`LaunchMode.NORMAL`, então dev e testes continuam subindo com os defaults deles.

#### 3. Reserva dupla no mesmo horário

`0a418aa` (teste) · `9718cc6` (correção)

A regra vivia só no código, como check-then-insert. Oito pedidos simultâneos no mesmo slot já
produziram oito agendamentos.

`V4` cria a extensão `btree_gist` e uma restrição `EXCLUDE USING gist` sobre
`(profissional_id, tstzrange(inicio, fim))`, restrita aos `AGENDADO` vivos. O conflito passa a
ser impossível por construção, independente de ordem de execução, de quantas instâncias da API
estejam no ar e do nível de isolamento.

Medido depois: 8 pedidos simultâneos → exatamente 1 reserva e 7 × 409, em três execuções
seguidas.

#### 4. Tela de disponibilidade que apagava horário

`0357798` (teste) · `ee87f76` (correção) — repositório do app

A tela colapsava a semana em **um** par `{início, fim}` e reescrevia esse par em todos os dias
ligados ao salvar. Com seg–sex 09:00–19:00 e sábado 09:00–13:00, desligar a quarta fazia o
sábado virar 09:00–19:00.

O modelo passa a ser um horário **por dia**. Ligar ou desligar um dia mexe só naquele dia. A
tela continua mostrando as três linhas do design quando a semana é uniforme (o caso do frame
12) e lista uma linha por dia quando os dias divergem. O modal de um dia só ganhou o
interruptor "Aplicar a todos os dias": espalhar horário virou escolha explícita.

Primeiro teste de tela do app. Para ele rodar, `jest.config.js` passou a transformar
`standard-navigation`, que o `expo-router` importa como ESM puro.

#### 5. Seed mínimo de produção

`be2bacb` (teste) · `d2b16b4` (correção)

As migrations de produção são DDL puro. O estabelecimento nascia só no seed de dev: a API subia
verde e o primeiro cadastro respondia `404 Estabelecimento nao encontrado`.

`BootstrapEstabelecimento` cria o que faltar na subida, lendo de variáveis de ambiente. É
idempotente (compara por slug e por e-mail) e **não sobrescreve** nada que já exista.

#### 6. Saída de conta para o ADMIN

`a5ea456` (teste) · `7c83885` (correção) — repositório do app

O ADMIN era o único papel sem caminho para encerrar a sessão. A ação foi para a linha do
eyebrow da visão geral, o mesmo lugar e o mesmo componente que a agenda do profissional usa.

### Etapa 2

#### 7. Ex-funcionário mantinha acesso

`a565a36` (teste) · `a81edde` (correção)

Desligar um profissional era soft delete só da ficha: a role continuava e `/clientes` exige
apenas a role. Duas frentes, porque uma só não resolve:

- `ProfissionalService.remover` rebaixa o usuário a `CLIENTE` → fecha os logins futuros;
- `ClienteService` exige ficha **viva** de quem se apresenta como `PROFISSIONAL` nas três
  leituras de cliente → fecha o token de até 8h que o ex-funcionário já tem no bolso.

Medido depois: com o mesmo token, `GET /clientes` passou de 200 para 403; o novo login volta
com papel `CLIENTE`.

#### 8. App travando na splash e deslogando sozinho

`22f7ef0` (teste) · `8496a76` (correção) — repositório do app

- Toda chamada passou a ter relógio (15s). Sem ele, uma rede que aceita a conexão e não responde
  deixava a promise pendente para sempre — era assim que a splash travava.
- O usuário passou a ser guardado junto do token, que é a peça que faltava para abrir offline
  sabendo qual papel mostrar.
- `GET /auth/eu` passou a **atualizar** quem é o usuário, não a autorizar a abertura. No erro,
  só `kind: 'autenticacao'` desloga; rede e 5xx mantêm a sessão.

#### 9. Corpo JSON ausente derrubando 8 endpoints com 500

`3fd85dc` (teste) · `ccd73bf` (correção)

`@Valid` não dispara em objeto nulo. Os 11 parâmetros de corpo dos 6 resources ganharam
`@NotNull`, então a falta de corpo vira 400 com a violação, como qualquer campo obrigatório.

Em `/auth/login` e `/auth/registrar`, que são públicos, isso era amplificação de log sem
autenticação nenhuma.

#### 10. Unicidade: 409 em vez de 500, e nome de serviço único

`27da162` (teste) · `6fcdb95` (correção) · `f83f896` (comentários)

`V5` cria `ux_servico_tenant_nome` (único por tenant, com `lower()`, parcial em
`deleted_at IS NULL`). `ViolacaoDeBanco` concentra a tradução de restrição para conflito de
negócio.

A tradução mora no `FalhaInesperadaMapper` e **não** nos services: com id gerado pela
aplicação, o Hibernate pode adiar o `INSERT` para o commit, e aí a exceção já escapou do `try`
e chega embrulhada em `RollbackException`. Isso foi medido — 7 dos 8 pedidos falhavam depois do
`try`.

#### 11. Health check e CORS

`c847197` (teste) · `bcded80` (correção)

`quarkus-smallrye-health` entrou no pom: `/q/health`, `/live` e `/ready`, com o check do
datasource vindo junto do agroal. CORS declarado e **desligado** por padrão, ligável por
`CORS_HABILITADO` e `CORS_ORIGINS`.

---

## Decisões que tomei sozinho

1. **`America/Araguaina` como padrão, no banco e não em configuração.** O fuso é atributo do
   estabelecimento, não do processo: dois tenants em fusos diferentes precisam de valores
   diferentes, e uma variável de ambiente não comporta isso. Fica numa coluna com default.

2. **O segredo de dev também foi trocado.** Você pediu que o valor antigo não fosse reutilizado
   nem em dev; gerei novos para `%dev` e `%test`. Eles são públicos também, e isso está
   explícito no `application.properties`: só assinam token contra banco local descartável. O
   antigo está **banido em qualquer profile**, não só em produção.

3. **Bootstrap na aplicação, não em migration.** Migration é arquivo versionado e não é lugar de
   credencial — o seed de dev, com senha conhecida por todos, é o exemplo do que evitar. No
   bootstrap os valores vêm do ambiente e podem ser removidos depois do primeiro boot.

4. **Admin inicial opcional, mas meia configuração derruba o boot.** Com `BOOTSTRAP_ADMIN_EMAIL`
   definido e senha ausente ou curta, a API recusa subir. Ninguém descobre que não há
   administrador até precisar de um — falhar cedo é mais barato.

5. **Fuso inválido no bootstrap também derruba o boot**, em vez de virar UTC em silêncio e
   trazer de volta o bug dos horários deslocados.

6. **A tradução de conflito ficou no exception mapper, não nos services.** Decisão tomada depois
   de medir: a violação aparecia no commit, fora do alcance de um `try` no service.

7. **Na tela de disponibilidade, semana divergente lista um dia por linha.** O design só
   desenhou a semana uniforme. Entre inventar um resumo que esconde a diferença e listar os
   dias no mesmo vocabulário visual, listar é mais honesto. A semana uniforme continua
   exatamente como o frame 12.

8. **Mensagem do commit inicial do app corrigida com `--amend`.** O primeiro commit saiu com um
   `@` solto por erro de sintaxe de shell. Como ele ainda não tinha sido enviado a lugar nenhum,
   corrigi antes do primeiro push, para o histórico público nascer limpo. Nenhum commit
   publicado foi reescrito.

9. **O README do app foi mantido, não reescrito.** Você pediu um README curto; o que existia já
   cobria o pedido (o que é, stack, como rodar, variáveis, link para a API) com qualidade.
   Encurtar destruiria documentação boa. Só corrigi o link relativo, que quebrava no GitHub.

10. **Nenhuma mudança de contrato da API.** Os campos e os caminhos continuam os mesmos; o que
    mudou foi o **valor** dos instantes devolvidos (agora no offset do estabelecimento) e alguns
    códigos de status que passaram de 500 para 400/409. O app não precisou de ajuste de tipos —
    só a documentação dele, que descrevia o quirk de fuso como esperado (`77fee4c`).

---

## O que ficou pendente

- **Revogação de token propriamente dita.** O ex-funcionário perdeu acesso a `/clientes`, mas o
  token antigo ainda abre `/agendamentos/agenda` até vencer. Fechar isso exige versão de sessão
  no usuário e checagem por requisição — é mudança de desenho, não conserto, e tem custo por
  requisição que merece decisão sua.
- **Endpoint de revogação para o ADMIN.** Não criei: sem o mecanismo de versão de sessão acima,
  ele seria só um atalho para o rebaixamento que o `DELETE` já faz.
- **`btree_gist` exige permissão para criar extensão.** Se o usuário do banco de produção não
  puder, peça ao provedor `CREATE EXTENSION btree_gist` antes do primeiro deploy — sem ela a
  `V4` falha e a API não sobe. Está anotado no README.
- **O `.env` local do app aponta para `192.168.10.249`**, um IP que esta máquina não tem mais.
  É arquivo local e ignorado pelo git, então não afeta o repositório, mas `npm run smoke:web`
  vai falhar até você acertar. Nas verificações eu redirecionei no navegador em vez de mexer no
  seu arquivo.
- **Recuperação de senha e `PUT /auth/eu`** continuam sem existir. Quem esquece a senha fica
  trancado — com o bootstrap, ao menos o administrador inicial pode ser recriado por variável de
  ambiente num novo deploy.

---

## Como gerar o segredo de produção

```bash
openssl rand -base64 48
```

Injete em `JWT_SECRET`. **Não há valor padrão em produção**: faltando a variável, com menos de
32 bytes, ou com qualquer valor de exemplo deste repositório, a API recusa subir e diz o
motivo. Trocar o segredo invalida todos os tokens já emitidos — todo mundo entra de novo.

No primeiro deploy, defina também:

```bash
BOOTSTRAP_TENANT_NOME="Stewer Gabriel"
BOOTSTRAP_TENANT_FUSO=America/Araguaina
BOOTSTRAP_ADMIN_EMAIL=voce@seudominio.com
BOOTSTRAP_ADMIN_SENHA="$(openssl rand -base64 18)"   # guarde antes de colar
```

As duas últimas podem ser removidas depois que a API subir uma vez.

---

## Nova estimativa

A auditoria estimava, em duas colunas:

| | Como estava hoje | Para publicar |
|---|---|---|
| API | ~85% | 48% |
| App | ~80% | 55% |

Depois deste ciclo:

| | Como está hoje | Para publicar |
|---|---|---|
| API | ~95% | **72%** |
| App | ~88% | **65%** |

**O que moveu a coluna da direita.** Os quatro bloqueadores de produto da API saíram: fuso,
reserva dupla, segredo e deploy em banco vazio. A camada operacional saiu do zero com health
check e CORS. O app ganhou a correção de perda de dados na grade, saída de conta para o ADMIN,
timeout e sessão que sobrevive à falta de rede — e os primeiros testes de tela.

**O que ainda segura.** Do lado da API, o que falta para 100% é quase tudo produto, não defeito:
remarcar, recuperação de senha, `PUT /auth/eu`, notificações, relatório agregado, revogação de
token. Do lado do app, o que segura é a cadeia de publicação, que continua quase intocada — e
é ela que separa 65% de 100%.

---

## O que falta para chegar ao EAS, por prioridade

1. **Hospedar a API com HTTPS.** Nada no app avança sem isso: `eas.json` aponta para
   `api-staging.stewer.dev` e `api.stewer.dev`, que não existem. Com o bootstrap e o health
   check prontos, o deploy agora é viável — defina as variáveis, suba, confirme `/q/health`.
2. **CI nos dois repositórios.** `./mvnw test` de um lado, `npm run typecheck && npm run lint &&
   npm test` do outro. Os dois repositórios já estão no GitHub; falta o workflow. Sem CI, nada
   garante que o próximo commit não desfaça o que este ciclo consertou.
3. **`eas init`** para preencher `extra.eas.projectId`, hoje `undefined` — sem ele `eas build`
   nem começa.
4. **Build `preview` em aparelho real**, na mesma Wi-Fi da API, percorrendo os fluxos com as três
   contas. É o primeiro teste fora do navegador.
5. **Política de privacidade e termos hospedados**, com os links ligados nas telas de entrar e
   criar conta (hoje são texto morto). As lojas recusam o envio sem a URL.
6. **Arte final de ícone e splash.** Os arquivos em `assets/images/` são placeholders gerados por
   script.
7. **Recuperação de senha.** Fluxo obrigatório num app com login por senha, e hoje inexistente
   nas duas pontas.
8. **Build e teste em iOS**, que exige conta Apple Developer e nunca foi exercitado.
9. **Store listing**: descrição, capturas, categoria, e `submit.production` no `eas.json`, hoje
   vazio.
10. **Decidir sobre OTA.** O `eas.json` declara `channel` em todos os perfis, mas `expo-updates`
    não está instalado: ou instale, ou remova os canais para não prometer o que não existe.

---

Desenvolvido por **Gustavo Dev** — https://gustavodev.dev/
