package dev.gustavo.stewer.email;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Porta unica de saida de e-mail.
 *
 * <p>Desligado por padrao: sem um SMTP configurado (EMAIL_HABILITADO=true e as
 * variaveis SMTP_*), nada sai e o motivo fica no log. Assim a API sobe em
 * qualquer ambiente, e o e-mail liga quando houver um provedor (Brevo e Resend
 * tem cota gratuita).
 */
@ApplicationScoped
public class EnvioDeEmail {

    private static final Logger LOG = Logger.getLogger(EnvioDeEmail.class);

    @Inject
    Mailer mailer;

    @ConfigProperty(name = "stewer.email.habilitado", defaultValue = "false")
    boolean habilitado;

    public boolean habilitado() {
        return habilitado;
    }

    /**
     * Envia e devolve se saiu. Falha de SMTP nao derruba quem chamou: o lembrete
     * tenta de novo na proxima rodada e o "esqueci a senha" responde igual de
     * qualquer jeito.
     */
    public boolean enviar(String para, String assunto, String texto, String html) {
        if (!habilitado) {
            LOG.infof("E-mail desligado (EMAIL_HABILITADO=false); '%s' nao foi enviado.", assunto);
            return false;
        }
        try {
            mailer.send(Mail.withHtml(para, assunto, html).setText(texto));
            return true;
        } catch (RuntimeException e) {
            LOG.warnf(e, "Falha ao enviar e-mail '%s'", assunto);
            return false;
        }
    }
}
