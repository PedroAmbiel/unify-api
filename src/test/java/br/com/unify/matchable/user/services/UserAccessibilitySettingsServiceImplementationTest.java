package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.user.dto.UserAccessibilitySettingsResponse;
import br.com.unify.matchable.user.dto.UserAccessibilitySettingsUpsertRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserAccessibilitySettings;
import br.com.unify.matchable.user.enums.FontScaleOption;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;

/**
 * Este teste exige um Quarkus real (CDI + banco Postgres) porque
 * {@link UserAccessibilitySettingsServiceImplementation} depende diretamente de
 * métodos estáticos do Panache ({@code UserAccessibilitySettings.findByUser},
 * {@code persist}), que só funcionam com um EntityManager/transação ativos.
 * Diferente dos demais testes de serviço deste pacote (que evitam tocar o
 * Panache e testam apenas lógica pura via reflection), aqui não há como
 * isolar o comportamento de "criar x atualizar" sem uma persistência real.
 */
@QuarkusTest
class UserAccessibilitySettingsServiceImplementationTest {

    @Inject
    UserAccessibilitySettingsServiceImplementation service;

    @Test
    void getSettingsReturnsDefaultsWhenThereIsNoStoredRecord() {
        User user = persistUser("no-settings-" + UUID.randomUUID() + "@example.com");

        UserAccessibilitySettingsResponse response = QuarkusTransaction.requiringNew()
                .call(() -> service.getSettings(User.findById(user.id)));

        assertEquals(FontScaleOption.MEDIUM, response.fontScale());
        assertEquals(FontScaleOption.MEDIUM.getMultiplier(), response.fontScaleMultiplier());
        assertFalse(response.highContrast());
        assertFalse(response.screenReaderOptimized());
        assertFalse(response.reduceMotion());
    }

    @Test
    void saveSettingsCreatesOnFirstCallAndUpdatesWithoutDuplicatingOnSecondCall() {
        User user = persistUser("save-settings-" + UUID.randomUUID() + "@example.com");

        UserAccessibilitySettingsUpsertRequest firstRequest =
                new UserAccessibilitySettingsUpsertRequest(FontScaleOption.LARGE, true, true, false);
        UserAccessibilitySettingsResponse firstResponse = QuarkusTransaction.requiringNew()
                .call(() -> service.saveSettings(User.findById(user.id), firstRequest));

        assertEquals(FontScaleOption.LARGE, firstResponse.fontScale());
        assertTrue(firstResponse.highContrast());
        assertEquals(1L, countSettingsForUser(user));

        UserAccessibilitySettingsUpsertRequest secondRequest =
                new UserAccessibilitySettingsUpsertRequest(FontScaleOption.EXTRA_LARGE, false, true, true);
        UserAccessibilitySettingsResponse secondResponse = QuarkusTransaction.requiringNew()
                .call(() -> service.saveSettings(User.findById(user.id), secondRequest));

        assertEquals(FontScaleOption.EXTRA_LARGE, secondResponse.fontScale());
        assertFalse(secondResponse.highContrast());
        assertTrue(secondResponse.screenReaderOptimized());
        assertTrue(secondResponse.reduceMotion());
        assertEquals(1L, countSettingsForUser(user));
    }

    @Test
    void saveSettingsRejectsNullRequest() {
        User user = persistUser("null-request-" + UUID.randomUUID() + "@example.com");

        assertThrows(IllegalArgumentException.class, () -> QuarkusTransaction.requiringNew()
                .call(() -> service.saveSettings(User.findById(user.id), null)));
    }

    private long countSettingsForUser(User user) {
        return QuarkusTransaction.requiringNew()
                .call(() -> UserAccessibilitySettings.count("user.id = ?1", user.id));
    }

    private User persistUser(String email) {
        return QuarkusTransaction.requiringNew().call(() -> {
            User user = new User();
            user.id = UUID.randomUUID();
            user.name = "Teste";
            user.lastName = "Acessibilidade";
            user.email = email;
            user.password = "hash-nao-usado-neste-teste";
            user.birthdate = LocalDate.now().minusYears(25);
            user.verified = true;
            user.lastUpdatedAt = Instant.now();
            user.persist();
            return user;
        });
    }
}
