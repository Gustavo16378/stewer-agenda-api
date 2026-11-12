package dev.gustavo.stewer.service;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.EventoAuditoria;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.dto.auditoria.EventoAuditoriaResponse;
import dev.gustavo.stewer.exception.AcessoNegadoException;
import dev.gustavo.stewer.repository.EventoAuditoriaRepository;
import dev.gustavo.stewer.security.UsuarioLogado;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Leitura da auditoria. O ADMIN ve tudo; o profissional da equipe so a linha do
 * tempo de um cliente (o que aconteceu com ele), nunca o estabelecimento todo.
 */
@ApplicationScoped
public class AuditoriaService {

    private static final int TAMANHO_PADRAO = 30;
    private static final int TAMANHO_MAXIMO = 100;

    @Inject
    EventoAuditoriaRepository repositorio;

    @Inject
    ClienteService clienteService;

    @Inject
    UsuarioLogado usuarioLogado;

    @Transactional
    public List<EventoAuditoriaResponse> buscar(UUID tenantId, List<String> entidades, UUID clienteId,
            int pagina, int tamanho) {
        if (usuarioLogado.role() == Role.PROFISSIONAL) {
            if (clienteId == null) {
                throw new AcessoNegadoException("A auditoria completa e so do administrador");
            }
            clienteService.exigirEquipeViva();
            clienteService.exigirCliente(tenantId, clienteId);
        }
        int p = Math.max(pagina, 0);
        int t = tamanho <= 0 ? TAMANHO_PADRAO : Math.min(tamanho, TAMANHO_MAXIMO);
        List<String> filtro = entidades == null ? List.of()
                : entidades.stream().filter(e -> e != null && !e.isBlank()).map(String::trim).toList();
        return repositorio.buscar(tenantId, filtro, clienteId, p, t).stream()
                .map(AuditoriaService::paraResponse)
                .toList();
    }

    private static EventoAuditoriaResponse paraResponse(EventoAuditoria e) {
        return new EventoAuditoriaResponse(e.getId(), e.getCriadoEm(), e.getAutorNome(), e.getAutorRole(),
                e.getAcao(), e.getEntidade(), e.getEntidadeId(), e.getClienteId(), e.getResumo());
    }
}
