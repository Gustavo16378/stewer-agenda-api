# Segurança

## Como reportar

Achou uma falha? Não abra issue pública. Escreva para **gustavo@gustavodev.dev** com o
passo a passo para reproduzir. A resposta vem em até 3 dias úteis.

## O que a API faz, por risco (OWASP Top 10)

| risco | proteção | teste |
|---|---|---|
| A01 Controle de acesso | todo endpoint declara quem pode usar (`@RolesAllowed`, `@Authenticated` ou `@PermitAll`); o serviço confere o dono do registro (cliente só mexe no que é dele, profissional só na agenda dele); tudo filtrado pelo estúdio do token | `MatrizDeAcessoTest`, `AcessoAosDadosDosOutrosTest` |
| A02 Criptografia | senha em bcrypt; token HS256 com segredo de no mínimo 32 bytes, que a API recusa se estiver vazio, curto ou publicado no repositório; HSTS | `SegredoJwtTest`, `CaixaCinzaTest`, `TokensTest` |
| A03 Injeção | só consultas parametrizadas (Panache/JPA); entrada validada (Bean Validation); texto guardado como texto | `InjecaoTest` |
| A04 Design inseguro | foto conferida pelos primeiros bytes, não pelo tipo que o app declara; limite de tamanho; agenda sem sobreposição garantida pelo banco (constraint de exclusão) | `UploadECabecalhosTest`, `ReservaConcorrenteTest` |
| A05 Configuração | cabeçalhos de segurança em toda resposta; CORS só para as origens configuradas; erro sem pilha nem nome de classe; dado pessoal com `no-store` | `UploadECabecalhosTest` |
| A07 Autenticação | trava de força bruta no login (429); mensagem igual para e-mail que existe e que não existe; token adulterado, vencido ou `alg: none` recusado; código de redefinição de senha de uso único e com validade | `TokensTest`, `SenhaEmailEContaTest`, carga |
| A08 Integridade | auditoria só de inserção (quem fez o quê e quando), sem update nem delete | `AuditoriaTest` |
| A09 Registro | auditoria por estúdio, filtrável no painel; o log não guarda senha nem token | `AuditoriaTest` |

## Dados pessoais (LGPD)

Ao excluir a conta, o cliente é anonimizado, a ficha e as fotos dele saem junto, e o
nome dele some da auditoria. Ver `CarteiraDeClientesTest` e `ContaResourceTest`.
