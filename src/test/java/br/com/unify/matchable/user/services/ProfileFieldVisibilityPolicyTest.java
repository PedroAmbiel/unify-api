package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.user.enums.ProfileField;
import br.com.unify.matchable.user.enums.ProfileFieldVisibility;

class ProfileFieldVisibilityPolicyTest {

    @Test
    void publicConfigurationHidesNothingAndNeverAsksAboutFollows() {
        AtomicInteger lookups = new AtomicInteger();

        Set<ProfileField> hidden = ProfileFieldVisibilityPolicy.hiddenFields(
                configuration(Map.of()), relationship(true, true, lookups));

        assertTrue(hidden.isEmpty());
        assertEquals(0, lookups.get(), "sem parte restrita, nenhuma consulta de follow");
    }

    @Test
    void eachModeDependsOnTheRightDirectionOfTheFollow() {
        Map<ProfileField, ProfileFieldVisibility> configuration = configuration(Map.of(
                ProfileField.GENDER, ProfileFieldVisibility.HIDDEN,
                ProfileField.INTEREST_TYPES, ProfileFieldVisibility.FOLLOWERS_ONLY,
                ProfileField.BIO, ProfileFieldVisibility.FOLLOWING_ONLY
        ));

        assertEquals(Set.of(ProfileField.GENDER, ProfileField.INTEREST_TYPES, ProfileField.BIO),
                ProfileFieldVisibilityPolicy.hiddenFields(configuration, relationship(false, false, null)));
        assertEquals(Set.of(ProfileField.GENDER, ProfileField.BIO),
                ProfileFieldVisibilityPolicy.hiddenFields(configuration, relationship(true, false, null)),
                "quem me segue vê o que é só para seguidores");
        assertEquals(Set.of(ProfileField.GENDER, ProfileField.INTEREST_TYPES),
                ProfileFieldVisibilityPolicy.hiddenFields(configuration, relationship(false, true, null)),
                "quem eu sigo vê o que é só para quem eu sigo");
        assertEquals(Set.of(ProfileField.GENDER),
                ProfileFieldVisibilityPolicy.hiddenFields(configuration, relationship(true, true, null)),
                "HIDDEN vale para todos");
    }

    @Test
    void followLookupsHappenAtMostOncePerDirection() {
        AtomicInteger lookups = new AtomicInteger();
        Map<ProfileField, ProfileFieldVisibility> configuration = configuration(Map.of(
                ProfileField.GENDER, ProfileFieldVisibility.FOLLOWERS_ONLY,
                ProfileField.PRONOUNS, ProfileFieldVisibility.FOLLOWERS_ONLY,
                ProfileField.BIO, ProfileFieldVisibility.FOLLOWING_ONLY,
                ProfileField.GALLERY, ProfileFieldVisibility.FOLLOWING_ONLY
        ));

        ProfileFieldVisibilityPolicy.hiddenFields(configuration, relationship(true, false, lookups));

        assertEquals(2, lookups.get());
    }

    private static Map<ProfileField, ProfileFieldVisibility> configuration(Map<ProfileField, ProfileFieldVisibility> overrides) {
        Map<ProfileField, ProfileFieldVisibility> configuration = new EnumMap<>(ProfileField.class);
        for (ProfileField field : ProfileField.values()) {
            configuration.put(field, ProfileFieldVisibility.PUBLIC);
        }
        configuration.putAll(overrides);
        return configuration;
    }

    private static ProfileFieldVisibilityPolicy.Relationship relationship(
            boolean viewerFollowsOwner, boolean ownerFollowsViewer, AtomicInteger lookups) {
        return new ProfileFieldVisibilityPolicy.Relationship() {
            @Override
            public boolean viewerFollowsOwner() {
                if (lookups != null) {
                    lookups.incrementAndGet();
                }
                return viewerFollowsOwner;
            }

            @Override
            public boolean ownerFollowsViewer() {
                if (lookups != null) {
                    lookups.incrementAndGet();
                }
                return ownerFollowsViewer;
            }
        };
    }
}
