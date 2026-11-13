package dev.gustavo.stewer.email;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import org.jboss.logging.Logger;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.StatusAgendamento;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.repository.TenantRepository;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Manda o lembrete por e-mail para quem tem horario nas proximas 24 horas.
 *
 * <p>Roda de hora em hora, nao de minuto em minuto, por causa do banco gratuito:
 * cada rodada acorda o Postgres do Neon, e acorda-lo a cada poucos minutos
 * gastaria a cota mensal. De hora em hora o lembrete sai entre 23 e 24 horas
 * antes, e o banco dorme o resto do tempo.
 *
 * <p>Quem marcou com menos de uma hora de antecedencia nao recebe: ele acabou
 * de marcar, nao precisa ser lembrado.
 */
@ApplicationScoped
public class LembreteDeHorario {

    private static final Logger LOG = Logger.getLogger(LembreteDeHorario.class);

    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'às' HH:mm", Locale.of("pt", "BR"));

    @Inject
    EnvioDeEmail envioDeEmail;

    @Inject
    TenantRepository tenantRepository;

    @Scheduled(cron = "{stewer.lembrete.cron}", concurrentExecution = Scheduled.ConcurrentExecution.SKIP,
            skipExecutionIf = Desligado.class)
    void rodar() {
        enviarPendentes();
    }

    /** Visivel para o teste chamar sem esperar o relogio. Devolve quantos sairam. */
    @Transactional
    public int enviarPendentes() {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        List<Agendamento> pendentes = Panache.getEntityManager().createQuery("""
                select a from Agendamento a
                  join fetch a.cliente c
                  join fetch a.servico
                  join fetch a.profissional p
                  join fetch p.usuario
                 where a.deletedAt is null
                   and a.status = :status
                   and a.lembreteEnviadoEm is null
                   and a.inicio > :desde
                   and a.inicio <= :ate
                   and c.lembreteEmail = true
                   and c.deletedAt is null
                """, Agendamento.class)
                .setParameter("status", StatusAgendamento.AGENDADO)
                .setParameter("desde", agora.plusHours(1))
                .setParameter("ate", agora.plusHours(24))
                .getResultList();

        int enviados = 0;
        for (Agendamento a : pendentes) {
            Tenant tenant = tenantRepository.findById(a.getTenantId());
            String quando = a.getInicio().atZoneSameInstant(tenant.zoneId()).format(QUANDO);
            Modelos.Email email = Modelos.lembrete(tenant.getNome(), a.getCliente().getNome(),
                    a.getServico().getNome(), a.getProfissional().getUsuario().getNome(), quando);
            if (!envioDeEmail.enviar(a.getCliente().getEmail(), email.assunto(), email.texto(),
                    email.html())) {
                // Um envio falhou: o SMTP provavelmente esta fora. Insistir em cada um
                // seguraria a transacao ate o limite; os que faltam saem na proxima rodada.
                break;
            }
            a.setLembreteEnviadoEm(agora);
            enviados++;
        }
        if (enviados > 0) {
            LOG.infof("%d lembrete(s) de horario enviados.", enviados);
        }
        return enviados;
    }

    /** Sem e-mail configurado nem consulta o banco: a rodada nao acorda o Neon a toa. */
    @ApplicationScoped
    public static class Desligado implements Scheduled.SkipPredicate {

        @Inject
        EnvioDeEmail envioDeEmail;

        @Override
        public boolean test(io.quarkus.scheduler.ScheduledExecution execucao) {
            return !envioDeEmail.habilitado();
        }
    }
}
