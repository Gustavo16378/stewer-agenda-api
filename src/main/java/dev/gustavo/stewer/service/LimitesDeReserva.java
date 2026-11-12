package dev.gustavo.stewer.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.gustavo.stewer.exception.RegraNegocioException;
import dev.gustavo.stewer.repository.AgendamentoRepository;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Teto do que um cliente reserva sozinho pelo app.
 *
 * <p>O cadastro e aberto e nao confirma e-mail. Sem teto, uma pessoa cria
 * algumas contas e lota a agenda do mes inteiro. Profissional e ADMIN nao
 * passam por aqui: quando eles marcam, ja conhecem o cliente.
 */
@ApplicationScoped
public class LimitesDeReserva {

    @Inject
    AgendamentoRepository agendamentoRepository;

    @ConfigProperty(name = "stewer.reservas.max-futuros-por-cliente", defaultValue = "3")
    int maxFuturosPorCliente;

    @ConfigProperty(name = "stewer.reservas.antecedencia-maxima-dias", defaultValue = "60")
    int antecedenciaMaximaDias;

    public void exigirDentroDosLimites(UUID tenantId, UUID clienteId, OffsetDateTime inicio) {
        OffsetDateTime agora = OffsetDateTime.now(ZoneOffset.UTC);
        if (inicio.isAfter(agora.plusDays(antecedenciaMaximaDias))) {
            throw new RegraNegocioException("ANTECEDENCIA_MAXIMA",
                    "Da para agendar com ate " + antecedenciaMaximaDias + " dias de antecedencia");
        }
        if (agendamentoRepository.contarFuturosDoCliente(tenantId, clienteId, agora)
                >= maxFuturosPorCliente) {
            throw new RegraNegocioException("LIMITE_DE_AGENDAMENTOS",
                    "Voce ja tem " + maxFuturosPorCliente + " horarios marcados. "
                            + "Espere um deles passar ou cancele um para marcar outro");
        }
    }
}
