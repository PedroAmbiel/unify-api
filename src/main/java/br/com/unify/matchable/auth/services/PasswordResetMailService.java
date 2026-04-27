package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class PasswordResetMailService {

    @Inject
    Mailer mailer;

    public void sendResetLink(User user, String resetUrl, Instant expiresAt) {
        String recipient = user.email;
        String displayName = user.name == null || user.name.isBlank() ? "usuario" : user.name;
        String body = """
                Ola %s,

                Recebemos uma solicitacao para redefinir a sua senha na Unify.

                Use o link abaixo para cadastrar uma nova senha:
                %s

                Este link expira em %s UTC.
                Se voce nao solicitou esta redefinicao, ignore este email.
                """.formatted(displayName, resetUrl, expiresAt);

        mailer.send(Mail.withText(recipient, "Redefinicao de senha", body));
    }
}