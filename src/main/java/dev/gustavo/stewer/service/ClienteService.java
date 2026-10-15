package dev.gustavo.stewer.service;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.AnotacaoCliente;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.dto.cliente.AnotacaoRequest;
import dev.gustavo.stewer.dto.cliente.AnotacaoResponse;
import dev.gustavo.stewer.dto.cliente.ClienteDetalheResponse;
import dev.gustavo.stewer.dto.cliente.ClienteResumoResponse;
import dev.gustavo.stewer.exception.AcessoNegadoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.mapper.AnotacaoMapper;
import dev.gustavo.stewer.mapper.ClienteMapper;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.AnotacaoClienteRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;
import dev.gustavo.stewer.security.UsuarioLogado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Base de clientes vista pelo profissional: quem ja passou pelo estudio,
 * o historico de cada um e as anotacoes de visagismo.
 *
 * <p>Todo acesso e escopado pelo {@code tenantId} do token; o {@code id} que
 * chega pela rota so e aceito se for um usuario vivo, do mesmo tenant e com
 * papel CLIENTE -- caso contrario o recurso simplesmente nao existe (404).
 */
@ApplicationScoped
public class ClienteService {

    /** Tamanho de pagina quando o front nao pede nada. */
    private static final int TAMANHO_PADRAO = 20;

    /** Teto por pagina: impede que um {@code ?tamanho=100000} varra a tabela. */
    private static final int TAMANHO_MAXIMO = 100;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    AnotacaoClienteRepository anotacaoClienteRepository;

    @Inject
    UsuarioLogado usuarioLogado;

    /**
     * Lista os clientes do estabelecimento, opcionalmente filtrando por trecho
     * de nome, e-mail ou telefone.
     */
    public List<ClienteResumoResponse> listar(UUID tenantId, String busca, int pagina, int tamanho) {
        exigirEquipeViva();
        // Paginacao fora da faixa e ajustada em silencio em vez de virar 400:
        // e parametro de conveniencia da tela, nao vale derrubar a listagem.
        int paginaSegura = Math.max(pagina, 0);
        int tamanhoSeguro = tamanho <= 0 ? TAMANHO_PADRAO : Math.min(tamanho, TAMANHO_MAXIMO);

        return ClienteMapper.paraResumos(
                usuarioRepository.buscarClientes(tenantId, busca, paginaSegura, tamanhoSeguro));
    }

    /**
     * Ficha do cliente com o historico completo de atendimentos, do mais
     * recente para o mais antigo.
     */
    @Transactional
    public ClienteDetalheResponse detalhar(UUID tenantId, UUID clienteId) {
        exigirEquipeViva();
        Usuario cliente = exigirCliente(tenantId, clienteId);
        List<Agendamento> historico = agendamentoRepository.doCliente(tenantId, clienteId);
        return ClienteMapper.paraDetalhe(cliente, historico);
    }

    /** Anotacoes vivas do cliente, da mais nova para a mais antiga. */
    @Transactional
    public List<AnotacaoResponse> listarAnotacoes(UUID tenantId, UUID clienteId) {
        exigirEquipeViva();
        exigirCliente(tenantId, clienteId);
        return AnotacaoMapper.paraResponses(anotacaoClienteRepository.doCliente(tenantId, clienteId));
    }

    /**
     * Registra uma anotacao de visagismo. O autor nao vem da requisicao: e
     * sempre a ficha de profissional do usuario do token, para que nao seja
     * possivel assinar anotacao em nome de outra pessoa.
     */
    @Transactional
    public AnotacaoResponse criarAnotacao(UUID tenantId, UUID usuarioAutorId,
            UUID clienteId, AnotacaoRequest dto) {
        Usuario cliente = exigirCliente(tenantId, clienteId);

        Profissional autor = profissionalRepository.porUsuario(tenantId, usuarioAutorId)
                .orElseThrow(() -> new RegraNegocioException("SEM_FICHA_PROFISSIONAL",
                        "Seu usuario nao possui ficha de profissional neste estabelecimento; "
                                + "peca ao administrador para cria-la antes de anotar"));

        AnotacaoCliente anotacao = new AnotacaoCliente();
        anotacao.setTenantId(tenantId);
        anotacao.setCliente(cliente);
        anotacao.setProfissional(autor);
        anotacao.setTexto(dto.texto().trim());
        anotacao.setAgendamento(resolverAgendamento(tenantId, clienteId, dto.agendamentoId()));

        // Flush aqui para que createdAt/updatedAt gerados pelo Hibernate ja
        // estejam preenchidos quando o mapper montar a resposta.
        anotacaoClienteRepository.persistAndFlush(anotacao);

        return AnotacaoMapper.paraResponse(anotacao);
    }

    /**
     * Um atendimento so pode ancorar uma anotacao se for do mesmo tenant e do
     * mesmo cliente -- caso contrario o profissional estaria colando a
     * observacao no historico errado.
     */
    private Agendamento resolverAgendamento(UUID tenantId, UUID clienteId, UUID agendamentoId) {
        if (agendamentoId == null) {
            return null;
        }
        Agendamento agendamento = agendamentoRepository.buscarVivo(tenantId, agendamentoId)
                .orElseThrow(() -> new RegraNegocioException("AGENDAMENTO_INVALIDO",
                        "Agendamento nao encontrado neste estabelecimento: " + agendamentoId));

        if (!agendamento.getCliente().getId().equals(clienteId)) {
            throw new RegraNegocioException("AGENDAMENTO_INVALIDO",
                    "O agendamento informado nao pertence a este cliente");
        }
        return agendamento;
    }

    /** O {@code id} da rota precisa ser um CLIENTE vivo deste tenant. */

    /**
     * Quem se apresenta como PROFISSIONAL precisa ter ficha viva na equipe.
     *
     * <p>A anotacao {@code @RolesAllowed} olha so o claim do token, e o token
     * vale ate 8h. Sem esta checagem, desligar alguem nao surtia efeito nenhum
     * ate o token dele vencer: ele seguia lendo a clientela inteira e as
     * anotacoes de todos os colegas. Rebaixar a role (ProfissionalService)
     * fecha os logins futuros; isto aqui fecha o token que ja esta no bolso.
     *
     * <p>O ADMIN passa direto: ele administra o estabelecimento sem ter ficha.
     */
    private void exigirEquipeViva() {
        if (usuarioLogado.role() != Role.PROFISSIONAL) {
            return;
        }
        profissionalRepository.porUsuario(usuarioLogado.tenantId(), usuarioLogado.usuarioId())
                .orElseThrow(() -> new AcessoNegadoException(
                        "Voce nao faz mais parte da equipe deste estabelecimento"));
    }

    private Usuario exigirCliente(UUID tenantId, UUID clienteId) {
        return usuarioRepository.buscarVivoComRole(tenantId, clienteId, Role.CLIENTE)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Cliente", clienteId));
    }
}
