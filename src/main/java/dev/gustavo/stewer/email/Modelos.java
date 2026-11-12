package dev.gustavo.stewer.email;

/**
 * Textos dos e-mails. Simples de proposito: texto puro com uma versao HTML
 * minima, que chega bem em qualquer cliente de e-mail e nao cai em spam por
 * excesso de imagem.
 */
public final class Modelos {

    private Modelos() {
    }

    public record Email(String assunto, String texto, String html) {
    }

    public static Email codigoDeRedefinicao(String estudio, String nome, String codigo, int minutos) {
        String assunto = "Seu código para redefinir a senha";
        String texto = "Olá, " + nome + ".\n\n"
                + "Seu código para redefinir a senha no app do " + estudio + " é: " + codigo + "\n\n"
                + "Ele vale por " + minutos + " minutos. Se não foi você que pediu, ignore este e-mail: "
                + "sua senha continua a mesma.";
        String html = casca(estudio, "<p>Olá, " + escapar(nome) + ".</p>"
                + "<p>Seu código para redefinir a senha é:</p>"
                + "<p style=\"font-size:28px;letter-spacing:6px;font-weight:600;margin:24px 0\">"
                + codigo + "</p>"
                + "<p>Ele vale por " + minutos + " minutos. Se não foi você que pediu, ignore este "
                + "e-mail: sua senha continua a mesma.</p>");
        return new Email(assunto, texto, html);
    }

    public static Email lembrete(String estudio, String nome, String servico, String profissional,
            String quando) {
        String assunto = "Lembrete: " + servico + " " + quando;
        String texto = "Olá, " + nome + ".\n\n"
                + "Passando para lembrar do seu horário no " + estudio + ":\n"
                + servico + " com " + profissional + ", " + quando + ".\n\n"
                + "Se não puder vir, cancele ou remarque pelo app para liberar o horário.";
        String html = casca(estudio, "<p>Olá, " + escapar(nome) + ".</p>"
                + "<p>Passando para lembrar do seu horário:</p>"
                + "<p style=\"font-size:18px;margin:20px 0\"><strong>" + escapar(servico)
                + "</strong> com " + escapar(profissional) + "<br>" + escapar(quando) + "</p>"
                + "<p>Se não puder vir, cancele ou remarque pelo app para liberar o horário.</p>");
        return new Email(assunto, texto, html);
    }

    private static String casca(String estudio, String corpo) {
        return "<div style=\"font-family:Georgia,serif;color:#1a1a1a;max-width:520px;"
                + "margin:0 auto;padding:24px;line-height:1.6\">"
                + "<p style=\"font-size:11px;letter-spacing:3px;color:#8a7a55\">"
                + escapar(estudio).toUpperCase() + "</p>"
                + corpo
                + "<p style=\"font-size:12px;color:#888;margin-top:32px\">"
                + "E-mail automático. Para desligar os lembretes, abra o app em Perfil.</p></div>";
    }

    static String escapar(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
