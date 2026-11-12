package dev.gustavo.stewer.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.ImagemPublica;

import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ImagemPublicaRepository implements TenantScopedRepository<ImagemPublica> {

    private static final Sort ORDEM = Sort.by("ordem").and("createdAt");

    public List<ImagemPublica> doTipo(UUID tenantId, ImagemPublica.Tipo tipo) {
        return vivos(tenantId, "tipo = :tipo", ORDEM, Parameters.with("tipo", tipo)).list();
    }

    public List<ImagemPublica> doServico(UUID tenantId, UUID servicoId) {
        return vivos(tenantId, "servico.id = :servico", ORDEM, Parameters.with("servico", servicoId)).list();
    }

    /** Ids das fotos de cada servico, em ordem, sem carregar os bytes. */
    public Map<UUID, List<UUID>> idsPorServico(UUID tenantId) {
        List<Object[]> linhas = getEntityManager().createQuery(
                "select i.servico.id, i.id from ImagemPublica i where i.tenantId = :tenantId "
                        + "and i.deletedAt is null and i.servico is not null order by i.ordem, i.createdAt",
                Object[].class)
                .setParameter("tenantId", tenantId)
                .getResultList();
        Map<UUID, List<UUID>> mapa = new LinkedHashMap<>();
        for (Object[] l : linhas) {
            mapa.computeIfAbsent((UUID) l[0], k -> new java.util.ArrayList<>()).add((UUID) l[1]);
        }
        return mapa;
    }

    public int proximaOrdem(UUID tenantId, ImagemPublica.Tipo tipo, UUID servicoId) {
        Integer max = getEntityManager().createQuery(
                "select max(i.ordem) from ImagemPublica i where i.tenantId = :tenantId and i.deletedAt is null "
                        + "and i.tipo = :tipo and (:servico is null or i.servico.id = :servico)",
                Integer.class)
                .setParameter("tenantId", tenantId)
                .setParameter("tipo", tipo)
                .setParameter("servico", servicoId)
                .getSingleResult();
        return max == null ? 0 : max + 1;
    }

    /** Leitura publica: so pelo id, que nao da para adivinhar. */
    public java.util.Optional<ImagemPublica> publica(UUID id) {
        return find("id = :id and deletedAt is null", Parameters.with("id", id)).firstResultOptional();
    }
}
