package dev.gustavo.stewer.repository;

import java.util.List;
import java.util.UUID;

import dev.gustavo.stewer.domain.Servico;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ServicoRepository implements TenantScopedRepository<Servico> {

    public List<Servico> listar(UUID tenantId, boolean apenasAtivos) {
        Sort ordem = Sort.by("nome").ascending();
        if (!apenasAtivos) {
            return vivos(tenantId, ordem).list();
        }
        return vivos(tenantId, "ativo = :ativo", ordem, Parameters.with("ativo", true)).list();
    }

    public boolean nomeEmUso(UUID tenantId, String nome, UUID ignorarId) {
        String filtro = "lower(nome) = :nome" + (ignorarId == null ? "" : " and id <> :ignorar");
        Parameters params = Parameters.with("nome", nome.trim().toLowerCase());
        if (ignorarId != null) {
            params = params.and("ignorar", ignorarId);
        }
        return vivos(tenantId, filtro, params).count() > 0;
    }
}
