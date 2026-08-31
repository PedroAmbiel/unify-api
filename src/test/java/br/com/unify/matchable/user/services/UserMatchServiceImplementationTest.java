package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
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

    @Test
    @DisplayName("Perfis idênticos com preferências SIMILAR chegam ao topo da escala")
    void identicalProfilesScoreNearOneHundred() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();
        UserProfile profile = buildCurrentProfile();
        UserMatchPreference preference = buildCurrentPreference();

        double score = service.calculateCompatibilityScore(
                profile, preference, 30,
                profile, preference, 30,   // literalmente o mesmo perfil
                0d);

        assertTrue(score >= 95d, "perfis idênticos deveriam pontuar >= 95, foi " + score);
    }

    @Test
    @DisplayName("Perfis opostos com preferências SIMILAR ficam na faixa baixa")
    void oppositeProfilesScoreLow() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        double score = service.calculateCompatibilityScore(
                buildCurrentProfile(), buildCurrentPreference(), 29,
                buildOppositeProfile(), buildOppositeSimilarPreference(), 55,
                110d);

        assertTrue(score <= 25d, "perfis opostos deveriam pontuar <= 25, foi " + score);
    }

    @Test
    @DisplayName("Perfil parcial (só comunicação e interesses) é avaliado sem punição pelos campos vazios")
    void partialProfileIsNormalizedByCoverage() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserProfile current = new UserProfile();
        current.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1), communicationForm(2)));
        current.interestTypes = new LinkedHashSet<>(Set.of(interestType(1), interestType(2)));

        UserProfile candidate = new UserProfile();
        candidate.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1)));
        candidate.interestTypes = new LinkedHashSet<>(Set.of(interestType(1), interestType(2)));

        double score = service.calculateCompatibilityScore(current, null, 30, candidate, null, 31, 5d);

        // cobertura = (22 + 18 + 12 + 4 + 3) / 100 = 0,59 -> acima do piso de confiança
        // (acessibilidade entra por ausência mútua declarada, ver B3.4)
        assertTrue(score > 60d, "perfil parcial com sinais fortes deveria pontuar > 60, foi " + score);
        assertTrue(score <= 100d);
    }

    @Test
    @DisplayName("Comunicação: 1 canal em comum já garante o peso cheio, mesmo com conjuntos desbalanceados")
    void communicationRewardsViabilityNotSetSimilarity() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserProfile flexible = new UserProfile();   // 5 formas de comunicação
        flexible.communicationForms = new LinkedHashSet<>(Set.of(
                communicationForm(1), communicationForm(2), communicationForm(3),
                communicationForm(4), communicationForm(5)));

        UserProfile textOnly = new UserProfile();   // 1 forma, presente no conjunto do outro
        textOnly.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1)));

        double score = service.calculateCompatibilityScore(flexible, null, 30, textOnly, null, 30, 5d);

        // Com a fórmula antiga (shared / max) isso daria 1/5 = 20% do peso de comunicação.
        // Com a nova (viabilidade), 1 canal comum -> 60% + 40% * (1/1) = 100% do peso.
        assertTrue(score >= 90d,
                "quem tem MAIS formas de comunicação não pode ser penalizado; score foi " + score);
    }

    @Test
    @DisplayName("Sem canal de comunicação em comum o score cai de verdade")
    void noSharedCommunicationChannelIsPenalized() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserProfile current = new UserProfile();
        current.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1)));
        current.interestTypes = new LinkedHashSet<>(Set.of(interestType(1)));

        UserProfile withoutSharedChannel = new UserProfile();
        withoutSharedChannel.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(9)));
        withoutSharedChannel.interestTypes = new LinkedHashSet<>(Set.of(interestType(1)));

        UserProfile withSharedChannel = new UserProfile();
        withSharedChannel.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1)));
        withSharedChannel.interestTypes = new LinkedHashSet<>(Set.of(interestType(1)));

        double penalizedScore = service.calculateCompatibilityScore(
                current, null, 30, withoutSharedChannel, null, 30, 5d);
        double viableScore = service.calculateCompatibilityScore(
                current, null, 30, withSharedChannel, null, 30, 5d);

        // O fator de comunicação é avaliável e zerado: 22 pontos entram no denominador
        // e não no numerador, derrubando o score em relação ao par com canal em comum.
        assertTrue(penalizedScore < viableScore,
                "sem canal comum o score deveria cair; " + penalizedScore + " vs " + viableScore);
        assertTrue(penalizedScore < 70d, "sem canal comum o score deveria cair; foi " + penalizedScore);
    }

    @Test
    @DisplayName("Dois perfis sem necessidades de acessibilidade são compatíveis nesse eixo")
    void bothWithoutAccessibilityNeedsAreCompatible() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserProfile current = buildCurrentProfile();
        current.accessibilityNeeds = new LinkedHashSet<>();
        UserProfile candidate = buildCompatibleCandidateProfile();
        candidate.accessibilityNeeds = new LinkedHashSet<>();

        double score = service.calculateCompatibilityScore(
                current, buildCurrentPreference(), 29,
                candidate, buildCompatibleCandidatePreference(), 28, 6d);

        assertTrue(score >= 75d, "ausência mútua de necessidades não pode zerar o fator; foi " + score);
    }

    @Test
    @DisplayName("Sem coordenadas o fator distância sai da conta em vez de valer 1 ponto arbitrário")
    void nullDistanceIsNotApplicable() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        double withDistance = service.calculateCompatibilityScore(
                buildCurrentProfile(), buildCurrentPreference(), 29,
                buildCompatibleCandidateProfile(), buildCompatibleCandidatePreference(), 28, 5d);

        double withoutDistance = service.calculateCompatibilityScore(
                buildCurrentProfile(), buildCurrentPreference(), 29,
                buildCompatibleCandidateProfile(), buildCompatibleCandidatePreference(), 28,
                (Double) null, UserMatchServiceImplementation.ScoringContext.empty());

        // Sem distância o score não desaba: o peso 4 sai do numerador E do denominador.
        assertTrue(Math.abs(withDistance - withoutDistance) < 8d,
                "ausência de coordenada não pode derrubar o score; " + withDistance + " vs " + withoutDistance);
    }

    @Test
    @DisplayName("Score de baixa cobertura é limitado ao teto de confiança")
    void lowCoverageScoreIsCapped() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserProfile current = new UserProfile();
        current.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1)));

        UserProfile candidate = new UserProfile();
        candidate.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(1)));

        // cobertura = (22 + 12) / 100 = 0,34 < 0,40 -> teto de 70
        double score = service.calculateCompatibilityScore(
                current, null, null, candidate, null, null, (Double) null,
                UserMatchServiceImplementation.ScoringContext.empty());

        assertTrue(score <= MatchScoringPolicy.LOW_CONFIDENCE_SCORE_CAP,
                "score de baixa confiança deveria ser limitado a 70; foi " + score);
    }

    @Test
    @DisplayName("Penalidade de reciprocidade reduz o score sem eliminar o candidato")
    void reciprocityPenaltyReducesButDoesNotEliminate() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        double neutral = service.calculateCompatibilityScore(
                buildCurrentProfile(), buildCurrentPreference(), 29,
                buildCompatibleCandidateProfile(), buildCompatibleCandidatePreference(), 28,
                6d, UserMatchServiceImplementation.ScoringContext.empty());

        double penalized = service.calculateCompatibilityScore(
                buildCurrentProfile(), buildCurrentPreference(), 29,
                buildCompatibleCandidateProfile(), buildCompatibleCandidatePreference(), 28,
                6d, new UserMatchServiceImplementation.ScoringContext(true, true, false));

        assertEquals(13d, neutral - penalized, 0.01d);   // 8 + 5
        assertTrue(penalized > 0d, "a penalidade reduz o ranking, nunca elimina o perfil");
    }

    @Test
    @DisplayName("Tipos de conexão parcialmente compatíveis valem metade do peso")
    void partiallyCompatibleConnectionTypesScoreHalfWeight() {
        UserMatchServiceImplementation service = new UserMatchServiceImplementation();

        UserMatchPreference friendship = buildCurrentPreference();          // 1 = Amizade
        UserMatchPreference networking = buildCompatibleCandidatePreference();
        networking.connectionType = connectionType(3);                      // 3 = Networking
        UserMatchPreference relationship = buildCompatibleCandidatePreference();
        relationship.connectionType = connectionType(2);                    // 2 = Relacionamento

        double exact = service.calculateCompatibilityScore(
                buildCurrentProfile(), friendship, 29,
                buildCompatibleCandidateProfile(), buildCompatibleCandidatePreference(), 28, 6d);
        double partial = service.calculateCompatibilityScore(
                buildCurrentProfile(), friendship, 29,
                buildCompatibleCandidateProfile(), networking, 28, 6d);
        double incompatible = service.calculateCompatibilityScore(
                buildCurrentProfile(), friendship, 29,
                buildCompatibleCandidateProfile(), relationship, 28, 6d);

        assertEquals(MatchScoringPolicy.WEIGHT_CONNECTION_TYPE / 2d, exact - partial, 0.01d);
        assertEquals(MatchScoringPolicy.WEIGHT_CONNECTION_TYPE, exact - incompatible, 0.01d);
    }

    @Test
    @DisplayName("Todo id do mapa de compatibilidade parcial existe no catálogo connection_types")
    void partialConnectionTypeMapOnlyReferencesCatalogIds() {
        // Catálogo real de src/main/resources/import.sql:
        // 1 = Amizade, 2 = Relacionamento, 3 = Networking, 4 = Comunidade.
        Set<Integer> catalogIds = Set.of(1, 2, 3, 4);

        for (Map.Entry<Integer, Set<Integer>> entry : UserMatchServiceImplementation.COMPATIBLE_CONNECTION_TYPES.entrySet()) {
            assertTrue(catalogIds.contains(entry.getKey()),
                    "id " + entry.getKey() + " não existe em connection_types");
            for (Integer compatibleId : entry.getValue()) {
                assertTrue(catalogIds.contains(compatibleId),
                        "id " + compatibleId + " não existe em connection_types");
                assertTrue(UserMatchServiceImplementation.COMPATIBLE_CONNECTION_TYPES
                                .getOrDefault(compatibleId, Set.of())
                                .contains(entry.getKey()),
                        "o mapa de compatibilidade parcial precisa ser simétrico: "
                                + entry.getKey() + " <-> " + compatibleId);
            }
        }

        assertTrue(UserMatchServiceImplementation.COMPATIBLE_CONNECTION_TYPES
                        .getOrDefault(2, Set.of()).isEmpty(),
                "Relacionamento (2) não pode ter par parcial com tipos platônicos");
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

    /** Perfil com ids de lookup completamente disjuntos dos de buildCurrentProfile(). */
    private UserProfile buildOppositeProfile() {
        UserProfile profile = new UserProfile();
        profile.communicationForms = new LinkedHashSet<>(Set.of(communicationForm(8), communicationForm(9)));
        profile.accessibilityNeeds = new LinkedHashSet<>(Set.of(accessibilityNeed(7)));
        profile.autonomyLevel = autonomyLevel(3);
        profile.interestTypes = new LinkedHashSet<>(Set.of(interestType(8), interestType(9)));
        profile.lifestyleTypes = new LinkedHashSet<>(Set.of(lifestyleType(7)));
        profile.loveLanguages = new LinkedHashSet<>(Set.of(loveLanguage(7)));
        profile.energyLevel = energyLevel(5);
        return profile;
    }

    /** Preferências SIMILAR: o oposto é punido justamente por procurar semelhança. */
    private UserMatchPreference buildOppositeSimilarPreference() {
        UserMatchPreference preference = new UserMatchPreference();
        preference.accessibilityNeedSimilarity = SimilarityPreference.SIMILAR;
        preference.autonomyCompatibility = SimilarityPreference.SIMILAR;
        preference.connectionType = connectionType(2);
        preference.lifestyleSimilarity = SimilarityPreference.SIMILAR;
        preference.loveLanguageSimilarity = SimilarityPreference.SIMILAR;
        preference.energyLevelSimilarity = SimilarityPreference.SIMILAR;
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