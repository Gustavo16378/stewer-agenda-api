# Relatório — preparação para produção gratuita

Ciclo seguinte ao [RELATORIO-CORRECOES.md](RELATORIO-CORRECOES.md). Objetivo: deixar a API
pronta para rodar de graça no Render com banco no Neon, e o app pronto para apontar para ela.

**Nada foi provisionado.** Não tenho acesso às suas contas e não tentei criar nenhuma. O que
existe é código, configuração e documentação prontos para você clicar nos painéis e colar
variáveis. O passo a passo é o [DEPLOY.md](DEPLOY.md).

| | Antes | Depois |
|---|---|---|
| Testes da API | 167 | 181 |
| Testes do app | 164 | 171 |
| Workflows de CI | 0 | 3 |

---

## Commits

### API

| Hash | O quê |
|---|---|
| `34e1859` | Imagem de produção multi-stage que cabe em 512 MB |
| `a605e77` | `DATABASE_URL` do painel do Neon colada como está |
| `9a04da9` | `GET /ping`, sinal de vida que não acorda o banco |
| `db63f0e` | Blueprint do Render |
| `d969def` | CI: testes com Postgres real e build da imagem |
| `c36589e` | CI: ping de 10 em 10 minutos |
| `23294af` | `DEPLOY.md` |
| `9fb6fd1` | Este relatório |
| `8a08a22` | CI: `mvnw` executável e buildx com driver que exporta cache |

### App

| Hash | O quê |
|---|---|
| `7b4033e` | CI: typecheck, lint e testes |
| `19a374b` | Aguenta o servidor acordando sem mostrar erro seco |
| `73416e5` | `eas.json` e `.env.example` apontando para a API real |
| `9f6ca1d` | README aponta para o guia de deploy |

---

## Os números medidos

Container real, `--memory=512m --memory-swap=512m --cpus=0.1`, contra um PostgreSQL 16 em
rede Docker isolada, **banco vazio** (as 4 migrations e o bootstrap rodaram do zero).

| Medida | Valor | Teto |
|---|---|---|
| Subida completa até responder | **75 s** | — |
| Memória em repouso | **163 MB** | 512 MB (32%) |
| Memória sob rajada | **174 MB** | 512 MB (34%) |
| Rajada: 120 requisições, 12 simultâneas | **10,1 s, 0 erros** | — |
| `OOMKilled` | **não** | — |
| Reinícios | **0** | — |
| Tamanho da imagem | 391 MB | — |

A folga é grande: 338 MB sobrando no pico. Os 75 s de subida são consequência dos 0,1 de CPU,
não de desperdício — bate com o "cerca de um minuto" que o próprio Render documenta.

### Como cheguei nas flags da JVM

Não chutei. `MaxRAMPercentage=75` **matou o container por OOM** sob a rajada: 75% de 512 MB
são 384 MB de heap, e o que sobra não cobre metaspace, code cache, pilhas e os buffers
diretos do Netty. Desci para **55%** (281 MB de heap) e a memória total estacionou em 174 MB
no pico — o heap nem chega perto do teto, e a margem absorve um pico maior.

As outras: `UseSerialGC` porque com 0,1 de CPU não há paralelismo para aproveitar e o G1
reserva memória e threads que só atrapalham; `TieredStopAtLevel=1` porque o compilador C2
custa CPU que não temos e derruba o tempo de subida; `MaxMetaspaceSize` e
`ReservedCodeCacheSize` com teto explícito, porque sem eles crescem até o limite do container
e quem morre é o processo inteiro.

### A prova do endpoint leve

Com o container de produção no ar e o **Postgres parado**:

| Chamada | Resposta |
|---|---|
| `GET /ping` | **200 `pong`** |
| `GET /q/health/live` | **200** |
| `GET /q/health/ready` | **503** |

Religando o Postgres, readiness voltou a 200 e o login funcionou **sem reiniciar a API** —
o `validate-on-borrow` do pool descarta sozinho as conexões que o banco derrubou ao dormir.

É isso que separa as duas contas gratuitas: o ping de 10 em 10 minutos mantém o Render
acordado sem tocar no Neon, e as 100 CU-hours só correm quando alguém de fato usa o app.

---

## Regiões, e por quê

**Render: Virginia (US East). Neon: AWS US East (N. Virginia), `aws-us-east-1`.**

O Render não tem nenhuma região na América do Sul — as opções são Oregon, Ohio, Virginia,
Frankfurt e Singapura. Virginia é a mais próxima do Brasil.

O Neon **tem** São Paulo (`aws-sa-east-1`), e foi tentador. Mas você mesmo definiu o critério:
a latência entre API e banco pesa mais que a distância até o usuário, e a conta confirma.
Com os dois na Virginia, a conversa API↔banco é local (menos de 1 ms) e o usuário paga a
travessia **uma vez** por tela. Com o banco em São Paulo, **cada consulta** faria a travessia:
uma tela que faz seis consultas ficaria perto de um segundo mais lenta.

---

## Decisões que tomei sozinho

1. **Traduzi o enunciado de Spring Boot para Quarkus.** O pedido falava em "camadas do Spring
   Boot", "lazy initialization" e "grupo liveness do Actuator"; este projeto é Quarkus. Usei os
   equivalentes diretos: as camadas do `quarkus-app` (`lib/`, `quarkus/`, `app/`,
   `quarkus-run.jar`, da que menos muda para a que mais muda) e o SmallRye Health
   (`/q/health/live` e `/q/health/ready`).

2. **Não há lazy initialization para avaliar.** Quarkus resolve injeção e configuração em
   tempo de build, não na subida — a técnica não existe aqui. A preocupação por trás dela
   ("não esconder erro de configuração") está atendida de outra forma: `TokenService` e
   `ConfiguracaoDeProducao` derrubam o boot quando falta `JWT_SECRET` ou `DATABASE_URL`.

3. **Não há JMX para desligar.** Quarkus não expõe JMX por padrão. O enxugamento real foi
   outro: imagem só com JRE, pool de 4 conexões, SerialGC e apenas C1.

4. **`/ping` próprio em vez de reaproveitar `/q/health/live`.** O liveness já não toca no
   banco, mas é um detalhe de configuração do SmallRye — uma extensão nova poderia registrar
   um check de liveness que consulta algo, e o custo apareceria como CU-hours do Neon no fim
   do mês, sem ninguém relacionar as duas coisas. `/ping` devolve uma constante e não tem como
   regredir. Um teste trava que o check do datasource não aparece no grupo de liveness.

5. **A retentativa de "servidor acordando" ficou na camada HTTP, não no TanStack Query.** O
   login e a restauração de sessão não passam por hooks; se a política morasse no Query, a
   primeira tela depois do sono (justamente o login) continuaria falhando.

6. **Retentativa conservadora por método.** Falha de conexão é repetida sempre (a requisição
   não chegou ao servidor, nada aconteceu). Já 502/503/504 só são repetidos em `GET` e nos dois
   endpoints de autenticação. `POST /agendamentos` **não** é repetido: duplicar reserva é pior
   do que falhar uma, mesmo com a restrição do banco protegendo.

7. **O banco não entrou no `render.yaml`.** O PostgreSQL gratuito do Render expira em 30 dias;
   declará-lo no blueprint convidaria ao erro de usá-lo. O Neon é criado à parte e entra como
   `DATABASE_URL`.

8. **O workflow de ping sai em silêncio sem a variável.** Um workflow vermelho todos os dias
   vira ruído que ninguém lê, e a única ação possível seria criar a variável. Ele avisa no
   resumo da execução e sai com sucesso.

9. **`QUARKUS_PROFILE=prod` explícito no blueprint.** O Quarkus escolhe `prod` ao rodar de um
   jar, mas depender disso é frágil: se o perfil cair para `dev`, a API procura um Postgres em
   `localhost` e falha de um jeito confuso.

10. **CI da API aponta para um service container em vez de Testcontainers.** Testcontainers
    dentro do Actions é mais lento e mais frágil. A imagem oficial do Postgres já traz o
    `btree_gist` que a `V4` exige. Conferi localmente com o mesmo comando: 181 testes verdes,
    nenhum container de Dev Services iniciado.

11. **Separei um commit depois de feito.** Um commit juntou a retentativa do app com a
    configuração do `eas.json` — dois assuntos. Como nada tinha sido enviado, desfiz com
    `reset --soft` e refiz em dois. Nenhum commit publicado foi reescrito.

---

## O que não deu para validar sem as contas

- **O deploy em si.** O `render.yaml` está escrito conforme a documentação do Render, mas
  nunca passou pelo parser deles. Se o blueprint reclamar de algum campo, o erro aparece na
  tela de criação e é corrigível ali mesmo.
- **A conexão real com o Neon.** Testei contra um PostgreSQL 16 local com senha cheia de
  caractere especial, incluindo a quebra da connection string e o `sslmode` na query. O que
  não dá para simular é o TLS obrigatório do Neon e o tempo real de acordar do compute.
- **`btree_gist` no Neon.** Confirmado na documentação deles: listado como suportado em
  PG14–PG18, sem restrição de plano. Se mesmo assim o usuário do banco não puder criar
  extensão, o `DEPLOY.md` traz a saída (rodar o `CREATE EXTENSION` no SQL Editor).
- **Se a região Virginia está disponível no plano Free do Render.** A documentação não diz que
  há restrição, mas também não afirma que não há. Se o painel recusar, Ohio é a segunda melhor
  — e aí o projeto do Neon deve ir para `aws-us-east-2`, pelo mesmo motivo de ficar junto.
- ~~Os workflows do GitHub Actions.~~ **Validados de verdade depois do push.** A primeira
  execução falhou por dois motivos reais, os dois corrigidos em `8a08a22`:
  `./mvnw: Permission denied` (o arquivo foi commitado do Windows e chegou ao índice como
  `100644`; agora é `100755`) e `Cache export is not supported for the docker driver`
  (faltava o `setup-buildx-action`). Depois disso: **CI da API verde em 3m17s com 181
  testes e a imagem montada; CI do app verde em 1m05s**. O workflow de ping também foi
  disparado à mão e saiu com sucesso, imprimindo que a variável `URL_DA_API` ainda não
  existe — exatamente o comportamento silencioso previsto.
- **O ping mantendo a instância acordada.** Depende de um serviço que ainda não existe.

---

## A conta das horas

| | |
|---|---|
| Horas gratuitas por mês, por workspace | 750 |
| Mês de 31 dias acordado o tempo todo | 744 |
| **Folga** | **6 horas** |

Cabe, mas por pouco — e o total é do **workspace inteiro**. Um segundo serviço gratuito faria
os dois dividirem as mesmas 750 horas, e nenhum passaria o mês no ar. Está escrito no
`DEPLOY.md` com destaque, porque é o tipo de limite que só aparece quando o serviço cai no dia 28.

**O ping não gasta CU-hours do Neon**, e isso foi medido: com o Postgres parado, `/ping`
continuou devolvendo 200. Ele não abre conexão.

---

## Nova estimativa

| | Como está hoje | Para publicar |
|---|---|---|
| API | ~95% → **~97%** | 72% → **88%** |
| App | ~88% | 65% → **72%** |

**O que moveu a API.** Faltava tudo que é operação: imagem que coubesse no plano gratuito,
configuração para um banco gerenciado, sinal de vida separado, blueprint, CI e um guia que
você consiga seguir. Os 12% que faltam são o deploy de verdade — apertar os botões — e o
roadmap de produto (remarcar, recuperação de senha, notificações), não defeito.

**O que moveu o app.** Deixou de apontar para domínios inventados, passou a aguentar o
servidor acordando sem mostrar erro seco, e ganhou CI. Os 28% que faltam são quase todos a
cadeia de publicação nas lojas, que continua intocada porque depende da sua conta.

---

## O que falta até a loja, por prioridade

1. **Executar o `DEPLOY.md`.** Nada mais avança sem a URL da API: os perfis do EAS, o ping e o
   teste em aparelho todos dependem dela. É a única tarefa que só você pode fazer, e leva uns
   40 minutos.
2. **Criar a variável `URL_DA_API` no GitHub** e conferir a primeira execução do ping. Sem
   isso o primeiro cliente do dia espera um minuto.
3. **`eas init`** para preencher `extra.eas.projectId`, hoje `undefined` — sem ele `eas build`
   nem começa.
4. **Build `preview` em aparelho real**, apontando para a API no Render. É o primeiro teste
   do app fora do navegador e contra a infraestrutura de verdade.
5. **Política de privacidade e termos hospedados**, com os links ligados nas telas de entrar e
   criar conta (hoje são texto morto). As lojas recusam o envio sem a URL.
6. **Arte final de ícone e splash.** Os arquivos em `assets/images/` são placeholders gerados
   por script.
7. **Recuperação de senha.** Fluxo obrigatório num app com login por senha, inexistente nas
   duas pontas. Hoje, quem esquece a senha depende de você recriar o usuário no banco.
8. **Build e teste em iOS**, que exige conta Apple Developer e nunca foi exercitado.
9. **Store listing**: descrição, capturas, categoria, e `submit.production` no `eas.json`,
   hoje vazio.
10. **Decidir sobre OTA.** O `eas.json` declara `channel` em todos os perfis, mas
    `expo-updates` não está instalado: ou instale, ou remova os canais para não prometer o que
    não existe.

---

Desenvolvido por **Gustavo Dev** — https://gustavodev.dev/
