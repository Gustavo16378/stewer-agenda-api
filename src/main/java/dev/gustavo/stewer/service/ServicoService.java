package dev.gustavo.stewer.service;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.dto.servico.ServicoRequest;
import dev.gustavo.stewer.dto.servico.ServicoResponse;
import dev.gustavo.stewer.exception.ConflitoException;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.mapper.ServicoMapper;
import dev.gustavo.stewer.repository.ServicoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Catalogo de servicos do estabelecimento.
 *
 * <p>Todo metodo recebe o {@code tenantId} da sessao e repassa ao repositorio:
 * nenhuma query aqui enxerga outro estabelecimento nem registro soft-deletado.
 * Servico removido vira {@code deletedAt} preenchido porque agendamentos
 * antigos continuam apontando para ele -- apagar de verdade quebraria o
 * historico.
 */
@ApplicationScoped
public class ServicoService {

    @Inject
    ServicoRepository servicoRepository;

    /** Catalogo ordenado por nome. Inativos so entram quando explicitamente pedidos. */
    public List<ServicoResponse> listar(UUID tenantId, boolean incluirInativos) {
        return ServicoMapper.paraResponses(servicoRepository.listar(tenantId, !incluirInativos));
    }

    public ServicoResponse porId(UUID tenantId, UUID id) {
        return ServicoMapper.paraResponse(buscar(tenantId, id));
    }

    @Transactional
    public ServicoResponse criar(UUID tenantId, ServicoRequest dto) {
        String nome = dto.nome().trim();
        exigirNomeLivre(tenantId, nome, null);

        Servico servico = new Servico();
        servico.setTenantId(tenantId);
        aplicar(servico, dto, nome);
        // Flush imediato para que id e timestamps ja voltem preenchidos na resposta.
        // A checagem acima resolve o caso comum sem ir ao banco. Sob corrida as
        // duas transacoes passam por ela, e quem barra e ux_servico_tenant_nome.
        // ViolacaoDeBanco traduz a violacao no mesmo 409 -- no mapper, porque o
        // INSERT pode sair so no commit e escapar de um try daqui.
        servicoRepository.persistAndFlush(servico);

        return ServicoMapper.paraResponse(servico);
    }

    @Transactional
    public ServicoResponse atualizar(UUID tenantId, UUID id, ServicoRequest dto) {
        Servico servico = buscar(tenantId, id);
        String nome = dto.nome().trim();
        exigirNomeLivre(tenantId, nome, id);

        aplicar(servico, dto, nome);
        // A entidade esta gerenciada: o flush existe so para o updatedAt chegar
        // atualizado ao cliente, nao para persistir a mudanca.
        servicoRepository.flush();

        return ServicoMapper.paraResponse(servico);
    }

    /** Soft delete: o servico some do catalogo, mas o historico de agenda sobrevive. */
    @Transactional
    public void remover(UUID tenantId, UUID id) {
        servicoRepository.softDelete(buscar(tenantId, id));
    }

    private Servico buscar(UUID tenantId, UUID id) {
        return servicoRepository.buscarVivo(tenantId, id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Servico", id));
    }

    /** Nome duplicado confunde o cliente na hora de agendar; a comparacao ignora caixa. */
    private void exigirNomeLivre(UUID tenantId, String nome, UUID ignorarId) {
        if (servicoRepository.nomeEmUso(tenantId, nome, ignorarId)) {
            throw new ConflitoException("NOME_EM_USO", "Ja existe um servico com este nome");
        }
    }

    private static void aplicar(Servico servico, ServicoRequest dto, String nome) {
        servico.setNome(nome);
        servico.setDescricao(normalizar(dto.descricao()));
        servico.setDuracaoMinutos(dto.duracaoMinutos());
        servico.setPrecoCentavos(dto.precoCentavos());
        servico.setAtivo(dto.ativoOuPadrao());
    }

    static String normalizar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.trim();
        return limpo.isEmpty() ? null : limpo;
    }
}
