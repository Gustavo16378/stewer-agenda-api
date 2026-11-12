package dev.gustavo.stewer.push;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Envia notificacao para celulares pelo servico de push do Expo (gratuito, sem
 * chave para quem usa os tokens do proprio Expo).
 *
 * <p>Fire-and-forget: o envio e assincrono e uma falha so vai para o log. Um
 * aviso que nao chegou nao pode desfazer um agendamento que deu certo.
 */
@ApplicationScoped
public class NotificacaoPush {

    private static final Logger LOG = Logger.getLogger(NotificacaoPush.class);
    private static final URI EXPO = URI.create("https://exp.host/--/api/v2/push/send");

    public record Mensagem(String para, String titulo, String corpo, Map<String, String> dados) {
    }

    @ConfigProperty(name = "stewer.push.habilitado", defaultValue = "true")
    boolean habilitado;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /** Com o push desligado (testes), as mensagens ficam aqui para conferir. */
    private final List<Mensagem> retidas = Collections.synchronizedList(new ArrayList<>());

    public void enviar(List<String> tokens, String titulo, String corpo, Map<String, String> dados) {
        List<Mensagem> mensagens = tokens.stream()
                .filter(t -> t != null && t.startsWith("ExponentPushToken"))
                .map(t -> new Mensagem(t, titulo, corpo, dados))
                .toList();
        if (mensagens.isEmpty()) {
            return;
        }
        if (!habilitado) {
            retidas.addAll(mensagens);
            return;
        }
        try {
            List<Map<String, Object>> corpoJson = mensagens.stream()
                    .map(m -> Map.<String, Object>of("to", m.para(), "title", m.titulo(),
                            "body", m.corpo(), "data", m.dados(), "sound", "default"))
                    .toList();
            HttpRequest req = HttpRequest.newBuilder(EXPO)
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(corpoJson)))
                    .build();
            http.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .whenComplete((r, erro) -> {
                        if (erro != null) {
                            LOG.warnf("Push nao enviado: %s", erro.getMessage());
                        } else if (r.statusCode() >= 300) {
                            LOG.warnf("Push recusado pelo Expo (%d): %s", r.statusCode(), r.body());
                        }
                    });
        } catch (Exception e) {
            LOG.warnf(e, "Push nao enviado");
        }
    }

    public List<Mensagem> retidas() {
        return List.copyOf(retidas);
    }

    public void limparRetidas() {
        retidas.clear();
    }
}
