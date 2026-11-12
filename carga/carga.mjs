/**
 * Carga e estresse da API, caixa preta.
 *
 * Roda contra uma API ja no ar, com os dados de demonstracao (perfil dev), e dispara
 * pedidos com o autocannon:
 *
 *  - carga: o uso de um estudio cheio, com folga. Cobra latencia p95 e zero erro;
 *  - estresse: 100 conexoes ao mesmo tempo na rota mais pesada (horarios livres, que
 *    cruza grade, bloqueios e agendamentos). Cobra estabilidade: nenhum 5xx, nenhuma
 *    conexao perdida;
 *  - forca bruta no login: 60 tentativas na mesma conta. A trava responde 429 e a API
 *    continua de pe.
 *
 * Na CI a API, o banco e o teste dividem uma maquina pequena: os numeros servem para
 * pegar regressao (consulta sem indice, laco a mais), nao para prometer latencia.
 *
 *   API_URL=http://localhost:8080 node carga/carga.mjs
 *
 * Gera carga/relatorio.md (e o resumo do job, na CI). Sai com 1 se algo reprovar.
 */
import autocannon from 'autocannon';
import { appendFileSync, writeFileSync } from 'node:fs';

const API = (process.env.API_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const DURACAO = Number(process.env.CARGA_SEGUNDOS ?? 8);

async function json(caminho, opcoes = {}) {
  const r = await fetch(API + caminho, {
    ...opcoes,
    headers: { 'content-type': 'application/json', ...(opcoes.headers ?? {}) },
  });
  return { status: r.status, corpo: r.status === 204 ? null : await r.json().catch(() => null) };
}

const rodar = (opcoes) =>
  new Promise((ok, falha) => {
    const o = opcoes.amount ? opcoes : { duration: DURACAO, ...opcoes };
    const i = autocannon(o, (e, r) => (e ? falha(e) : ok(r)));
    autocannon.track(i, { renderProgressBar: false, renderResultsTable: false, renderLatencyTable: false });
  });

// --- preparo: um cliente novo, o servico e o profissional da demonstracao, um dia livre
const sufixo = Date.now().toString(36);
const cadastro = await json('/auth/registrar', {
  method: 'POST',
  body: JSON.stringify({ nome: `Carga ${sufixo}`, email: `carga-${sufixo}@exemplo.dev`, senha: 'carga12345' }),
});
if (cadastro.status !== 201) throw new Error(`cadastro falhou: ${cadastro.status}`);
const token = cadastro.corpo.token;
const auth = { authorization: `Bearer ${token}` };
const servicos = (await json('/servicos', { headers: auth })).corpo;
const profissionais = (await json('/profissionais', { headers: auth })).corpo;
const servico = servicos[0];
const profissional = profissionais[0];
const amanha = new Date(Date.now() + 86_400_000).toLocaleDateString('sv-SE', { timeZone: 'America/Sao_Paulo' });
const livres = `/profissionais/${profissional.id}/horarios-livres?data=${amanha}&servicoId=${servico.id}`;

const cenarios = [
  { nome: 'ping', tipo: 'carga', p95: 50, opcoes: { url: `${API}/ping`, connections: 20 } },
  { nome: 'estudio (publico, banco)', tipo: 'carga', p95: 150, opcoes: { url: `${API}/estudio`, connections: 20 } },
  { nome: 'catalogo de servicos', tipo: 'carga', p95: 150, opcoes: { url: `${API}/servicos`, connections: 20, headers: auth } },
  { nome: 'meus horarios', tipo: 'carga', p95: 200, opcoes: { url: `${API}/agendamentos/meus`, connections: 20, headers: auth } },
  { nome: 'horarios livres', tipo: 'carga', p95: 300, opcoes: { url: API + livres, connections: 20, headers: auth } },
  { nome: 'horarios livres, 100 conexoes', tipo: 'estresse', opcoes: { url: API + livres, connections: 100, headers: auth } },
];

const linhas = [];
let reprovou = false;

for (const c of cenarios) {
  const r = await rodar(c.opcoes);
  const erros5xx = r['5xx'] ?? 0;
  const p95 = r.latency.p97_5 ?? r.latency.p99; // o autocannon da p97.5; e o mais proximo de p95 que ele mede
  const ok = r.errors === 0 && r.timeouts === 0 && erros5xx === 0 && r.non2xx === 0 && (!c.p95 || p95 <= c.p95);
  if (!ok) reprovou = true;
  linhas.push(
    `| ${c.nome} | ${c.tipo} | ${c.opcoes.connections} | ${r.requests.total} | ${Math.round(r.requests.average)}/s | ${r.latency.p50} ms | ${p95} ms${c.p95 ? ` (teto ${c.p95})` : ''} | ${erros5xx + r.errors + r.timeouts} | ${ok ? 'ok' : 'REPROVOU'} |`
  );
}

// forca bruta: 60 senhas erradas na mesma conta
const bruta = await rodar({
  url: `${API}/auth/login`,
  method: 'POST',
  amount: 60,
  connections: 5,
  headers: { 'content-type': 'application/json' },
  body: JSON.stringify({ email: `carga-${sufixo}@exemplo.dev`, senha: 'errada-errada' }),
});
const bloqueados = bruta.statusCodeStats?.['429']?.count ?? 0;
const depois = await json('/ping');
const brutaOk = bloqueados >= 50 && (bruta['5xx'] ?? 0) === 0 && depois.status === 200;
if (!brutaOk) reprovou = true;
linhas.push(
  `| forca bruta no login | seguranca | 5 | 60 | | | | ${bruta['5xx'] ?? 0} | ${brutaOk ? 'ok' : 'REPROVOU'} (${bloqueados} de 60 barrados com 429) |`
);

const relatorio = [
  '## Carga e estresse da API',
  '',
  `${DURACAO} s por cenario, contra ${API}.`,
  '',
  '| cenario | tipo | conexoes | pedidos | vazao | p50 | p97,5 | erros | resultado |',
  '|---|---|---|---|---|---|---|---|---|',
  ...linhas,
  '',
].join('\n');

writeFileSync(new URL('./relatorio.md', import.meta.url), relatorio);
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, relatorio);
console.log(relatorio);
process.exit(reprovou ? 1 : 0);
