package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.auth.dto.VerificationCodeDispatchResponse;
import br.com.unify.matchable.auth.entity.EmailVerificationCode;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

@ApplicationScoped
public class EmailVerificationService {

    private static final char[] VERIFICATION_CODE_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final int VERIFICATION_CODE_LENGTH = 6;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @ConfigProperty(name = "unify.mail.verification.expiration-hours", defaultValue = "3")
    long verificationExpirationHours;

    @Inject
    VerificationMailService verificationMailService;

    public boolean requiresVerification(User user) {
        return user != null && !user.verified;
    }

    public VerificationCodeDispatchResponse issueCode(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofHours(verificationExpirationHours));
        String code = generateCode();

        EmailVerificationCode.disablePendingForUser(user, now);

        EmailVerificationCode verificationCode = new EmailVerificationCode();
        verificationCode.id = UUIDv7Generator.generate();
        verificationCode.user = user;
        verificationCode.codeHash = TokenService.hashToken(normalizeCode(code));
        verificationCode.createdAt = now;
        verificationCode.expiresAt = expiresAt;
        verificationCode.persist();

        verificationMailService.sendVerificationCode(user, code, expiresAt);

        return new VerificationCodeDispatchResponse(
                user.email,
                Duration.between(now, expiresAt).toSeconds(),
                "Codigo de verificacao enviado para o email informado"
        );
    }

    public boolean verifyCode(User user, String rawCode) {
        String normalizedCode = normalizeCode(rawCode);
        if (normalizedCode == null) {
            return false;
        }

        Instant now = Instant.now();
        EmailVerificationCode verificationCode = EmailVerificationCode.findUsableCode(
                user,
                TokenService.hashToken(normalizedCode),
                now
        );
        if (verificationCode == null) {
            return false;
        }

        verificationCode.consumedAt = now;
        EmailVerificationCode.disablePendingForUser(user, now);
        user.verified = true;
        user.lastUpdatedAt = now;
        return true;
    }

    private String generateCode() {
        StringBuilder builder = new StringBuilder(VERIFICATION_CODE_LENGTH);
        for (int index = 0; index < VERIFICATION_CODE_LENGTH; index++) {
            builder.append(VERIFICATION_CODE_ALPHABET[SECURE_RANDOM.nextInt(VERIFICATION_CODE_ALPHABET.length)]);
        }
        return builder.toString();
    }

    private String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return null;
        }

        String normalizedCode = rawCode.trim().toUpperCase(Locale.ROOT);
        if (normalizedCode.length() != VERIFICATION_CODE_LENGTH) {
            return null;
        }

        for (int index = 0; index < normalizedCode.length(); index++) {
            char character = normalizedCode.charAt(index);
            boolean isUppercaseLetter = character >= 'A' && character <= 'Z';
            boolean isDigit = character >= '0' && character <= '9';
            if (!isUppercaseLetter && !isDigit) {
                return null;
            }
        }

        return normalizedCode;
    }
}