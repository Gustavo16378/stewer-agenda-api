package dev.gustavo.stewer.service;

import java.util.Base64;
import java.util.Set;

import dev.gustavo.stewer.exception.RegraNegocioException;

/**
 * Imagem que chega em base64 (foto de perfil, foto de antes e depois): confere o tipo,
 * decodifica e barra o tamanho. Aceita base64 puro ou uma data URL inteira.
 */
public final class ImagemRecebida {

    static final Set<String> TIPOS = Set.of("image/jpeg", "image/png", "image/webp");

    private ImagemRecebida() {
    }

    public record Imagem(String tipo, byte[] bytes) {
    }

    public static Imagem decodificar(String tipoConteudo, String base64, int tamanhoMaximo, String rotuloMaximo) {
        String tipo = tipoConteudo == null ? "" : tipoConteudo.trim().toLowerCase();
        if (!TIPOS.contains(tipo)) {
            throw new RegraNegocioException("FOTO_TIPO_INVALIDO", "Envie a foto em JPEG, PNG ou WebP");
        }
        byte[] bytes;
        try {
            String puro = base64.startsWith("data:") ? base64.substring(base64.indexOf(',') + 1) : base64;
            bytes = Base64.getDecoder().decode(puro.trim());
        } catch (IllegalArgumentException e) {
            throw new RegraNegocioException("FOTO_INVALIDA", "A imagem nao veio em base64 valido");
        }
        if (bytes.length == 0) {
            throw new RegraNegocioException("FOTO_INVALIDA", "A imagem veio vazia");
        }
        if (bytes.length > tamanhoMaximo) {
            throw new RegraNegocioException("FOTO_GRANDE_DEMAIS", "Foto grande demais; o limite e " + rotuloMaximo);
        }
        return new Imagem(tipo, bytes);
    }

    public static String dataUrl(String tipo, byte[] bytes) {
        return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
