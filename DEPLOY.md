# Como colocar a API no ar, de graça

Passo a passo para quem nunca usou nenhum destes painéis. São duas contas
gratuitas (**Neon** para o banco, **Render** para a API) e nenhum cartão de crédito.

Reserve uns 40 minutos na primeira vez. Depois disso, cada atualização é só um `git push`.

> **Por que duas contas?** O Render também oferece PostgreSQL gratuito, mas ele **expira em
> 30 dias** e o banco é apagado. O Neon gratuito não expira.

---

## Antes de começar

Tenha à mão:

- a conta do GitHub com os dois repositórios já publicados;
- um terminal para rodar um comando (`openssl`);
- um e-mail que você vai usar como administrador do estúdio.

Durante o processo você vai copiar **três segredos**. Cole cada um num arquivo de rascunho
no seu computador enquanto trabalha, e apague o arquivo no fim. Nenhum deles pode entrar
no Git.

---

## 1. Criar o banco no Neon

1. Acesse **neon.com** e crie a conta (dá para entrar com o GitHub).
2. Clique em **New Project**.
3. Preencha:
   - **Project name**: `stewer-agenda`
   - **Postgres version**: a mais recente oferecida (16, 17 ou 18 — todas servem)
   - **Region**: **AWS US East (N. Virginia)** — `aws-us-east-1`

> **Por que Virginia e não São Paulo?** O Neon tem região em São Paulo, mas o Render **não
> tem nenhuma região na América do Sul**. A API vai rodar na Virginia. Se o banco ficasse em
> São Paulo, **cada consulta** faria uma viagem Brasil–Estados Unidos ida e volta, e uma tela
> que faz seis consultas ficaria quase um segundo mais lenta. Com os dois na Virginia, a
> conversa entre API e banco é local; o que sobra é a distância do seu celular até a Virginia,
> paga **uma vez** por tela.

4. Criado o projeto, o painel mostra a caixa **Connection string**. Nela:
   - escolha a opção **Direct connection** (e **não** "Pooled connection" — a API já tem o
     pool dela);
   - clique no olho para revelar a senha e copie a linha inteira.

Ela se parece com isto:

```
postgresql://neondb_owner:npg_AbCd1234@ep-cool-sun-12345678.us-east-1.aws.neon.tech/neondb?sslmode=require
```

**Guarde essa linha inteira.** É o seu `DATABASE_URL`. Não precisa separar usuário e senha:
a API faz isso sozinha.

---

## 2. Gerar o segredo do JWT

No terminal:

```bash
openssl rand -base64 48
```

Sai uma linha embaralhada de uns 64 caracteres. **Guarde.** É o seu `JWT_SECRET`.

> Sem essa variável a API **recusa subir**, de propósito. Um valor padrão escondido num
> repositório público deixaria qualquer pessoa forjar um login de administrador.

No Windows sem `openssl`, use o PowerShell:

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Max 256 }))
```

---

## 3. Escolher a senha do primeiro administrador

Invente uma senha de pelo menos 8 caracteres, ou gere uma:

```bash
openssl rand -base64 18
```

**Guarde**, junto com o e-mail que você vai usar. É com esses dois que você vai entrar no app
pela primeira vez.

---

## 4. Subir a API no Render

1. Acesse **render.com** e crie a conta com o GitHub.
2. No painel, clique em **New** → **Blueprint**.
3. Escolha o repositório **stewer-agenda-api** e confirme. O Render lê o `render.yaml` do
   repositório e já sabe que é um serviço Docker, plano Free, região Virginia.
4. Ele vai **perguntar os valores** das cinco variáveis marcadas como secretas. Preencha:

| Variável | O que colar | Exemplo do formato |
|---|---|---|
| `DATABASE_URL` | a connection string do passo 1, inteira | `postgresql://usuario:senha@ep-xxx.us-east-1.aws.neon.tech/neondb?sslmode=require` |
| `JWT_SECRET` | o segredo do passo 2 | `9f3K2...` (64 caracteres) |
| `BOOTSTRAP_ADMIN_EMAIL` | seu e-mail de administrador | `gustavo@exemplo.com` |
| `BOOTSTRAP_ADMIN_SENHA` | a senha do passo 3 | mínimo 8 caracteres |
| `CORS_ORIGINS` | deixe **vazio** por enquanto | preencha só quando tiver o app na web |

As outras variáveis já vêm preenchidas pelo `render.yaml` e você não precisa tocar nelas
(`QUARKUS_PROFILE`, `BOOTSTRAP_TENANT_NOME`, `BOOTSTRAP_TENANT_FUSO`, `CORS_HABILITADO`,
`TENANT_SLUG_PADRAO`, `JWT_EXPIRACAO_SEGUNDOS`).

5. Clique em **Apply**. O Render começa a construir a imagem.

---

## 5. Acompanhar o primeiro deploy

Abra a aba **Logs** do serviço. O primeiro build demora **de 5 a 10 minutos** (ele compila o
projeto inteiro dentro do Docker). Depois disso, a API sobe em cerca de **75 segundos** —
é lento porque o plano gratuito dá só 0,1 de CPU.

O que você quer ver, nesta ordem:

```
Successfully applied 4 migrations to schema "public", now at version v5
Estabelecimento 'Stewer Gabriel' (slug 'stewer-gabriel', fuso America/Araguaina) criado na inicializacao.
Administrador inicial 'seu@email.com' criado na inicializacao.
stewer-agenda-api 1.0.0-SNAPSHOT on JVM ... started in 75.0s. Listening on: http://0.0.0.0:8080
Profile prod activated.
```

Quando aparecer **Live** no topo da página, anote a URL. Será algo como
`https://stewer-agenda-api.onrender.com`.

### Se falhar

| O que aparece no log | O que aconteceu | Como resolver |
|---|---|---|
| `DATABASE_URL nao esta definida` | a variável ficou vazia | preencha em **Environment** e clique em **Manual Deploy** |
| `DATABASE_URL nao virou uma URL JDBC de PostgreSQL` | a string foi colada pela metade | copie de novo do Neon, a linha **inteira** |
| `JWT_SECRET nao esta definida` ou `precisa ter no minimo 32 bytes` | segredo faltando ou curto | gere de novo com `openssl rand -base64 48` |
| `Este JWT_SECRET e um valor de exemplo` | você colou um exemplo da documentação | gere o seu |
| `stewer.bootstrap.admin.email foi configurado, mas a senha nao` | preencheu o e-mail e esqueceu a senha | preencha `BOOTSTRAP_ADMIN_SENHA` |
| `ERROR: permission denied to create extension "btree_gist"` | o usuário do banco não pode criar extensão | no Neon, abra o **SQL Editor** e rode `CREATE EXTENSION IF NOT EXISTS btree_gist;`, depois **Manual Deploy** |
| `Connection refused` / `timeout` falando do banco | região errada ou string da conexão *pooled* | confira que o projeto Neon está em `aws-us-east-1` e que você copiou a **Direct connection** |
| `Out of memory` / `Exited with status 137` | a instância estourou os 512 MB | não deve acontecer (medimos 174 MB no pico), mas se acontecer, abra uma issue com o log |
| Build parado em `dependency:go-offline` | rede do Render instável | **Manual Deploy** de novo |

---

## 6. Testar que está funcionando

Troque `SUA-URL` pela URL do Render nos comandos abaixo.

**a) A API está viva?**

```bash
curl https://SUA-URL.onrender.com/ping
```

Resposta esperada: `pong`. Se a instância estava dormindo, a primeira chamada demora até um
minuto — é normal.

**b) O banco responde?**

```bash
curl https://SUA-URL.onrender.com/q/health/ready
```

Resposta esperada: `{"status":"UP", ...}` com um check chamado
`Database connections health check`.

> Use este só quando quiser testar. Ele **acorda o banco** de propósito, e o Neon gratuito
> tem 100 CU-hours por mês.

**c) O administrador consegue entrar?**

```bash
curl -X POST https://SUA-URL.onrender.com/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"SEU@EMAIL.COM","senha":"A-SENHA-QUE-VOCE-ESCOLHEU"}'
```

Resposta esperada: um JSON com `token` e, dentro de `usuario`, `"role":"ADMIN"`.

**d) Cadastro de cliente funciona?**

```bash
curl -X POST https://SUA-URL.onrender.com/auth/registrar \
  -H 'Content-Type: application/json' \
  -d '{"nome":"Teste","email":"teste@exemplo.com","senha":"senha12345"}'
```

Resposta esperada: `201` com token e `"role":"CLIENTE"`.

**Deu tudo certo?** Volte ao Render, em **Environment**, e **apague**
`BOOTSTRAP_ADMIN_EMAIL` e `BOOTSTRAP_ADMIN_SENHA`. O administrador já existe no banco e as
variáveis não são mais necessárias.

---

## 7. Manter a API acordada

A instância gratuita **dorme depois de 15 minutos sem tráfego** e leva cerca de um minuto
para voltar. Um workflow já pronto no repositório bate em `/ping` de 10 em 10 minutos para
evitar isso.

Para ligar:

1. No GitHub, abra o repositório **stewer-agenda-api**.
2. **Settings** → **Secrets and variables** → **Actions** → aba **Variables**.
3. **New repository variable**:
   - **Name**: `URL_DA_API`
   - **Value**: `https://SUA-URL.onrender.com` (sem barra no fim)
4. Abra a aba **Actions** → **Manter a API acordada** → **Run workflow** para testar na hora.

Deu certo se o resumo da execução mostrar `resposta: 200`.

### O que esperar

- **O agendamento do GitHub atrasa.** Em horário de pico a fila do Actions empurra a execução
  alguns minutos, e algumas podem ser puladas. Com intervalo de 10 minutos e sono de 15, há
  folga para um atraso ocasional — mas não para uma pausa longa.
- **O GitHub desativa workflows agendados após 60 dias sem commit no repositório.** Se você
  passar dois meses sem mexer no código, o ping simplesmente para, sem avisar. O painel de
  Actions mostra um botão para reativar.

### Reforço opcional: cron-job.org

Imune aos dois problemas acima, e também gratuito:

1. Acesse **cron-job.org** e crie a conta.
2. **Create cronjob**.
3. **Title**: `Stewer API` · **URL**: `https://SUA-URL.onrender.com/ping`
4. Em **Schedule**, escolha **Every 10 minutes**.
5. Salve e ative.

Ter os dois ao mesmo tempo não atrapalha: o `/ping` é barato e não toca no banco.

### A conta das horas

| | |
|---|---|
| Horas gratuitas por mês, por workspace | 750 |
| Mês de 31 dias, acordado o tempo todo | 744 |
| Folga | **6 horas** |

Cabe, mas por pouco — e esse total é do **workspace inteiro**. Se você criar um segundo
serviço gratuito no Render, os dois dividem as mesmas 750 horas e nenhum dos dois passa o mês
inteiro no ar.

**O ping não gasta nada do Neon:** `/ping` devolve uma string constante, sem abrir conexão
com o banco. As 100 CU-hours do Neon só correm quando alguém de fato usa o app.

---

## 8. Apontar o app para a API

1. Abra `stewer-agenda-app/eas.json`.
2. Troque os dois `https://TROQUE-AQUI.onrender.com` (nos perfis `preview` e `production`)
   pela sua URL.
3. Para rodar na sua máquina, edite `stewer-agenda-app/.env`:

```
EXPO_PUBLIC_API_URL=https://SUA-URL.onrender.com
```

4. Se você publicar o app na web, volte ao Render e preencha `CORS_ORIGINS` com o endereço
   do site, por exemplo `https://stewer.vercel.app`. Sem isso o navegador bloqueia as
   chamadas. O app de celular não precisa disso.

---

## 9. Quando vale sair do gratuito

O que vai apertar primeiro, em ordem:

**1. As 750 horas do Render (primeira dor).** Já estão 99% comprometidas só para manter uma
API acordada. No instante em que você quiser um segundo serviço — um site, um ambiente de
teste — não cabe. O plano **Starter custa US$ 7 por mês** por serviço, não dorme e não tem
teto de horas.

**2. A lentidão para acordar.** Se você desligar o ping para economizar horas, o primeiro
cliente do dia espera um minuto. O app mostra "Conectando ao servidor" em vez de erro, mas é
uma espera real. Mesma solução: Starter.

**3. Os 0,1 de CPU.** Medimos 120 requisições com 12 simultâneas em 10 segundos, sem erro.
Para um estúdio com um profissional isso sobra. Se virarem cinco profissionais com clientes
agendando ao mesmo tempo, as telas começam a arrastar.

**4. As 100 CU-hours do Neon.** Só correm quando alguém usa o app. Um estúdio pequeno não
chega perto. Se chegar, o **Launch custa US$ 19 por mês**.

**5. Os 0,5 GB do Neon (a mais distante).** Este projeto guarda texto e datas, não imagens.
Dá para anos de agendamentos.

**Resumo:** a primeira conta a pagar é **US$ 7 por mês no Render**, e ela compra duas coisas
ao mesmo tempo — não dormir mais e poder ter um segundo serviço. Até lá, o gratuito dá conta
de um estúdio com um profissional.

---

Desenvolvido por **Gustavo Dev** — https://gustavodev.dev/
