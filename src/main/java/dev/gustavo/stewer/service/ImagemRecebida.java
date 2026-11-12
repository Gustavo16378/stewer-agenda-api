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
        // O tipo declarado tem de bater com o conteudo: um HTML ou SVG com script nao entra
        // dizendo que e JPEG.
        if (!assinaturaConfere(tipo, bytes)) {
            throw new RegraNegocioException("FOTO_INVALIDA", "O arquivo nao e uma imagem " + tipo.substring(6).toUpperCase());
        }
        return new Imagem(tipo, bytes);
    }

    /** Os primeiros bytes de cada formato: JPEG FF D8 FF, PNG 89 50 4E 47, WebP RIFF....WEBP. */
    static boolean assinaturaConfere(String tipo, byte[] b) {
        return switch (tipo) {
            case "image/jpeg" -> b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
            case "image/png" -> b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
            case "image/webp" -> b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                    && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P';
            default -> false;
        };
    }

    public static String dataUrl(String tipo, byte[] bytes) {
        return "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(bytes);
    }
}
