package dev.gustavo.stewer.service;

import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.exception.RecursoNaoEncontradoException;
import dev.gustavo.stewer.repository.TenantRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Resolucao do estabelecimento.
 *
 * <p>Hoje roda um tenant so, entao endpoints publicos (registrar/login) caem
 * no slug padrao quando o cliente nao informa nada. O dia em que existir o
 * segundo estabelecimento, basta o front passar o slug -- nada mais muda.
 */
@ApplicationScoped
public class TenantService {

    @Inject
    TenantRepository tenantRepository;

    @ConfigProperty(name = "stewer.tenant.slug-padrao")
    String slugPadrao;

    public Tenant resolver(String slug) {
        String alvo = (slug == null || slug.isBlank()) ? slugPadrao : slug.trim();
        return tenantRepository.porSlug(alvo)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Estabelecimento nao encontrado: " + alvo));
    }

    public Tenant porId(UUID id) {
        return tenantRepository.vivoPorId(id)
                .orElseThrow(() -> RecursoNaoEncontradoException.de("Estabelecimento", id));
    }
}
