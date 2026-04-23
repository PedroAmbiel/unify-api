package br.com.unify.matchable.auth.services;

import br.com.unify.matchable.auth.dto.TokenResponse;
import br.com.unify.matchable.auth.entity.ActiveConnection;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.entity.User;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;

@ApplicationScoped
public class TokenService {

    @ConfigProperty(name = "unify.jwt.access-token.expiration-minutes", defaultValue = "60")
    long accessTokenExpirationMinutes;

    @ConfigProperty(name = "unify.jwt.refresh-token.expiration-days", defaultValue = "30")
    long refreshTokenExpirationDays;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public TokenResponse generateTokens(User user, String deviceInfo, String ipAddress) {
        String accessToken = generateAccessToken(user);
        String refreshToken = generateRefreshToken();

        ActiveConnection connection = new ActiveConnection();
        connection.id = UUIDv7Generator.generate();
        connection.user = user;
        connection.refreshToken = hashToken(refreshToken);
        connection.deviceInfo = deviceInfo;
        connection.ipAddress = ipAddress;
        connection.createdAt = Instant.now();
        connection.expiresAt = Instant.now().plus(Duration.ofDays(refreshTokenExpirationDays));
        connection.revoked = false;
        connection.persist();

        return new TokenResponse(
                accessToken,
                refreshToken,
                accessTokenExpirationMinutes * 60
        );
    }

    public TokenResponse refreshTokens(String refreshToken, String deviceInfo, String ipAddress) {
        String tokenHash = hashToken(refreshToken);
        ActiveConnection connection = ActiveConnection.findByRefreshTokenHash(tokenHash);

        if (connection == null || connection.expiresAt.isBefore(Instant.now())) {
            return null;
        }

        connection.revoked = true;
        connection.persist();

        return generateTokens(connection.user, deviceInfo, ipAddress);
    }

    public void revokeAllConnections(User user) {
        ActiveConnection.revokeAllForUser(user);
    }

    private String generateAccessToken(User user) {
        return Jwt.issuer("https://unify.com.br")
                .subject(user.id.toString())
                .groups(Set.of("user"))
                .claim("name", user.name)
                .claim("lastName", user.lastName)
                .expiresIn(Duration.ofMinutes(accessTokenExpirationMinutes))
                .sign();
    }

    private String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
