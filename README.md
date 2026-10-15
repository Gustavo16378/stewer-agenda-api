# Stewer Agenda API

API REST de agendamento para estúdio de visagismo e consultoria de imagem.

Um estabelecimento no lançamento, **schema multi-tenant desde o primeiro commit**: toda tabela
de negócio carrega `tenant_id` e nenhuma query roda sem esse filtro. O dia em que existir o
segundo estúdio, não há migration de emergência para escrever.

---

## Stack

| Camada | Escolha |
|---|---|
| Linguagem | Java 21 |
| Framework | Quarkus 3.33.3.1 (LTS) |
| Build | Maven (via `mvnw`, não precisa ter Maven instalado) |
| Banco | PostgreSQL 16 |
| Persistência | Hibernate ORM com Panache — **padrão Repository** |
| Migrations | Flyway |
| Auth | SmallRye JWT (HS256) — roles `CLIENTE`, `PROFISSIONAL`, `ADMIN` |
| Validação | Bean Validation (Hibernate Validator) |
| Docs | OpenAPI + Swagger UI |
| Testes | JUnit 5 + RestAssured, contra PostgreSQL 16 real (Dev Services / Testcontainers) |

Arquitetura em camadas: `resource` (HTTP) → `service` (regra de negócio, transação) →
`repository` (Panache). DTOs de entrada e saída são tipos separados das entidades, com
**mappers manuais** — sem geração de código, sem mágica de runtime.

---

## Pré-requisitos

- **JDK 21** (`java -version` deve mostrar 21)
- **Docker** rodando — usado pelo `docker-compose` em dev e pelos Testcontainers nos testes

Maven não é necessário: o repositório traz o Maven Wrapper (`./mvnw` / `mvnw.cmd`).

---

## Passo a passo

### 1. Subir o banco

```bash
docker compose up -d
```

Sobe um PostgreSQL 16 em `localhost:5432` (banco `stewer_agenda`, usuário e senha `stewer`),
com volume nomeado `stewer_agenda_pgdata` — os dados sobrevivem a `docker compose down`.

Para conferir:

```bash
docker compose ps
docker compose logs -f postgres
```

> **Ja tem um PostgreSQL na sua maquina?** Se a porta 5432 estiver ocupada por uma instalacao
> local, o container sobe mas as conexoes vao parar no Postgres errado -- o sintoma e
> `autenticacao do tipo senha falhou para o usuario "stewer"`. Nesse caso, publique o
> container em outra porta e aponte o dev para ela:
>
> ```bash
> POSTGRES_PORT=5433 docker compose up -d
> ./mvnw quarkus:dev -Dquarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5433/stewer_agenda
> ```

### 2. Rodar em modo dev

```bash
./mvnw quarkus:dev
```

No Windows (PowerShell / cmd): `.\mvnw.cmd quarkus:dev`

No boot o Flyway aplica `V1` (schema) e `V2` (seed de desenvolvimento). A API sobe em
**http://localhost:8080**.

| O quê | Onde |
|---|---|
| Swagger UI | http://localhost:8080/q/swagger-ui |
| OpenAPI (JSON) | http://localhost:8080/q/openapi?format=json |
| Dev UI do Quarkus | http://localhost:8080/q/dev |

### 3. Rodar os testes

```bash
./mvnw test
```

Não use o banco do compose: o profile `test` **não** define datasource, então o Quarkus sobe um
container PostgreSQL 16 descartável via Dev Services e aplica só a `V1`. O schema testado é
byte a byte o mesmo que vai para produção, e nenhum teste depende do seed.

Rodar uma classe ou um teste só:

```bash
./mvnw test -Dtest=HorariosLivresResourceTest
./mvnw test -Dtest=HorariosLivresResourceTest#diaSemDisponibilidadeNaoTemSlot
```

### 4. Empacotar

```bash
./mvnw package                       # target/quarkus-app/quarkus-run.jar
java -jar target/quarkus-app/quarkus-run.jar
```

---

## Usuários do seed (profile dev)

A migration `V2` roda **apenas em dev** e cria o estúdio "Stewer Gabriel"
(slug `stewer-gabriel`).

**A senha de todos é `dev12345`.**

| Papel | E-mail | Nome |
|---|---|---|
| `ADMIN` | `admin@stewer.dev` | Gabriel Admin |
| `PROFISSIONAL` | `stewer@stewer.dev` | Stewer Gabriel — especialidade Visagismo |
| `CLIENTE` | `joana@exemplo.dev` | Joana Ribeiro |
| `CLIENTE` | `marcos@exemplo.dev` | Marcos Tavares |

Também vêm 3 serviços — Corte + Visagismo (60 min, R$ 180,00), Consultoria de Imagem
(90 min, R$ 320,00) e Barba (30 min, R$ 70,00) — e a grade de disponibilidade do Stewer de
**segunda a sábado, 09:00 às 19:00**.

O hash gravado no seed é BCrypt custo 10 com salt fixo, para que o checksum da migration não
mude a cada build:

```
$2a$10$S1Pjb0TwOUbjZkPfPET0GO2XaZS8//YwbiyMnC5cHEqg9hqTkKbAO
```

### Pegando um token

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"stewer@stewer.dev","senha":"dev12345"}'
```

Depois mande `Authorization: Bearer <token>` nas demais chamadas. No Swagger UI, use o botão
**Authorize** e cole apenas o token.

---

## Endpoints

### Auth
| Método | Rota | Quem |
|---|---|---|
| `POST` | `/auth/registrar` | público — cria `CLIENTE` no tenant |
| `POST` | `/auth/login` | público — devolve JWT com role e tenant |
| `GET` | `/auth/eu` | autenticado — dados do token atual |

### Serviços
| Método | Rota | Quem |
|---|---|---|
| `GET` | `/servicos` | autenticado |
| `GET` | `/servicos/{id}` | autenticado |
| `POST` | `/servicos` | `ADMIN` |
| `PUT` | `/servicos/{id}` | `ADMIN` |
| `DELETE` | `/servicos/{id}` | `ADMIN` — soft delete |

### Profissionais
| Método | Rota | Quem |
|---|---|---|
| `GET` | `/profissionais` | autenticado |
| `GET` | `/profissionais/{id}` | autenticado |
| `POST` | `/profissionais` | `ADMIN` |
| `PUT` | `/profissionais/{id}` | `ADMIN` |
| `DELETE` | `/profissionais/{id}` | `ADMIN` — soft delete |

### Disponibilidade e bloqueios
| Método | Rota | Quem |
|---|---|---|
| `GET` | `/profissionais/{id}/disponibilidade` | autenticado |
| `PUT` | `/profissionais/{id}/disponibilidade` | `PROFISSIONAL` dono ou `ADMIN` — substitui a grade semanal inteira |
| `GET` | `/profissionais/{id}/bloqueios` | autenticado |
| `POST` | `/profissionais/{id}/bloqueios` | `PROFISSIONAL` dono ou `ADMIN` |
| `DELETE` | `/profissionais/{id}/bloqueios/{bloqueioId}` | `PROFISSIONAL` dono ou `ADMIN` |

### Agendamentos
| Método | Rota | Quem |
|---|---|---|
| `GET` | `/profissionais/{id}/horarios-livres?data=YYYY-MM-DD&servicoId=…` | autenticado |
| `POST` | `/agendamentos` | `CLIENTE` — cria para si |
| `GET` | `/agendamentos/meus?periodo=futuros\|historico\|todos` | `CLIENTE` |
| `GET` | `/agendamentos/agenda?data=YYYY-MM-DD` | `PROFISSIONAL` (própria) / `ADMIN` (com `profissionalId`) |
| `PATCH` | `/agendamentos/{id}/status` | `PROFISSIONAL`/`ADMIN` concluem, cancelam ou marcam não comparecimento; `CLIENTE` só cancela o próprio |

### Clientes (visão do profissional)
| Método | Rota | Quem |
|---|---|---|
| `GET` | `/clientes?busca=&pagina=&tamanho=` | `PROFISSIONAL` / `ADMIN` |
| `GET` | `/clientes/{id}` | `PROFISSIONAL` / `ADMIN` — dados + histórico |
| `POST` | `/clientes/{id}/anotacoes` | `PROFISSIONAL` |
| `GET` | `/clientes/{id}/anotacoes` | `PROFISSIONAL` / `ADMIN` |

### Formato de erro

Toda falha volta no mesmo envelope:

```json
{
  "codigo": "HORARIO_OCUPADO",
  "mensagem": "Ja existe um agendamento neste horario",
  "status": 409,
  "timestamp": "2026-09-01T22:14:03.512Z"
}
```

Erro de validação (400) inclui `violacoes`:

```json
{
  "codigo": "VALIDACAO",
  "mensagem": "Requisicao invalida",
  "status": 400,
  "violacoes": [{ "campo": "email", "mensagem": "email invalido" }]
}
```

`codigo` é estável — trate por ele, nunca pelo texto da mensagem.

---

## O endpoint de horários livres

`GET /profissionais/{id}/horarios-livres?data=YYYY-MM-DD&servicoId=…` é a peça central. O
cálculo é:

1. pega a **grade semanal** do profissional para o dia da semana daquela data;
2. subtrai os **bloqueios** que tocam a janela;
3. subtrai os **agendamentos ativos** — só status `AGENDADO` ocupa; `CANCELADO`,
   `CONCLUIDO` e `NAO_COMPARECEU` liberam o horário;
4. fatia o que sobrou pela **duração do serviço**, descartando slot que não caiba inteiro na
   faixa;
5. quando a data pedida é hoje, omite o que já passou.

Sobreposição usa intervalo meio-aberto (`a.inicio < b.fim && a.fim > b.inicio`): um
atendimento que termina 10:00 e outro que começa 10:00 **não** conflitam.

Essa regra não vive só no código. A migration `V4` cria uma restrição `EXCLUDE USING gist`
sobre `(profissional_id, tstzrange(inicio, fim))`, válida para os `AGENDADO` vivos: duas
reservas sobrepostas são impossíveis por construção, independente de ordem de execução, de
quantas instâncias da API estejam no ar e do nível de isolamento. O serviço continua checando
antes (para responder 409 sem ir ao banco no caso comum) e traduz a violação da restrição
(`SQLSTATE 23P01`) para o mesmo `409 HORARIO_OCUPADO`.

> A restrição exige a extensão `btree_gist`, criada pela própria migration. Se o usuário do
> banco de produção não puder criar extensão, peça ao provedor `CREATE EXTENSION btree_gist`
> antes do primeiro deploy — sem ela a migration falha e a API não sobe.

Dia sem grade devolve `200` com lista vazia — não `404`.

---

## Regras de dados (valem para todo o projeto)

- **Migrations sempre aditivas.** Nunca `DROP`, nunca `ALTER` destrutivo. Precisa mudar uma
  coluna? Cria a nova, migra o dado, para de ler a antiga.
- **Soft delete em toda entidade de negócio.** `deleted_at timestamptz NULL`; as queries
  padrão filtram `deleted_at IS NULL` (veja `TenantScopedRepository`). `DELETE` de recurso
  marca a coluna, não apaga a linha.
- **`created_at` / `updated_at` em toda tabela**, preenchidos pelo Hibernate.
- **IDs são UUID**, gerados pela aplicação.
- **Timestamps em UTC** (`timestamptz`). Instantes usam `OffsetDateTime`; hora do dia da grade
  semanal usa `time` / `LocalTime`.
- **A hora de parede da grade é lida no fuso do estabelecimento** (`tenant.fuso`, padrão
  `America/Araguaina` — o estúdio fica em Palmas, TO). `09:00` quer dizer nove da manhã no
  relógio de quem atende, em qualquer época do ano. O mesmo fuso define onde começa e termina o
  dia de calendário pedido em `?data=`.
  > `America/Araguaina` e **não** `America/Sao_Paulo`: se o horário de verão brasileiro voltar,
  > só São Paulo muda de offset, e a agenda de Palmas sairia uma hora errada metade do ano.
  > Quem converte é `ZoneId` (nunca um offset fixo), porque só ele sabe qual offset vale em
  > cada data. `FusoDoEstabelecimentoTest` trava essa regra.
  > Nao ative `quarkus.hibernate-orm.jdbc.timezone=UTC`. Ele faz o driver converter as colunas
  > `time` pelo fuso da JVM: a grade gravada como 09:00 volta como 06:00 numa maquina em UTC-3,
  > e todos os horarios oferecidos ao cliente saem errados. Os instantes ja sao `timestamptz`
  > (absolutos), entao a propriedade nao acrescenta nada. `DisponibilidadeSeedTimezoneTest`
  > trava essa regressao gravando a grade por SQL cru, como a migration do seed faz.
- **Índices**: `tenant_id` em toda tabela de negócio, `agendamento (profissional_id, inicio)`,
  `usuario (tenant_id, email)` — mais um índice parcial em `agendamento` só para os
  `AGENDADO` vivos, que é o que o cálculo de horários livres consulta.

### ⚠️ A versão `V2` está reservada

O seed de dev é `db/seed/V2__seed_dev.sql`. Ele fica num diretório **irmão** de `db/migration`
(e não dentro dele) porque `quarkus.flyway.locations` é resolvido em tempo de build e o scan é
recursivo — aninhado, ele vazaria para produção.

Consequência: nenhuma migration de negócio pode se chamar `V2`. Se alguém criar
`db/migration/V2__…`, o profile dev passa a enxergar duas migrations com versão 2 e o Flyway
aborta com `Found more than one migration with version 2`. A numeração de negócio já seguiu
para `V4`; a próxima é `V5`.

```
src/main/resources/db/
├── migration/            <- todos os profiles (vai para o jar de prod)
│   ├── V1__init_schema.sql
│   ├── V3__fuso_do_estabelecimento.sql
│   └── V4__agendamento_sem_sobreposicao.sql
└── seed/                 <- SÓ dev
    └── V2__seed_dev.sql
```

---

## Configuração por profile

| Profile | Banco | Migrations |
|---|---|---|
| `dev` | `docker-compose` em `localhost:5432` | `V1` + seed `V2` |
| `test` | container efêmero via Dev Services | só `V1` |
| `prod` | variáveis de ambiente | só `V1` |

### Variáveis de ambiente (profile prod / Railway)

Veja `.env.example`. As que importam:

| Variável | Descrição |
|---|---|
| `DATABASE_URL` | URL **JDBC**: `jdbc:postgresql://host:5432/banco` |
| `DATABASE_USERNAME` | usuário do banco |
| `DATABASE_PASSWORD` | senha do banco |
| `JWT_SECRET` | segredo HMAC. **Obrigatória em produção, sem valor padrão.** Gere com `openssl rand -base64 48` |
| `JWT_ISSUER` | emissor gravado em `iss` (default `https://stewer-agenda-api`) |
| `JWT_EXPIRACAO_SEGUNDOS` | validade do token (default `28800` = 8h) |
| `TENANT_SLUG_PADRAO` | slug usado quando o cliente não informa (default `stewer-gabriel`) |
| `BOOTSTRAP_TENANT_NOME` | nome do estabelecimento criado no primeiro boot |
| `BOOTSTRAP_TENANT_FUSO` | fuso IANA da grade (default `America/Araguaina`) |
| `BOOTSTRAP_ADMIN_EMAIL` | e-mail do admin inicial — opcional |
| `BOOTSTRAP_ADMIN_SENHA` | senha dele, mínimo 8 caracteres. Obrigatória se o e-mail estiver definido |
| `PORT` | porta HTTP — o Railway injeta sozinho |

O Railway costuma expor `DATABASE_URL` no formato `postgresql://user:senha@host:porta/banco`,
que **não** é JDBC. Converta antes de injetar:

```
postgresql://u:s@host:5432/db   ->   jdbc:postgresql://host:5432/db
                                     DATABASE_USERNAME=u
                                     DATABASE_PASSWORD=s
```

### O primeiro deploy

As migrations de produção são DDL puro: não criam nenhuma linha. O estabelecimento só
nasce no seed de dev, que nunca vai para produção. Sem nada a mais, a API subiria verde e o
primeiro cadastro responderia `404 Estabelecimento nao encontrado`.

Quem resolve isso é `BootstrapEstabelecimento`: na subida, cria o que faltar a partir das
variáveis de ambiente. É **idempotente** (compara por slug e por e-mail) e **não
sobrescreve** nada que já exista — mudar o nome ou o fuso depois é operação de banco, não
efeito colateral de reiniciar.

```bash
BOOTSTRAP_TENANT_NOME="Stewer Gabriel"
BOOTSTRAP_TENANT_FUSO=America/Araguaina
BOOTSTRAP_ADMIN_EMAIL=voce@seudominio.com
BOOTSTRAP_ADMIN_SENHA="$(openssl rand -base64 18)"   # guarde antes de colar
```

O admin inicial é opcional, mas se `BOOTSTRAP_ADMIN_EMAIL` estiver definido a senha passa a
ser obrigatória (mínimo 8 caracteres) e a API recusa subir sem ela — meia configuração é
pior que nenhuma, porque ninguém descobre que não há administrador até precisar de um.
Depois do primeiro boot as duas variáveis podem ser removidas.

> **Por que na aplicação e não numa migration:** migration não é lugar de credencial. O hash
> ficaria escrito num arquivo versionado, como acontece com o seed de dev, cuja senha todo
> mundo conhece. Aqui os valores vêm do ambiente e nunca entram no repositório.
>
> A senha **não** aparece no log; o e-mail aparece, para o operador confirmar quem foi criado.

`BancoVazioTest` sobe a API contra um estabelecimento que nenhuma migration cria e confere
que cadastro, login e `/auth/eu` funcionam.

### O segredo do JWT

Gere o seu e injete em `JWT_SECRET`:

```bash
openssl rand -base64 48
```

**Produção não tem valor padrão.** Se a variável faltar, tiver menos de 32 bytes (o mínimo do
HS256) ou for um dos valores de exemplo que estão neste repositório, a API **recusa subir** e
diz qual é o problema. Isso é deliberado: o repositório é público, e um default silencioso
deixaria qualquer leitor forjar um token com `groups:["ADMIN"]` e o `tenantId` do estúdio.

Dev e teste têm cada um o seu default no `application.properties`. São públicos também — só
assinam token contra um banco local descartável — e a mesma validação os rejeita em produção.

> O segredo usado antes de outubro de 2026 vazou no histórico público e está **banido em
> qualquer profile**. Se você tinha um ambiente apontando para ele, gere outro.

Trocar o segredo invalida todos os tokens já emitidos: todo mundo precisa entrar de novo.

`JWT_SECRET` aceita qualquer string. O SmallRye JWT, por baixo, só entende chave simétrica no
formato JWK — quem faz essa ponte é `JwtSecretConfigInterceptor`, que converte o segredo em
`{"kty":"oct","k":"…"}` na leitura da configuração. Isso mantém a variável de ambiente
simples de configurar sem abrir mão do HS256.

Em produção o Flyway roda com `clean-disabled=true`: nem uma variável de ambiente perdida
consegue limpar o banco.

---

## Estrutura

```
src/main/java/dev/gustavo/stewer/
├── config/       JwtSecretConfigInterceptor (segredo HMAC -> JWK)
├── domain/       entidades JPA, BaseEntity, TenantOwnedEntity, enums
├── dto/          records de entrada e saída, por área
├── exception/    exceções de negócio + ExceptionMappers + envelope de erro
├── mapper/       conversão manual entidade <-> DTO
├── repository/   Panache (padrão Repository); TenantScopedRepository é a base
├── resource/     endpoints JAX-RS
├── security/     TokenService (emissão) e UsuarioLogado (leitura do JWT)
└── service/      regra de negócio e transações

src/main/resources/
├── application.properties
├── META-INF/services/       registro do ConfigSourceInterceptor
└── db/{migration,seed}/

src/test/java/dev/gustavo/stewer/
├── resource/     testes de endpoint com RestAssured
└── util/         DadosDeTeste — fábrica de cenários
```

---

## Comandos úteis

```bash
docker compose up -d          # sobe o Postgres
docker compose down           # para (mantém o volume)
docker compose down -v        # para e APAGA os dados de dev

./mvnw quarkus:dev            # dev com live reload
./mvnw test                   # testes
./mvnw package                # jar
./mvnw quarkus:dev -Dquarkus.flyway.clean-at-start=true   # recria o banco de dev do zero
```

---

Desenvolvido por **Gustavo Dev** — https://gustavodev.dev/
