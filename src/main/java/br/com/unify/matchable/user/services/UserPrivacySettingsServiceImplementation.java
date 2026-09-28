package br.com.unify.matchable.user.services;

import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.social.services.SocialService;
import br.com.unify.matchable.user.dto.UserPrivacySettingsResponse;
import br.com.unify.matchable.user.dto.UserPrivacySettingsUpdateRequest;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserPrivacySettings;
import br.com.unify.matchable.user.entity.UserProfileFieldVisibility;
import br.com.unify.matchable.user.enums.FeedVisibility;
import br.com.unify.matchable.user.enums.ProfileField;
import br.com.unify.matchable.user.enums.ProfileFieldVisibility;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserPrivacySettingsServiceImplementation implements UserPrivacySettingsService {

    static final String REQUEST_REQUIRED_MESSAGE = "Corpo da requisição de privacidade não informado";
    static final String INVALID_FEED_VISIBILITY_MESSAGE = "feedVisibility inválido. Use PUBLIC ou FOLLOWERS_ONLY";
    static final String INVALID_PROFILE_FIELD_MESSAGE = "Parte do perfil inválida em profileFieldVisibility: ";
    static final String INVALID_PROFILE_FIELD_VISIBILITY_MESSAGE =
            "Visibilidade inválida em profileFieldVisibility. Use PUBLIC, FOLLOWERS_ONLY, FOLLOWING_ONLY ou HIDDEN";

    @Inject
    SocialService socialService;

    /**
     * Leitura NÃO cria linha: quem só abriu a tela continua sem registro e
     * vale como default — o mesmo que discovery e feed já assumem.
     */
    @Override
    public UserPrivacySettingsResponse getSettings(User user) {
        return toResponse(UserPrivacySettings.findByUser(user), UserProfileFieldVisibility.resolveAll(user));
    }

    @Override
    @Transactional
    public UserPrivacySettingsResponse saveSettings(User user, UserPrivacySettingsUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(REQUEST_REQUIRED_MESSAGE);
        }

        // Valida tudo antes de gravar: um PUT inválido não deixa registro pela metade.
        FeedVisibility feedVisibility = request.feedVisibility() == null
                ? null
                : parseFeedVisibility(request.feedVisibility());
        Map<ProfileField, ProfileFieldVisibility> fieldChanges = parseFieldVisibility(request.profileFieldVisibility());

        UserPrivacySettings settings = UserPrivacySettings.findByUser(user);
        boolean touchesSettings = request.discoverable() != null
                || request.showDistance() != null
                || request.showAge() != null
                || request.followApprovalRequired() != null
                || feedVisibility != null;
        if (touchesSettings) {
            if (settings == null) {
                settings = createDefaults(user);
            }
            if (request.discoverable() != null) {
                settings.discoverable = request.discoverable();
            }
            if (request.showDistance() != null) {
                settings.showDistance = request.showDistance();
            }
            if (request.showAge() != null) {
                settings.showAge = request.showAge();
            }
            if (feedVisibility != null) {
                settings.feedVisibility = feedVisibility;
            }
            boolean approvalTurnedOff = settings.followApprovalRequired
                    && Boolean.FALSE.equals(request.followApprovalRequired());
            if (request.followApprovalRequired() != null) {
                settings.followApprovalRequired = request.followApprovalRequired();
            }
            settings.updatedAt = Instant.now();

            // Conta voltou a ser aberta: quem estava esperando passa a seguir (mesmo
            // comportamento de redes sociais comuns; o app avisa antes de desligar).
            if (approvalTurnedOff) {
                socialService.acceptAllPendingFollowRequests(user);
            }
        }

        fieldChanges.forEach((field, visibility) -> applyFieldVisibility(user, field, visibility));

        return toResponse(settings, UserProfileFieldVisibility.resolveAll(user));
    }

    /** PUBLIC apaga a linha (sem linha = público); o resto cria ou atualiza. */
    private void applyFieldVisibility(User user, ProfileField field, ProfileFieldVisibility visibility) {
        UserProfileFieldVisibility current = UserProfileFieldVisibility.findByUserAndField(user, field);
        if (visibility == ProfileFieldVisibility.PUBLIC) {
            if (current != null) {
                current.delete();
            }
            return;
        }
        if (current == null) {
            current = new UserProfileFieldVisibility();
            current.id = UUIDv7Generator.generate();
            current.user = user;
            current.field = field;
            current.visibility = visibility;
            current.updatedAt = Instant.now();
            current.persist();
            return;
        }
        current.visibility = visibility;
        current.updatedAt = Instant.now();
    }

    private UserPrivacySettings createDefaults(User user) {
        UserPrivacySettings created = new UserPrivacySettings();
        created.id = UUIDv7Generator.generate();
        created.user = user;
        created.updatedAt = Instant.now();
        created.persist();
        return created;
    }

    static FeedVisibility parseFeedVisibility(String value) {
        try {
            return FeedVisibility.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException(INVALID_FEED_VISIBILITY_MESSAGE, exception);
        }
    }

    static Map<ProfileField, ProfileFieldVisibility> parseFieldVisibility(Map<String, String> raw) {
        Map<ProfileField, ProfileFieldVisibility> parsed = new EnumMap<>(ProfileField.class);
        if (raw == null) {
            return parsed;
        }
        raw.forEach((key, value) -> {
            ProfileField field;
            try {
                field = ProfileField.valueOf(key == null ? "" : key.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(INVALID_PROFILE_FIELD_MESSAGE + key, exception);
            }
            try {
                parsed.put(field, ProfileFieldVisibility.valueOf(value == null ? "" : value.trim().toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(INVALID_PROFILE_FIELD_VISIBILITY_MESSAGE, exception);
            }
        });
        return parsed;
    }

    private UserPrivacySettingsResponse toResponse(
            UserPrivacySettings settings,
            Map<ProfileField, ProfileFieldVisibility> fieldVisibility
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        fieldVisibility.forEach((field, visibility) -> fields.put(field.name(), visibility.name()));

        if (settings == null) {
            return new UserPrivacySettingsResponse(
                    UserPrivacySettings.DEFAULT_DISCOVERABLE,
                    UserPrivacySettings.DEFAULT_FEED_VISIBILITY.name(),
                    UserPrivacySettings.DEFAULT_SHOW_DISTANCE,
                    UserPrivacySettings.DEFAULT_SHOW_AGE,
                    UserPrivacySettings.DEFAULT_FOLLOW_APPROVAL_REQUIRED,
                    fields
            );
        }
        return new UserPrivacySettingsResponse(
                settings.discoverable,
                settings.feedVisibility.name(),
                settings.showDistance,
                settings.showAge,
                settings.followApprovalRequired,
                fields
        );
    }
}
