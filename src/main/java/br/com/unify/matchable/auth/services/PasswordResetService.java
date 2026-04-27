package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.auth.entity.PasswordResetToken;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.services.ServicesUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class PasswordResetService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_SIZE_BYTES = 64;

    @ConfigProperty(name = "unify.mail.password-reset.expiration-hours", defaultValue = "1")
    long passwordResetExpirationHours;

    @ConfigProperty(name = "unify.frontend.password-reset-url", defaultValue = "http://localhost:8081/reset-password")
    String passwordResetUrl;

    @Inject
    PasswordResetMailService passwordResetMailService;

    @Inject
    ServicesUser servicesUser;

    @Inject
    TokenService tokenService;

    public void issueResetLink(String email) {
        User user = servicesUser.findByEmail(email);
        if (user == null) {
            return;
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(passwordResetExpirationHours));
        String rawToken = generateToken();

        PasswordResetToken.disablePendingForUser(user, now);

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.id = UUIDv7Generator.generate();
        passwordResetToken.user = user;
        passwordResetToken.tokenHash = TokenService.hashToken(rawToken);
        passwordResetToken.createdAt = now;
        passwordResetToken.expiresAt = expiresAt;
        passwordResetToken.persist();

        passwordResetMailService.sendResetLink(user, buildResetUrl(rawToken), expiresAt);
    }

    public boolean resetPassword(String rawToken, String newPassword) {
        String normalizedToken = normalizeToken(rawToken);
        if (normalizedToken == null) {
            return false;
        }

        Instant now = Instant.now();
        PasswordResetToken passwordResetToken = PasswordResetToken.findUsableToken(
                TokenService.hashToken(normalizedToken),
                now
        );
        if (passwordResetToken == null) {
            return false;
        }

        passwordResetToken.consumedAt = now;
        PasswordResetToken.disablePendingForUser(passwordResetToken.user, now);
        servicesUser.updatePassword(passwordResetToken.user, newPassword, now);
        tokenService.revokeAllConnections(passwordResetToken.user);
        return true;
    }

    private String buildResetUrl(String rawToken) {
        String separator = passwordResetUrl.contains("?") ? "&" : "?";
        return passwordResetUrl + separator + "token=" + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    private String generateToken() {
        byte[] randomBytes = new byte[TOKEN_SIZE_BYTES];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private String normalizeToken(String rawToken) {
        if (rawToken == null) {
            return null;
        }

        String normalizedToken = rawToken.trim();
        return normalizedToken.isEmpty() ? null : normalizedToken;
    }
}