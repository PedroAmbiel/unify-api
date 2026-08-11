package br.com.unify.matchable.user.services;

import java.time.Instant;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.dto.UserAccessibilitySettingsResponse;
import br.com.unify.matchable.user.dto.UserAccessibilitySettingsUpsertRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserAccessibilitySettings;
import br.com.unify.matchable.user.enums.FontScaleOption;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserAccessibilitySettingsServiceImplementation implements UserAccessibilitySettingsService {

    private static final String REQUEST_REQUIRED_MESSAGE = "Corpo da requisição de acessibilidade não informado";

    @Override
    public UserAccessibilitySettingsResponse getSettings(User user) {
        UserAccessibilitySettings settings = UserAccessibilitySettings.findByUser(user);
        return settings == null ? defaultResponse() : toResponse(settings);
    }

    @Override
    @Transactional
    public UserAccessibilitySettingsResponse saveSettings(User user, UserAccessibilitySettingsUpsertRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(REQUEST_REQUIRED_MESSAGE);
        }

        UserAccessibilitySettings settings = findOrCreate(user);
        settings.fontScale = request.fontScale() != null ? request.fontScale() : FontScaleOption.MEDIUM;
        settings.highContrast = Boolean.TRUE.equals(request.highContrast());
        settings.screenReaderOptimized = Boolean.TRUE.equals(request.screenReaderOptimized());
        settings.reduceMotion = Boolean.TRUE.equals(request.reduceMotion());
        settings.lastUpdatedAt = Instant.now();

        if (!settings.isPersistent()) {
            settings.persist();
        }

        return toResponse(settings);
    }

    private UserAccessibilitySettings findOrCreate(User user) {
        UserAccessibilitySettings settings = UserAccessibilitySettings.findByUser(user);
        if (settings != null) {
            return settings;
        }

        settings = new UserAccessibilitySettings();
        settings.id = UUIDv7Generator.generate();
        settings.user = user;
        return settings;
    }

    private UserAccessibilitySettingsResponse toResponse(UserAccessibilitySettings settings) {
        return new UserAccessibilitySettingsResponse(
                settings.fontScale,
                settings.fontScale.getMultiplier(),
                settings.highContrast,
                settings.screenReaderOptimized,
                settings.reduceMotion
        );
    }

    private UserAccessibilitySettingsResponse defaultResponse() {
        return new UserAccessibilitySettingsResponse(
                FontScaleOption.MEDIUM,
                FontScaleOption.MEDIUM.getMultiplier(),
                false,
                false,
                false
        );
    }
}
