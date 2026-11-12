package dev.gustavo.stewer.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Uma linha da auditoria: quem fez o que, em que e quando.
 *
 * <p>Somente insercao. Nao estende BaseEntity de proposito: registro de
 * auditoria nao e atualizado nem apagado, nem logicamente. O nome e o papel do
 * autor ficam copiados na linha, para a historia continuar legivel mesmo que a
 * pessoa saia da equipe ou exclua a conta.
 */
@Entity
@Table(name = "evento_auditoria")
public class EventoAuditoria {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private OffsetDateTime criadoEm;

    @Column(name = "autor_id", updatable = false)
    private UUID autorId;

    @Column(name = "autor_nome", length = 120, updatable = false)
    private String autorNome;

    @Column(name = "autor_role", length = 20, updatable = false)
    private String autorRole;

    @Column(name = "acao", nullable = false, length = 40, updatable = false)
    private String acao;

    @Column(name = "entidade", nullable = false, length = 30, updatable = false)
    private String entidade;

    @Column(name = "entidade_id", updatable = false)
    private UUID entidadeId;

    @Column(name = "cliente_id", updatable = false)
    private UUID clienteId;

    @Column(name = "resumo", nullable = false, length = 400, updatable = false)
    private String resumo;

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public OffsetDateTime getCriadoEm() {
        return criadoEm;
    }

    public UUID getAutorId() {
        return autorId;
    }

    public void setAutorId(UUID autorId) {
        this.autorId = autorId;
    }

    public String getAutorNome() {
        return autorNome;
    }

    public void setAutorNome(String autorNome) {
        this.autorNome = autorNome;
    }

    public String getAutorRole() {
        return autorRole;
    }

    public void setAutorRole(String autorRole) {
        this.autorRole = autorRole;
    }

    public String getAcao() {
        return acao;
    }

    public void setAcao(String acao) {
        this.acao = acao;
    }

    public String getEntidade() {
        return entidade;
    }

    public void setEntidade(String entidade) {
        this.entidade = entidade;
    }

    public UUID getEntidadeId() {
        return entidadeId;
    }

    public void setEntidadeId(UUID entidadeId) {
        this.entidadeId = entidadeId;
    }

    public UUID getClienteId() {
        return clienteId;
    }

    public void setClienteId(UUID clienteId) {
        this.clienteId = clienteId;
    }

    public String getResumo() {
        return resumo;
    }

    public void setResumo(String resumo) {
        this.resumo = resumo;
    }
}
