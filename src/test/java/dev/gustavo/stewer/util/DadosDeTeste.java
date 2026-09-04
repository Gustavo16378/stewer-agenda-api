package dev.gustavo.stewer.util;

import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Bloqueio;
import dev.gustavo.stewer.domain.Disponibilidade;
import dev.gustavo.stewer.domain.Profissional;
import dev.gustavo.stewer.domain.Role;
import dev.gustavo.stewer.domain.Servico;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.domain.Usuario;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.BloqueioRepository;
import dev.gustavo.stewer.repository.DisponibilidadeRepository;
import dev.gustavo.stewer.repository.ProfissionalRepository;
import dev.gustavo.stewer.repository.ServicoRepository;
import dev.gustavo.stewer.repository.TenantRepository;
import dev.gustavo.stewer.repository.UsuarioRepository;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

/**
 * Fabrica de dados para os testes.
 *
 * <p>O profile de teste roda a migration V1 e nada mais -- o seed de dev fica
 * de fora de proposito, para que o schema testado seja identico ao de
 * producao. Cada teste monta o cenario de que precisa por aqui.
 */
@ApplicationScoped
public class DadosDeTeste {

    public static final String SENHA = "dev12345";

    @Inject
    EntityManager em;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    UsuarioRepository usuarioRepository;

    @Inject
    ProfissionalRepository profissionalRepository;

    @Inject
    ServicoRepository servicoRepository;

    @Inject
    DisponibilidadeRepository disponibilidadeRepository;

    @Inject
    BloqueioRepository bloqueioRepository;

    @Inject
    AgendamentoRepository agendamentoRepository;

    /** Zera o banco entre testes, respeitando a ordem das FKs. */
    @Transactional
    public void limpar() {
        em.createNativeQuery("TRUNCATE TABLE anotacao_cliente, agendamento, bloqueio, "
                + "disponibilidade, servico, profissional, usuario, tenant CASCADE")
                .executeUpdate();
    }

    @Transactional
    public Tenant tenant(String nome, String slug) {
        Tenant tenant = new Tenant();
        tenant.setNome(nome);
        tenant.setSlug(slug);
        tenant.setAtivo(true);
        tenantRepository.persist(tenant);
        return tenant;
    }

    @Transactional
    public Usuario usuario(UUID tenantId, String nome, String email, Role role) {
        Usuario usuario = new Usuario();
        usuario.setTenantId(tenantId);
        usuario.setNome(nome);
        usuario.setEmail(email);
        usuario.setSenhaHash(BcryptUtil.bcryptHash(SENHA));
        usuario.setRole(role);
        usuarioRepository.persist(usuario);
        return usuario;
    }

    @Transactional
    public Profissional profissional(UUID tenantId, UUID usuarioId, String especialidade) {
        Profissional profissional = new Profissional();
        profissional.setTenantId(tenantId);
        profissional.setUsuario(em.getReference(Usuario.class, usuarioId));
        profissional.setEspecialidade(especialidade);
        profissional.setAtivo(true);
        profissionalRepository.persist(profissional);
        return profissional;
    }

    @Transactional
    public Servico servico(UUID tenantId, String nome, int duracaoMinutos, long precoCentavos) {
        Servico servico = new Servico();
        servico.setTenantId(tenantId);
        servico.setNome(nome);
        servico.setDuracaoMinutos(duracaoMinutos);
        servico.setPrecoCentavos(precoCentavos);
        servico.setAtivo(true);
        servicoRepository.persist(servico);
        return servico;
    }

    @Transactional
    public Disponibilidade disponibilidade(UUID tenantId, UUID profissionalId,
            int diaSemana, String horaInicio, String horaFim) {
        Disponibilidade grade = new Disponibilidade();
        grade.setTenantId(tenantId);
        grade.setProfissional(em.getReference(Profissional.class, profissionalId));
        grade.setDiaSemana((short) diaSemana);
        grade.setHoraInicio(LocalTime.parse(horaInicio));
        grade.setHoraFim(LocalTime.parse(horaFim));
        disponibilidadeRepository.persist(grade);
        return grade;
    }

    @Transactional
    public Bloqueio bloqueio(UUID tenantId, UUID profissionalId,
            OffsetDateTime inicio, OffsetDateTime fim, String motivo) {
        Bloqueio bloqueio = new Bloqueio();
        bloqueio.setTenantId(tenantId);
        bloqueio.setProfissional(em.getReference(Profissional.class, profissionalId));
        bloqueio.setInicio(inicio);
        bloqueio.setFim(fim);
        bloqueio.setMotivo(motivo);
        bloqueioRepository.persist(bloqueio);
        return bloqueio;
    }

    @Transactional
    public Agendamento agendamento(UUID tenantId, UUID clienteId, UUID profissionalId,
            UUID servicoId, OffsetDateTime inicio, OffsetDateTime fim, StatusAgendamento status) {
        Agendamento agendamento = new Agendamento();
        agendamento.setTenantId(tenantId);
        agendamento.setCliente(em.getReference(Usuario.class, clienteId));
        agendamento.setProfissional(em.getReference(Profissional.class, profissionalId));
        agendamento.setServico(em.getReference(Servico.class, servicoId));
        agendamento.setInicio(inicio);
        agendamento.setFim(fim);
        agendamento.setStatus(status);
        agendamentoRepository.persist(agendamento);
        return agendamento;
    }
}
