package dev.gustavo.stewer.push;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import dev.gustavo.stewer.domain.Agendamento;
import dev.gustavo.stewer.domain.Tenant;
import dev.gustavo.stewer.repository.AgendamentoRepository;
import dev.gustavo.stewer.repository.DispositivoRepository;
import dev.gustavo.stewer.repository.TenantRepository;
import dev.gustavo.stewer.service.AgendamentoEvento;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.inject.Inject;

/**
 * Avisa o outro lado quando a agenda muda: o cliente marcou, remarcou ou
 * cancelou -> o profissional recebe; a equipe marcou, remarcou ou cancelou ->
 * o cliente recebe.
 *
 * <p>So depois do commit (AFTER_SUCCESS): um aviso de "novo horario" para um
 * agendamento que voltou atras seria pior do que nenhum aviso.
 */
@ApplicationScoped
public class AvisosDeAgenda {

    private static final DateTimeFormatter QUANDO =
            DateTimeFormatter.ofPattern("EEE, dd/MM 'às' HH:mm", Locale.of("pt", "BR"));

    @Inject
    AgendamentoRepository agendamentoRepository;

    @Inject
    DispositivoRepository dispositivoRepository;

    @Inject
    TenantRepository tenantRepository;

    @Inject
    NotificacaoPush push;

    void aoMudar(@Observes(during = TransactionPhase.AFTER_SUCCESS) AgendamentoEvento evento) {
        QuarkusTransaction.requiringNew().run(() -> avisar(evento));
    }

    private void avisar(AgendamentoEvento evento) {
        Agendamento a = agendamentoRepository.findById(evento.agendamentoId());
        if (a == null) {
            return;
        }
        UUID clienteId = a.getCliente().getId();
        UUID profissionalUsuarioId = a.getProfissional().getUsuario().getId();
        boolean foiOCliente = clienteId.equals(evento.autorId());
        UUID destino = foiOCliente ? profissionalUsuarioId : clienteId;
        if (destino.equals(evento.autorId())) {
            return;
        }

        List<String> tokens = dispositivoRepository.tokensDe(a.getTenantId(), List.of(destino));
        if (tokens.isEmpty()) {
            return;
        }

        Tenant tenant = tenantRepository.findById(a.getTenantId());
        String quando = a.getInicio().atZoneSameInstant(tenant.zoneId()).format(QUANDO);
        String servico = a.getServico().getNome();
        String titulo;
        String corpo;
        if (foiOCliente) {
            String cliente = a.getCliente().getNome();
            titulo = switch (evento.tipo()) {
                case CRIADO -> "Novo horário";
                case REMARCADO -> "Horário remarcado";
                case CANCELADO -> "Horário cancelado";
            };
            corpo = cliente + " · " + servico + ", " + quando;
        } else {
            String profissional = a.getProfissional().getUsuario().getNome();
            titulo = switch (evento.tipo()) {
                case CRIADO -> "Horário marcado para você";
                case REMARCADO -> "Seu horário mudou";
                case CANCELADO -> "Seu horário foi cancelado";
            };
            corpo = servico + " com " + profissional + ", " + quando;
        }
        push.enviar(tokens, titulo, corpo, Map.of("agendamentoId", a.getId().toString()));
    }
}
