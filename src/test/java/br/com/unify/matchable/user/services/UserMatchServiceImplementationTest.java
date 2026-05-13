package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.user.entity.AccessibilityNeed;
import br.com.unify.matchable.user.entity.AutonomyLevel;
import br.com.unify.matchable.user.entity.CommunicationForm;
import br.com.unify.matchable.user.entity.ConnectionType;
import br.com.unify.matchable.user.entity.EnergyLevel;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.LifestyleType;
import br.com.unify.matchable.user.entity.LoveLanguage;
import br.com.unify.matchable.user.entity.UserMatchPreference;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.enums.SimilarityPreference;

class UserMatchServiceImplementationTest {

    @Test
    void calculateCompatibilityScoreRewardsSharedSignalsAndCloserDistance() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserProfile currentProfile = buildCurrentProfile();
        UserMatchPreference currentPreference = buildCurrentPreference();

        UserProfile strongCandidate = buildCompatibleCandidateProfile();
        UserMatchPreference strongCandidatePreference = buildCompatibleCandidatePreference();

        UserProfile weakCandidate = buildWeakCandidateProfile();
        UserMatchPreference weakCandidatePreference = buildWeakCandidatePreference();

        double strongScore = service.calculateCompatibilityScore(
                currentProfile,
                currentPreference,
                29,
                strongCandidate,
                strongCandidatePreference,
                28,
                6d
        );

        double weakScore = service.calculateCompatibilityScore(
                currentProfile,
                currentPreference,
                29,
                weakCandidate,
                weakCandidatePreference,
                40,
                45d
        );

        assertTrue(strongScore > weakScore);
        assertTrue(strongScore >= 70d);
        assertTrue(weakScore < 40d);
    }

    @Test
    void buildOrganicFeedKeepsPriorityProfilesAndAvoidsDuplicates() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UUID rankedOne = UUID.randomUUID();
        UUID rankedTwo = UUID.randomUUID();
        UUID rankedThree = UUID.randomUUID();
        UUID rankedFour = UUID.randomUUID();
        UUID discoveryOne = UUID.randomUUID();
        UUID priorityNew = UUID.randomUUID();

        List<UUID> feed = service.buildOrganicFeed(
                List.of(rankedOne, rankedTwo, rankedThree, rankedFour),
                List.of(discoveryOne),
                List.of(priorityNew, rankedTwo),
                5
        );

        assertEquals(5, feed.size());
        assertTrue(feed.contains(priorityNew));
        assertTrue(feed.contains(rankedTwo));
        assertEquals(feed.size(), new LinkedHashSet<>(feed).size());
        assertNotEquals(List.of(rankedOne, rankedTwo, rankedThree, rankedFour, discoveryOne), feed);
    }

    private UserProfile buildCurrentProfile() {
        UserProfile profile = new UserProfile();
        profile.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1), communicationForm(2)));
        profile.accessibilityNeeds = new LinkedHashSet<>(Set.of(accessibilityNeed(1)));
        profile.autonomyLevel = autonomyLevel(1);
        profile.interestTypes = new LinkedHashSet<>(Set.of(interestType(1), interestType(2)));
        profile.lifestyleTypes = new LinkedHashSet<>(Set.of(lifestyleType(1)));
        profile.loveLanguages = new LinkedHashSet<>(Set.of(loveLanguage(1)));
        profile.energyLevel = energyLevel(2);
        return profile;
    }

    private UserMatchPreference buildCurrentPreference() {
        UserMatchPreference preference = new UserMatchPreference();
        preference.accessibilityNeedSimilarity = SimilarityPreference.SIMILAR;
        preference.autonomyCompatibility = SimilarityPreference.SIMILAR;
        preference.connectionType = connectionType(1);
        preference.lifestyleSimilarity = SimilarityPreference.SIMILAR;
        preference.loveLanguageSimilarity = SimilarityPreference.SIMILAR;
        preference.energyLevelSimilarity = SimilarityPreference.SIMILAR;
        return preference;
    }

    private UserProfile buildCompatibleCandidateProfile() {
        UserProfile profile = new UserProfile();
        profile.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1), communicationForm(2)));
        profile.accessibilityNeeds = new LinkedHashSet<>(Set.of(accessibilityNeed(1)));
        profile.autonomyLevel = autonomyLevel(1);
        profile.interestTypes = new LinkedHashSet<>(Set.of(interestType(1), interestType(3)));
        profile.lifestyleTypes = new LinkedHashSet<>(Set.of(lifestyleType(1)));
        profile.loveLanguages = new LinkedHashSet<>(Set.of(loveLanguage(1)));
        profile.energyLevel = energyLevel(2);
        return profile;
    }

    private UserMatchPreference buildCompatibleCandidatePreference() {
        UserMatchPreference preference = new UserMatchPreference();
        preference.accessibilityNeedSimilarity = SimilarityPreference.SIMILAR;
        preference.autonomyCompatibility = SimilarityPreference.SIMILAR;
        preference.connectionType = connectionType(1);
        preference.lifestyleSimilarity = SimilarityPreference.SIMILAR;
        preference.loveLanguageSimilarity = SimilarityPreference.SIMILAR;
        preference.energyLevelSimilarity = SimilarityPreference.SIMILAR;
        return preference;
    }

    private UserProfile buildWeakCandidateProfile() {
        UserProfile profile = new UserProfile();
        profile.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(3)));
        profile.accessibilityNeeds = new LinkedHashSet<>(Set.of(accessibilityNeed(2)));
        profile.autonomyLevel = autonomyLevel(3);
        profile.interestTypes = new LinkedHashSet<>(Set.of(interestType(4)));
        profile.lifestyleTypes = new LinkedHashSet<>(Set.of(lifestyleType(3)));
        profile.loveLanguages = new LinkedHashSet<>(Set.of(loveLanguage(4)));
        profile.energyLevel = energyLevel(1);
        return profile;
    }

    private UserMatchPreference buildWeakCandidatePreference() {
        UserMatchPreference preference = new UserMatchPreference();
        preference.accessibilityNeedSimilarity = SimilarityPreference.DIFFERENT;
        preference.autonomyCompatibility = SimilarityPreference.DIFFERENT;
        preference.connectionType = connectionType(2);
        preference.lifestyleSimilarity = SimilarityPreference.DIFFERENT;
        preference.loveLanguageSimilarity = SimilarityPreference.DIFFERENT;
        preference.energyLevelSimilarity = SimilarityPreference.DIFFERENT;
        return preference;
    }

    private CommunicationForm communicationForm(int id) {
        CommunicationForm value = new CommunicationForm();
        value.id = id;
        return value;
    }

    private AccessibilityNeed accessibilityNeed(int id) {
        AccessibilityNeed value = new AccessibilityNeed();
        value.id = id;
        return value;
    }

    private AutonomyLevel autonomyLevel(int id) {
        AutonomyLevel value = new AutonomyLevel();
        value.id = id;
        return value;
    }

    private InterestType interestType(int id) {
        InterestType value = new InterestType();
        value.id = id;
        return value;
    }

    private LifestyleType lifestyleType(int id) {
        LifestyleType value = new LifestyleType();
        value.id = id;
        return value;
    }

    private LoveLanguage loveLanguage(int id) {
        LoveLanguage value = new LoveLanguage();
        value.id = id;
        return value;
    }

    private EnergyLevel energyLevel(int id) {
        EnergyLevel value = new EnergyLevel();
        value.id = id;
        return value;
    }

    private ConnectionType connectionType(int id) {
        ConnectionType value = new ConnectionType();
        value.id = id;
        return value;
    }
}