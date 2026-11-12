package dev.gustavo.stewer.service;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import dev.gustavo.stewer.domain.EventoAuditoria;
import dev.gustavo.stewer.repository.EventoAuditoriaRepository;
import dev.gustavo.stewer.security.UsuarioLogado;

import io.quarkus.arc.Arc;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Registra quem fez o que. Chamado pelos services DENTRO da transacao da
 * mudanca: se a mudanca desfaz (rollback), o registro some junto; se o registro
 * falhar, a mudanca tambem nao acontece. Nao existe mudanca sem rastro.
 *
 * <p>O autor sai do token. Sem requisicao (tarefa agendada) ou sem login
 * (cadastro), o autor fica vazio ou e informado por quem chama.
 */
@ApplicationScoped
public class Auditoria {

    /** O que aconteceu. Texto estavel: o app traduz e filtra por ele. */
    public static final class Acao {
        public static final String CRIOU = "CRIOU";
        public static final String ALTEROU = "ALTEROU";
        public static final String REMOVEU = "REMOVEU";
        public static final String REMARCOU = "REMARCOU";
        public static final String CANCELOU = "CANCELOU";
        public static final String CONCLUIU = "CONCLUIU";
        public static final String NAO_COMPARECEU = "NAO_COMPARECEU";
        public static final String CADASTROU = "CADASTROU";
        public static final String EXCLUIU_CONTA = "EXCLUIU_CONTA";

        private Acao() {
        }
    }

    /** Em que coisa. */
    public static final class Entidade {
        public static final String AGENDAMENTO = "AGENDAMENTO";
        public static final String SERVICO = "SERVICO";
        public static final String PROFISSIONAL = "PROFISSIONAL";
        public static final String HORARIOS = "HORARIOS";
        public static final String BLOQUEIO = "BLOQUEIO";
        public static final String FICHA = "FICHA";
        public static final String ANOTACAO = "ANOTACAO";
        public static final String FOTO = "FOTO";
        public static final String CLIENTE = "CLIENTE";

        private Entidade() {
        }
    }

    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("EEE dd/MM HH:mm", Locale.forLanguageTag("pt-BR"));

    @Inject
    EventoAuditoriaRepository repositorio;

    /** "R$ 180,00" */
    public static String reais(long centavos) {
        return String.format(Locale.forLanguageTag("pt-BR"), "R$ %,.2f", centavos / 100.0);
    }

    /** "sex 13/11 15:00", no fuso do estabelecimento. */
    public static String quando(OffsetDateTime instante, ZoneId fuso) {
        return QUANDO.format(instante.atZoneSameInstant(fuso)).replace(".", "");
    }

    @Transactional(Transactional.TxType.MANDATORY)
    public void registrar(UUID tenantId, String acao, String entidade, UUID entidadeId,
            UUID clienteId, String resumo) {
        EventoAuditoria e = base(tenantId, acao, entidade, entidadeId, clienteId, resumo);
        UsuarioLogado logado = usuarioLogado();
        if (logado != null && logado.autenticado()) {
            e.setAutorId(logado.usuarioId());
            e.setAutorNome(logado.nome());
            e.setAutorRole(logado.role().name());
        }
        repositorio.persist(e);
    }

    /** Quando quem fez nao esta no token (o proprio cadastro, por exemplo). */
    @Transactional(Transactional.TxType.MANDATORY)
    public void registrarComo(UUID tenantId, UUID autorId, String autorNome, String autorRole,
            String acao, String entidade, UUID entidadeId, UUID clienteId, String resumo) {
        EventoAuditoria e = base(tenantId, acao, entidade, entidadeId, clienteId, resumo);
        e.setAutorId(autorId);
        e.setAutorNome(autorNome);
        e.setAutorRole(autorRole);
        repositorio.persist(e);
    }

    private static EventoAuditoria base(UUID tenantId, String acao, String entidade,
            UUID entidadeId, UUID clienteId, String resumo) {
        EventoAuditoria e = new EventoAuditoria();
        e.setTenantId(tenantId);
        e.setAcao(acao);
        e.setEntidade(entidade);
        e.setEntidadeId(entidadeId);
        e.setClienteId(clienteId);
        e.setResumo(resumo.length() > 400 ? resumo.substring(0, 397) + "..." : resumo);
        return e;
    }

    /** UsuarioLogado e de requisicao: fora de uma (scheduler) nao ha contexto ativo. */
    private static UsuarioLogado usuarioLogado() {
        if (!Arc.container().requestContext().isActive()) {
            return null;
        }
        return Arc.container().instance(UsuarioLogado.class).get();
    }
}
