package br.com.unify.matchable.user.services;

import br.com.unify.matchable.common.exceptions.ValidationException;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServicesUserImplementationTest {

    @Test
    void updatePasswordHashesPasswordAndUpdatesTimestamp() {
        ServicesUserImplementation service = new ServicesUserImplementation();
        User user = new User();
        Instant updatedAt = Instant.parse("2026-04-27T12:00:00Z");

        service.updatePassword(user, "NovaSenha@123", updatedAt);

        assertTrue(BcryptUtil.matches("NovaSenha@123", user.password));
        assertEquals(updatedAt, user.lastUpdatedAt);
    }

    @Test
    void updatePasswordRejectsWeakPasswords() {
        ServicesUserImplementation service = new ServicesUserImplementation();
        User user = new User();

        assertThrows(ValidationException.class, () -> service.updatePassword(user, "12345678", Instant.now()));
    }
}