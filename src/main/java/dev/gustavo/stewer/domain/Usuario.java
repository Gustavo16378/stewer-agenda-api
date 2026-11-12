package dev.gustavo.stewer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Pessoa que acessa o sistema: cliente, profissional ou administrador. */
@Entity
@Table(name = "usuario")
public class Usuario extends TenantOwnedEntity {

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, length = 180)
    private String email;

    @Column(name = "telefone", length = 30)
    private String telefone;

    /** Hash BCrypt. A senha em claro nunca sai do service de auth. */
    @Column(name = "senha_hash", nullable = false, length = 120)
    private String senhaHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** Recebe lembrete de horario por e-mail. Liga por padrao (V6). */
    /** Foto de perfil. Lazy: listar usuarios nao carrega os bytes. */
    @jakarta.persistence.Basic(fetch = jakarta.persistence.FetchType.LAZY)
    @Column(name = "foto")
    private byte[] foto;

    @Column(name = "foto_tipo", length = 40)
    private String fotoTipo;

    /** Quando a foto mudou; nulo sem foto. Vira a "versao" que o app usa no cache. */
    @Column(name = "foto_atualizada_em")
    private java.time.OffsetDateTime fotoAtualizadaEm;

    @Column(name = "lembrete_email", nullable = false)
    private boolean lembreteEmail = true;

    public boolean isLembreteEmail() {
        return lembreteEmail;
    }

    public void setLembreteEmail(boolean lembreteEmail) {
        this.lembreteEmail = lembreteEmail;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public byte[] getFoto() {
        return foto;
    }

    public void setFoto(byte[] foto) {
        this.foto = foto;
    }

    public String getFotoTipo() {
        return fotoTipo;
    }

    public void setFotoTipo(String fotoTipo) {
        this.fotoTipo = fotoTipo;
    }

    public java.time.OffsetDateTime getFotoAtualizadaEm() {
        return fotoAtualizadaEm;
    }

    public void setFotoAtualizadaEm(java.time.OffsetDateTime fotoAtualizadaEm) {
        this.fotoAtualizadaEm = fotoAtualizadaEm;
    }
}
