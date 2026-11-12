package dev.gustavo.stewer.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import dev.gustavo.stewer.domain.CodigoRedefinicaoSenha;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class CodigoRedefinicaoSenhaRepository
        implements TenantScopedRepository<CodigoRedefinicaoSenha> {

    /** O codigo mais recente do usuario. So ele vale: pedir de novo invalida o anterior. */
    public Optional<CodigoRedefinicaoSenha> maisRecente(UUID tenantId, UUID usuarioId) {
        return vivos(tenantId, "usuario.id = :usuario", Sort.by("createdAt").descending(),
                Parameters.with("usuario", usuarioId))
                .firstResultOptional();
    }

    /** Quantos codigos o usuario pediu desde {@code desde}: freio contra caixa lotada. */
    public long pedidosDesde(UUID tenantId, UUID usuarioId, OffsetDateTime desde) {
        return vivos(tenantId, "usuario.id = :usuario and createdAt > :desde",
                Parameters.with("usuario", usuarioId).and("desde", desde))
                .count();
    }
}
