package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.user.entity.User;
import io.quarkus.mailer.Mail;
import io.quarkus.mailer.Mailer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Instant;

@ApplicationScoped
public class VerificationMailService {

    @Inject
    Mailer mailer;

    public void sendVerificationCode(User user, String code, Instant expiresAt) {
        String recipient = user.email;
        String displayName = user.name == null || user.name.isBlank() ? "usuario" : user.name;
        String body = """
                Ola %s,

                Seu codigo de verificacao da Unify e: %s

                Este codigo expira em %s UTC.
                Se voce nao solicitou este cadastro, ignore este email.
                """.formatted(displayName, code, expiresAt);

        mailer.send(Mail.withText(recipient, "Codigo de verificacao da conta", body));
    }
}