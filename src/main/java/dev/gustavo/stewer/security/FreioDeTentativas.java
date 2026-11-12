package dev.gustavo.stewer.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import dev.gustavo.stewer.exception.MuitasTentativasException;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Conta falhas recentes por chave (o e-mail) e trava depois de um teto.
 *
 * <p>Em memoria de proposito: a API roda numa instancia so e o que importa e
 * tornar inviavel testar milhares de senhas. Reiniciar zera a contagem, o que
 * custa ao atacante um minuto de espera por reinicio, nao uma porta aberta.
 */
@ApplicationScoped
public class FreioDeTentativas {

    @ConfigProperty(name = "stewer.freio.max-falhas", defaultValue = "5")
    int maxFalhas;

    @ConfigProperty(name = "stewer.freio.janela-minutos", defaultValue = "15")
    int janelaMinutos;

    private final Map<String, Deque<Instant>> falhas = new ConcurrentHashMap<>();

    /** Recusa com 429 se a chave ja estourou o teto dentro da janela. */
    public void exigirLiberado(String chave) {
        Deque<Instant> recentes = limpar(chave);
        if (recentes != null && recentes.size() >= maxFalhas) {
            throw new MuitasTentativasException(
                    "Muitas tentativas seguidas. Espere " + janelaMinutos
                            + " minutos e tente de novo");
        }
    }

    public void registrarFalha(String chave) {
        Deque<Instant> fila = falhas.computeIfAbsent(normalizar(chave), k -> new ArrayDeque<>());
        synchronized (fila) {
            fila.addLast(Instant.now());
        }
    }

    public void zerar(String chave) {
        falhas.remove(normalizar(chave));
    }

    private Deque<Instant> limpar(String chave) {
        Deque<Instant> fila = falhas.get(normalizar(chave));
        if (fila == null) {
            return null;
        }
        Instant corte = Instant.now().minus(Duration.ofMinutes(janelaMinutos));
        synchronized (fila) {
            while (!fila.isEmpty() && fila.peekFirst().isBefore(corte)) {
                fila.pollFirst();
            }
        }
        return fila;
    }

    private static String normalizar(String chave) {
        return chave == null ? "" : chave.trim().toLowerCase();
    }
}
