package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.EventoAuditoria;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

/** Auditoria: so leitura filtrada por tenant e insercao. */
@ApplicationScoped
public class EventoAuditoriaRepository implements PanacheRepositoryBase<EventoAuditoria, UUID> {

    /**
     * Eventos do estabelecimento, do mais novo para o mais antigo. Filtros
     * opcionais por entidade (AGENDAMENTO, SERVICO...) e por cliente.
     */
    public List<EventoAuditoria> buscar(UUID tenantId, List<String> entidades, UUID clienteId,
            int pagina, int tamanho) {
        StringBuilder q = new StringBuilder("tenantId = :tenant");
        Parameters p = Parameters.with("tenant", tenantId);
        if (entidades != null && !entidades.isEmpty()) {
            q.append(" and entidade in :entidades");
            p.and("entidades", entidades);
        }
        if (clienteId != null) {
            q.append(" and clienteId = :cliente");
            p.and("cliente", clienteId);
        }
        return find(q.toString(), Sort.descending("criadoEm"), p)
                .page(Page.of(pagina, tamanho))
                .list();
    }

    /**
     * Quando o cliente exclui a conta, o nome dele sai dos resumos e o autor
     * vira anonimo. Unica escrita permitida na auditoria depois de gravada:
     * o direito de ser esquecido vale mais que o registro literal.
     */
    public void anonimizarCliente(UUID tenantId, UUID clienteId, String nome) {
        getEntityManager().createNativeQuery("""
                update evento_auditoria
                   set resumo = replace(resumo, :nome, 'cliente que excluiu a conta'),
                       autor_nome = case when autor_id = :cliente then null else autor_nome end
                 where tenant_id = :tenant and cliente_id = :cliente
                """)
                .setParameter("nome", nome)
                .setParameter("cliente", clienteId)
                .setParameter("tenant", tenantId)
                .executeUpdate();
    }
}
