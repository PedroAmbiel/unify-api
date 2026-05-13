package br.com.unify.matchable.user.services;

import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.MutualMatchResponse;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.dto.UserProfileImageResponse;
import br.com.unify.matchable.user.entity.AccessibilityNeed;
import br.com.unify.matchable.user.entity.CommunicationForm;
import br.com.unify.matchable.user.entity.ConnectionType;
import br.com.unify.matchable.user.entity.EnergyLevel;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.LifestyleType;
import br.com.unify.matchable.user.entity.LoveLanguage;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserCoordinates;
import br.com.unify.matchable.user.entity.UserMatchPreference;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import br.com.unify.matchable.user.enums.SimilarityPreference;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserMatchServiceImplementation implements UserMatchService {

    private static final int TARGET_MATCH_COUNT = 50;
    private static final int PRESELECTION_LIMIT = 250;
    private static final int MINIMUM_DISCOVERY_SCORE = 30;
    private static final int PREFERRED_DISCOVERY_MAX_SCORE = 70;
    private static final int MAX_AGE_EXPANSION_YEARS = 10;
    private static final int AGE_EXPANSION_STEP_YEARS = 5;
    private static final int MINIMUM_ALLOWED_AGE = 18;
    private static final String MATCH_IMAGE_DOWNLOAD_URL_PREFIX = "/users/me/matches/images/";
    private static final String MATCH_IMAGE_NOT_FOUND_MESSAGE = "Imagem de match não encontrada";

    @Inject
    EntityManager entityManager;

    @Override
    public List<UUID> getPotentialMatches(User user, PotentialMatchesRequest request) {
        UserProfile currentProfile = requireProfile(user);
        UserMatchPreference currentPreference = requireMatchPreference(currentProfile);
        UserCoordinates currentCoordinate = requireActiveCoordinate(currentProfile);
        Integer currentAge = calculateAge(user);

        validateDiscoveryState(currentProfile, currentPreference, currentCoordinate, currentAge);

        Set<UUID> alreadyUsedProfileIds = normalizeAlreadyUsed(request);
        List<UUID> priorityInboundProfileIds = collectPriorityInboundProfileIds(currentProfile, alreadyUsedProfileIds);
        Set<UUID> excludedProfileIds = collectExcludedProfileIds(currentProfile, alreadyUsedProfileIds, priorityInboundProfileIds);
        LinkedHashMap<UUID, CandidateSearchRow> candidateRows = loadCandidateRowsWithAgeExpansion(
                currentProfile,
                currentPreference,
                currentCoordinate,
                excludedProfileIds
        );

        if (candidateRows.isEmpty() && priorityInboundProfileIds.isEmpty()) {
            return List.of();
        }

        LinkedHashMap<UUID, UserProfile> candidateProfiles = loadProfiles(candidateRows.keySet());
        List<ScoredCandidate> scoredCandidates = scoreCandidates(
                currentProfile,
                currentPreference,
                currentAge,
                candidateRows,
                candidateProfiles
        );

        int remainingSlots = Math.max(0, TARGET_MATCH_COUNT - Math.min(TARGET_MATCH_COUNT, priorityInboundProfileIds.size()));
        int rankedTarget = Math.min((remainingSlots * 4) / 5, scoredCandidates.size());

        List<UUID> rankedIds = scoredCandidates.stream()
                .limit(rankedTarget)
                .map(ScoredCandidate::profileId)
                .toList();

        int discoveryTarget = Math.max(0, remainingSlots - rankedIds.size());
        List<UUID> discoveryIds = selectDiscoveryIds(scoredCandidates, new LinkedHashSet<>(rankedIds), discoveryTarget);

        List<UUID> organicFeed = buildOrganicFeed(rankedIds, discoveryIds, priorityInboundProfileIds, TARGET_MATCH_COUNT);

        if (organicFeed.size() < TARGET_MATCH_COUNT) {
            for (ScoredCandidate scoredCandidate : scoredCandidates) {
                if (organicFeed.size() >= TARGET_MATCH_COUNT) {
                    break;
                }
                addUnique(organicFeed, scoredCandidate.profileId());
            }
        }

        if (organicFeed.size() > TARGET_MATCH_COUNT) {
            return List.copyOf(organicFeed.subList(0, TARGET_MATCH_COUNT));
        }

        return List.copyOf(organicFeed);
    }

    @Override
    @Transactional
    public MatchDecisionResponse registerDecision(User user, MatchDecisionRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Corpo da requisição de match não informado");
        }
        if (request.targetProfileId() == null) {
            throw new IllegalArgumentException("O perfil alvo do match é obrigatório");
        }
        if (request.accepted() == null) {
            throw new IllegalArgumentException("A decisão do match deve ser informada");
        }

        UserProfile currentProfile = requireProfile(user);
        UserProfile targetProfile = UserProfile.findById(request.targetProfileId());

        if (targetProfile == null) {
            throw new NoSuchElementException("Perfil de destino não encontrado");
        }
        if (Objects.equals(currentProfile.id, targetProfile.id)) {
            throw new IllegalArgumentException("Você não pode iniciar um match com o próprio perfil");
        }

        UserPossibleMatch inboundMatch = UserPossibleMatch.findByStarterAndPending(targetProfile, currentProfile);
        if (inboundMatch != null) {
            if (inboundMatch.pendingAccepted != null) {
                throw new IllegalStateException("Este match já foi respondido");
            }
            inboundMatch.pendingAccepted = request.accepted();
            return toDecisionResponse(inboundMatch);
        }

        if (!Boolean.TRUE.equals(request.accepted())) {
            throw new IllegalArgumentException("Não existe um match pendente deste perfil para registrar uma recusa");
        }

        UserPossibleMatch existingOutboundMatch = UserPossibleMatch.findByStarterAndPending(currentProfile, targetProfile);
        if (existingOutboundMatch != null) {
            throw new IllegalStateException("Você já iniciou um match com este perfil");
        }

        UserPossibleMatch possibleMatch = new UserPossibleMatch();
        possibleMatch.id = UUIDv7Generator.generate();
        possibleMatch.starterProfile = currentProfile;
        possibleMatch.pendingProfile = targetProfile;
        possibleMatch.createdAt = Instant.now();
        possibleMatch.starterAccepted = true;
        possibleMatch.pendingAccepted = null;
        possibleMatch.persist();
        return toDecisionResponse(possibleMatch);
    }

    @Override
    public List<MutualMatchResponse> getMutualMatches(User user) {
        UserProfile currentProfile = UserProfile.findByUser(user);
        if (currentProfile == null) {
            return List.of();
        }

        return UserPossibleMatch.listConfirmedForProfile(currentProfile).stream()
                .map(match -> toMutualMatchResponse(currentProfile, match))
                .toList();
    }

    @Override
    public byte[] getMatchedProfileImage(User user, UUID imageId) {
        if (imageId == null) {
            throw new NoSuchElementException(MATCH_IMAGE_NOT_FOUND_MESSAGE);
        }

        UserProfile currentProfile = UserProfile.findByUser(user);
        if (currentProfile == null) {
            throw new NoSuchElementException(MATCH_IMAGE_NOT_FOUND_MESSAGE);
        }

        UserProfileImage image = UserProfileImage.findById(imageId);
        if (image == null || !image.active || !image.profilePicture || image.userProfile == null) {
            throw new NoSuchElementException(MATCH_IMAGE_NOT_FOUND_MESSAGE);
        }

        if (!UserPossibleMatch.existsConfirmedBetween(currentProfile, image.userProfile)) {
            throw new NoSuchElementException(MATCH_IMAGE_NOT_FOUND_MESSAGE);
        }

        return readStoredImage(image);
    }

    double calculateCompatibilityScore(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            Integer currentAge,
            UserProfile candidateProfile,
            UserMatchPreference candidatePreference,
            Integer candidateAge,
            double distanceKm
    ) {
        double communicationScore = scoreSharedSet(
                currentProfile.communicationForms,
                candidateProfile.communicationForms,
                communicationForm -> communicationForm.id,
                25d
        );

        double accessibilityScore = scoreReciprocalSetPreference(
                currentProfile.accessibilityNeeds,
                candidateProfile.accessibilityNeeds,
                currentPreference == null ? null : currentPreference.accessibilityNeedSimilarity,
                candidatePreference == null ? null : candidatePreference.accessibilityNeedSimilarity,
                accessibilityNeed -> accessibilityNeed.id,
                10d,
                0.75d
        );

        double autonomyScore = scoreReciprocalLevelPreference(
                currentProfile.autonomyLevel == null ? null : currentProfile.autonomyLevel.id,
                candidateProfile.autonomyLevel == null ? null : candidateProfile.autonomyLevel.id,
                currentPreference == null ? null : currentPreference.autonomyCompatibility,
                candidatePreference == null ? null : candidatePreference.autonomyCompatibility,
                10d,
                0.75d
        );

        double interestsScore = scoreSharedSet(
                currentProfile.interestTypes,
                candidateProfile.interestTypes,
                interestType -> interestType.id,
                15d
        );

        double connectionTypeScore = scoreConnectionTypeCompatibility(currentPreference, candidatePreference);

        double lifestyleScore = scoreReciprocalSetPreference(
                currentProfile.lifestyleTypes,
                candidateProfile.lifestyleTypes,
                currentPreference == null ? null : currentPreference.lifestyleSimilarity,
                candidatePreference == null ? null : candidatePreference.lifestyleSimilarity,
                lifestyleType -> lifestyleType.id,
                10d,
                0.7d
        );

        double loveLanguageScore = scoreReciprocalSetPreference(
                currentProfile.loveLanguages,
                candidateProfile.loveLanguages,
                currentPreference == null ? null : currentPreference.loveLanguageSimilarity,
                candidatePreference == null ? null : candidatePreference.loveLanguageSimilarity,
                loveLanguage -> loveLanguage.id,
                5d,
                0.7d
        );

        double energyScore = scoreReciprocalLevelPreference(
                currentProfile.energyLevel == null ? null : currentProfile.energyLevel.id,
                candidateProfile.energyLevel == null ? null : candidateProfile.energyLevel.id,
                currentPreference == null ? null : currentPreference.energyLevelSimilarity,
                candidatePreference == null ? null : candidatePreference.energyLevelSimilarity,
                5d,
                0.7d
        );

        double distanceScore = scoreDistance(distanceKm);
        double ageDifferenceScore = scoreAgeDifference(currentAge, candidateAge);

        double totalScore = communicationScore
                + accessibilityScore
                + autonomyScore
                + interestsScore
                + connectionTypeScore
                + lifestyleScore
                + loveLanguageScore
                + energyScore
                + distanceScore
                + ageDifferenceScore;

        return roundToTwoDecimals(Math.min(100d, totalScore));
    }

    List<UUID> buildOrganicFeed(List<UUID> rankedIds, List<UUID> discoveryIds, List<UUID> priorityIds, int limit) {
        List<UUID> feed = new ArrayList<>();
        int rankedIndex = 0;
        int discoveryIndex = 0;

        while (feed.size() < limit && (rankedIndex < rankedIds.size() || discoveryIndex < discoveryIds.size())) {
            int rankedInserted = 0;
            while (rankedInserted < 4 && rankedIndex < rankedIds.size() && feed.size() < limit) {
                addUnique(feed, rankedIds.get(rankedIndex));
                rankedIndex++;
                rankedInserted++;
            }

            if (discoveryIndex < discoveryIds.size() && feed.size() < limit) {
                addUnique(feed, discoveryIds.get(discoveryIndex));
                discoveryIndex++;
            }
        }

        while (rankedIndex < rankedIds.size() && feed.size() < limit) {
            addUnique(feed, rankedIds.get(rankedIndex));
            rankedIndex++;
        }

        while (discoveryIndex < discoveryIds.size() && feed.size() < limit) {
            addUnique(feed, discoveryIds.get(discoveryIndex));
            discoveryIndex++;
        }

        insertPriorityMatches(feed, priorityIds, limit);
        return feed;
    }

    private void validateDiscoveryState(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            UserCoordinates currentCoordinate,
            Integer currentAge
    ) {
        if (currentProfile.gender == null) {
            throw new IllegalArgumentException("Complete o gênero do perfil antes de buscar matches");
        }
        if (currentPreference.maxMatchDistanceKm == null || currentPreference.maxMatchDistanceKm < 1) {
            throw new IllegalArgumentException("Defina uma distância máxima de match válida antes de buscar matches");
        }
        if (currentPreference.desiredGenders == null || currentPreference.desiredGenders.isEmpty()) {
            throw new IllegalArgumentException("Defina pelo menos um gênero desejado antes de buscar matches");
        }
        if (currentCoordinate == null) {
            throw new IllegalArgumentException("Defina uma localização ativa antes de buscar matches");
        }
        if (currentAge == null) {
            throw new IllegalArgumentException("Não foi possível calcular a idade do usuário atual");
        }
    }

    private UserProfile requireProfile(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            throw new IllegalArgumentException("Complete o perfil antes de usar o algoritmo de match");
        }
        return profile;
    }

    private UserMatchPreference requireMatchPreference(UserProfile profile) {
        UserMatchPreference preference = UserMatchPreference.findByUserProfile(profile);
        if (preference == null) {
            throw new IllegalArgumentException("Complete as preferências de match antes de usar o algoritmo de match");
        }
        return preference;
    }

    private UserCoordinates requireActiveCoordinate(UserProfile profile) {
        return profile.getActiveCoordinate();
    }

    private Set<UUID> normalizeAlreadyUsed(PotentialMatchesRequest request) {
        if (request == null || request.alreadyUsedProfileIds() == null || request.alreadyUsedProfileIds().isEmpty()) {
            return new LinkedHashSet<>();
        }

        return request.alreadyUsedProfileIds().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<UUID> collectPriorityInboundProfileIds(UserProfile currentProfile, Set<UUID> alreadyUsedProfileIds) {
        return UserPossibleMatch.listInboundPending(currentProfile).stream()
                .map(match -> match.starterProfile == null ? null : match.starterProfile.id)
                .filter(Objects::nonNull)
                .filter(profileId -> !alreadyUsedProfileIds.contains(profileId))
                .distinct()
                .toList();
    }

    private Set<UUID> collectExcludedProfileIds(
            UserProfile currentProfile,
            Set<UUID> alreadyUsedProfileIds,
            List<UUID> priorityInboundProfileIds
    ) {
        LinkedHashSet<UUID> excludedProfileIds = new LinkedHashSet<>(alreadyUsedProfileIds);
        excludedProfileIds.add(currentProfile.id);

        for (UserPossibleMatch match : UserPossibleMatch.listAllRelatedToProfile(currentProfile)) {
            if (match.starterProfile != null
                    && Objects.equals(match.starterProfile.id, currentProfile.id)
                    && match.pendingProfile != null) {
                excludedProfileIds.add(match.pendingProfile.id);
            }

            if (match.pendingProfile != null
                    && Objects.equals(match.pendingProfile.id, currentProfile.id)
                    && match.pendingAccepted != null
                    && match.starterProfile != null) {
                excludedProfileIds.add(match.starterProfile.id);
            }
        }

        priorityInboundProfileIds.forEach(excludedProfileIds::remove);
        return excludedProfileIds;
    }

    private LinkedHashMap<UUID, CandidateSearchRow> loadCandidateRowsWithAgeExpansion(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            UserCoordinates currentCoordinate,
            Set<UUID> excludedProfileIds
    ) {
        LinkedHashMap<UUID, CandidateSearchRow> candidateRowsById = new LinkedHashMap<>();
        List<Integer> expansions = buildAgeExpansions(currentPreference);

        for (Integer expansion : expansions) {
            List<CandidateSearchRow> rows = executeCandidateQuery(
                    currentProfile,
                    currentPreference,
                    currentCoordinate,
                    excludedProfileIds,
                    expansion,
                    PRESELECTION_LIMIT
            );

            for (CandidateSearchRow row : rows) {
                candidateRowsById.putIfAbsent(row.profileId(), row);
            }

            if (candidateRowsById.size() >= PRESELECTION_LIMIT) {
                break;
            }
        }

        return candidateRowsById;
    }

    private List<Integer> buildAgeExpansions(UserMatchPreference currentPreference) {
        if (currentPreference.minAge == null && currentPreference.maxAge == null) {
            return List.of(0);
        }

        List<Integer> expansions = new ArrayList<>();
        expansions.add(0);
        for (int expansion = AGE_EXPANSION_STEP_YEARS; expansion <= MAX_AGE_EXPANSION_YEARS; expansion += AGE_EXPANSION_STEP_YEARS) {
            expansions.add(expansion);
        }
        return expansions;
    }

    @SuppressWarnings("unchecked")
    private List<CandidateSearchRow> executeCandidateQuery(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            UserCoordinates currentCoordinate,
            Set<UUID> excludedProfileIds,
            int ageExpansion,
            int limit
    ) {
        List<Integer> desiredGenderIds = currentPreference.desiredGenders.stream()
                .map(gender -> gender.id)
                .filter(Objects::nonNull)
                .toList();

        if (desiredGenderIds.isEmpty()) {
            return List.of();
        }

        List<UUID> excludedIds = excludedProfileIds.stream().filter(Objects::nonNull).toList();
        String genderParameters = namedParameters("gender", desiredGenderIds.size());
        String excludedClause = excludedIds.isEmpty()
                ? ""
                : " and up.id not in (" + namedParameters("excluded", excludedIds.size()) + ")";
        Integer minAge = expandedMinAge(currentPreference.minAge, ageExpansion);
        Integer maxAge = expandedMaxAge(currentPreference.maxAge, ageExpansion);
        StringBuilder ageClause = new StringBuilder();
        if (minAge != null) {
            ageClause.append(" and date_part('year', age(current_date, u.birthdate)) >= :minAge");
        }
        if (maxAge != null) {
            ageClause.append(" and date_part('year', age(current_date, u.birthdate)) <= :maxAge");
        }

        String sql = """
                with gender_filtered as (
                    select up.id as profile_id, up.fk_user as user_id
                    from user_profiles up
                    join users u on u.id = up.fk_user
                    where up.id <> :currentProfileId
                      and u.verified = true
                      and (up.fk_gender in (%s) or up.fk_gender = 4) -- Id 4 é para prefiro não informar, então devem entrar em consideração
                      %s
                ),
                age_filtered as (
                    select gf.profile_id
                    from gender_filtered gf
                    join users u on u.id = gf.user_id
                    where 1 = 1
                    %s
                ),
                distance_filtered as (
                    select af.profile_id,
                           6371.0 * acos(least(1.0, greatest(-1.0,
                               cos(radians(:currentLatitude)) * cos(radians(c.latitude)) * cos(radians(c.longitude) - radians(:currentLongitude))
                               + sin(radians(:currentLatitude)) * sin(radians(c.latitude))
                           ))) as distance_km
                    from age_filtered af
                    join user_coordinates c on c.fk_user_profile = af.profile_id and c.active = true
                )
                select profile_id, distance_km
                from distance_filtered
                where distance_km <= :maxMatchDistanceKm
                order by distance_km asc
                limit :resultLimit
                """.formatted(genderParameters, excludedClause, ageClause);

        Query query = entityManager.createNativeQuery(sql);
        query.setParameter("currentProfileId", currentProfile.id);

        for (int index = 0; index < desiredGenderIds.size(); index++) {
            query.setParameter("gender" + index, desiredGenderIds.get(index));
        }

        for (int index = 0; index < excludedIds.size(); index++) {
            query.setParameter("excluded" + index, excludedIds.get(index));
        }

        if (minAge != null) {
            query.setParameter("minAge", minAge);
        }
        if (maxAge != null) {
            query.setParameter("maxAge", maxAge);
        }
        query.setParameter("currentLatitude", currentCoordinate.latitude.doubleValue());
        query.setParameter("currentLongitude", currentCoordinate.longitude.doubleValue());
        query.setParameter("maxMatchDistanceKm", currentPreference.maxMatchDistanceKm.doubleValue());
        query.setParameter("resultLimit", limit);

        List<Object[]> rawRows = query.getResultList();
        List<CandidateSearchRow> candidateRows = new ArrayList<>(rawRows.size());
        for (Object[] row : rawRows) {
            candidateRows.add(new CandidateSearchRow(toUuid(row[0]), ((Number) row[1]).doubleValue()));
        }
        return candidateRows;
    }

    private Integer expandedMinAge(Integer minAge, int ageExpansion) {
        if (minAge == null) {
            return null;
        }
        return Math.max(MINIMUM_ALLOWED_AGE, minAge - ageExpansion);
    }

    private Integer expandedMaxAge(Integer maxAge, int ageExpansion) {
        if (maxAge == null) {
            return null;
        }
        return maxAge + ageExpansion;
    }

    private String namedParameters(String prefix, int size) {
        return IntStream.range(0, size)
                .mapToObj(index -> ":" + prefix + index)
                .collect(Collectors.joining(", "));
    }

    private UUID toUuid(Object value) {
        if (value instanceof UUID uuid) {
            return uuid;
        }
        return UUID.fromString(String.valueOf(value));
    }

    private LinkedHashMap<UUID, UserProfile> loadProfiles(Collection<UUID> profileIds) {
        LinkedHashMap<UUID, UserProfile> profilesById = new LinkedHashMap<>();
        for (UUID profileId : profileIds) {
            UserProfile profile = UserProfile.findById(profileId);
            if (profile != null) {
                profilesById.put(profileId, profile);
            }
        }
        return profilesById;
    }

    private List<ScoredCandidate> scoreCandidates(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            Integer currentAge,
            LinkedHashMap<UUID, CandidateSearchRow> candidateRows,
            LinkedHashMap<UUID, UserProfile> candidateProfiles
    ) {
        List<ScoredCandidate> scoredCandidates = new ArrayList<>();

        for (CandidateSearchRow candidateRow : candidateRows.values()) {
            UserProfile candidateProfile = candidateProfiles.get(candidateRow.profileId());
            if (candidateProfile == null || candidateProfile.user == null) {
                continue;
            }

            Integer candidateAge = calculateAge(candidateProfile.user);
            UserMatchPreference candidatePreference = candidateProfile.matchPreference == null
                    ? UserMatchPreference.findByUserProfile(candidateProfile)
                    : candidateProfile.matchPreference;

            double score = calculateCompatibilityScore(
                    currentProfile,
                    currentPreference,
                    currentAge,
                    candidateProfile,
                    candidatePreference,
                    candidateAge,
                    candidateRow.distanceKm()
            );

            scoredCandidates.add(new ScoredCandidate(candidateRow.profileId(), score, candidateRow.distanceKm()));
        }

        scoredCandidates.sort(
                Comparator.comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparingDouble(ScoredCandidate::distanceKm)
        );
        return scoredCandidates;
    }

    private List<UUID> selectDiscoveryIds(
            List<ScoredCandidate> scoredCandidates,
            Set<UUID> rankedIds,
            int discoveryTarget
    ) {
        if (discoveryTarget <= 0) {
            return List.of();
        }

        List<UUID> preferredPool = scoredCandidates.stream()
                .filter(candidate -> candidate.score() >= MINIMUM_DISCOVERY_SCORE)
                .filter(candidate -> candidate.score() < PREFERRED_DISCOVERY_MAX_SCORE)
                .map(ScoredCandidate::profileId)
                .filter(profileId -> !rankedIds.contains(profileId))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(preferredPool, ThreadLocalRandom.current());

        if (preferredPool.size() < discoveryTarget) {
            List<UUID> fallbackPool = scoredCandidates.stream()
                    .filter(candidate -> candidate.score() >= MINIMUM_DISCOVERY_SCORE)
                    .map(ScoredCandidate::profileId)
                    .filter(profileId -> !rankedIds.contains(profileId))
                    .filter(profileId -> !preferredPool.contains(profileId))
                    .distinct()
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(fallbackPool, ThreadLocalRandom.current());
            preferredPool.addAll(fallbackPool);
        }

        if (preferredPool.size() <= discoveryTarget) {
            return List.copyOf(preferredPool);
        }

        return List.copyOf(preferredPool.subList(0, discoveryTarget));
    }

    private void insertPriorityMatches(List<UUID> feed, List<UUID> priorityIds, int limit) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (UUID priorityId : priorityIds) {
            if (priorityId == null || feed.contains(priorityId)) {
                continue;
            }

            if (feed.isEmpty()) {
                feed.add(priorityId);
            } else {
                int upperBound = feed.size() >= limit ? feed.size() : feed.size() + 1;
                int insertIndex = random.nextInt(upperBound);
                feed.add(insertIndex, priorityId);
            }

            if (feed.size() > limit) {
                feed.remove(feed.size() - 1);
            }
        }
    }

    private void addUnique(List<UUID> feed, UUID profileId) {
        if (profileId != null && !feed.contains(profileId)) {
            feed.add(profileId);
        }
    }

    private double scoreConnectionTypeCompatibility(UserMatchPreference currentPreference, UserMatchPreference candidatePreference) {
        if (currentPreference == null
                || currentPreference.connectionType == null
                || candidatePreference == null
                || candidatePreference.connectionType == null) {
            return 0d;
        }

        return Objects.equals(currentPreference.connectionType.id, candidatePreference.connectionType.id) ? 15d : 0d;
    }

    private <T> double scoreSharedSet(
            Collection<T> currentValues,
            Collection<T> candidateValues,
            Function<T, Integer> idExtractor,
            double weight
    ) {
        if (currentValues == null || currentValues.isEmpty() || candidateValues == null || candidateValues.isEmpty()) {
            return 0d;
        }

        Set<Integer> currentIds = extractIds(currentValues, idExtractor);
        Set<Integer> candidateIds = extractIds(candidateValues, idExtractor);
        if (currentIds.isEmpty() || candidateIds.isEmpty()) {
            return 0d;
        }

        long sharedCount = currentIds.stream().filter(candidateIds::contains).count();
        double ratio = sharedCount / (double) Math.max(currentIds.size(), candidateIds.size());
        return weight * ratio;
    }

    private <T> double scoreReciprocalSetPreference(
            Collection<T> currentValues,
            Collection<T> candidateValues,
            SimilarityPreference currentPreference,
            SimilarityPreference candidatePreference,
            Function<T, Integer> idExtractor,
            double weight,
            double neutralRatio
    ) {
        List<Double> directionalScores = new ArrayList<>(2);
        Double currentScore = scoreDirectionalSetPreference(
                currentValues,
                candidateValues,
                currentPreference,
                idExtractor,
                weight,
                neutralRatio
        );
        if (currentScore != null) {
            directionalScores.add(currentScore);
        }

        Double candidateScore = scoreDirectionalSetPreference(
                candidateValues,
                currentValues,
                candidatePreference,
                idExtractor,
                weight,
                neutralRatio
        );
        if (candidateScore != null) {
            directionalScores.add(candidateScore);
        }

        if (directionalScores.isEmpty()) {
            return weight * neutralRatio;
        }

        return directionalScores.stream().mapToDouble(Double::doubleValue).average().orElse(weight * neutralRatio);
    }

    private <T> Double scoreDirectionalSetPreference(
            Collection<T> sourceValues,
            Collection<T> targetValues,
            SimilarityPreference preference,
            Function<T, Integer> idExtractor,
            double weight,
            double neutralRatio
    ) {
        if (preference == null) {
            return null;
        }
        if (preference == SimilarityPreference.ANY) {
            return weight * neutralRatio;
        }
        if (sourceValues == null || sourceValues.isEmpty() || targetValues == null || targetValues.isEmpty()) {
            return 0d;
        }

        Set<Integer> sourceIds = extractIds(sourceValues, idExtractor);
        Set<Integer> targetIds = extractIds(targetValues, idExtractor);
        if (sourceIds.isEmpty() || targetIds.isEmpty()) {
            return 0d;
        }

        long sharedCount = sourceIds.stream().filter(targetIds::contains).count();
        double ratio = sharedCount / (double) Math.max(sourceIds.size(), targetIds.size());
        if (preference == SimilarityPreference.SIMILAR) {
            return weight * ratio;
        }
        return weight * (1d - ratio);
    }

    private double scoreReciprocalLevelPreference(
            Integer currentLevelId,
            Integer candidateLevelId,
            SimilarityPreference currentPreference,
            SimilarityPreference candidatePreference,
            double weight,
            double neutralRatio
    ) {
        List<Double> directionalScores = new ArrayList<>(2);
        Double currentScore = scoreDirectionalLevelPreference(
                currentLevelId,
                candidateLevelId,
                currentPreference,
                weight,
                neutralRatio
        );
        if (currentScore != null) {
            directionalScores.add(currentScore);
        }

        Double candidateScore = scoreDirectionalLevelPreference(
                candidateLevelId,
                currentLevelId,
                candidatePreference,
                weight,
                neutralRatio
        );
        if (candidateScore != null) {
            directionalScores.add(candidateScore);
        }

        if (directionalScores.isEmpty()) {
            return weight * neutralRatio;
        }

        return directionalScores.stream().mapToDouble(Double::doubleValue).average().orElse(weight * neutralRatio);
    }

    private Double scoreDirectionalLevelPreference(
            Integer sourceLevelId,
            Integer targetLevelId,
            SimilarityPreference preference,
            double weight,
            double neutralRatio
    ) {
        if (preference == null) {
            return null;
        }
        if (preference == SimilarityPreference.ANY) {
            return weight * neutralRatio;
        }
        if (sourceLevelId == null || targetLevelId == null) {
            return 0d;
        }

        int distance = Math.abs(sourceLevelId - targetLevelId);
        if (preference == SimilarityPreference.SIMILAR) {
            if (distance == 0) {
                return weight;
            }
            if (distance == 1) {
                return weight * 0.6d;
            }
            return weight * 0.2d;
        }

        if (distance >= 2) {
            return weight;
        }
        if (distance == 1) {
            return weight * 0.6d;
        }
        return 0d;
    }

    private <T> Set<Integer> extractIds(Collection<T> values, Function<T, Integer> idExtractor) {
        return values.stream()
                .map(idExtractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double scoreDistance(double distanceKm) {
        if (distanceKm <= 10d) {
            return 3d;
        }
        if (distanceKm <= 30d) {
            return 2d;
        }
        return 1d;
    }

    private double scoreAgeDifference(Integer currentAge, Integer candidateAge) {
        if (currentAge == null || candidateAge == null) {
            return 0d;
        }

        int difference = Math.abs(currentAge - candidateAge);
        if (difference <= 2) {
            return 2d;
        }
        if (difference <= 5) {
            return 1d;
        }
        return 0d;
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private MatchDecisionResponse toDecisionResponse(UserPossibleMatch match) {
        return new MatchDecisionResponse(
                match.id,
                match.starterProfile == null ? null : match.starterProfile.id,
                match.pendingProfile == null ? null : match.pendingProfile.id,
                match.createdAt,
                match.starterAccepted,
                match.pendingAccepted,
                match.starterAccepted && Boolean.TRUE.equals(match.pendingAccepted)
        );
    }

    private MutualMatchResponse toMutualMatchResponse(UserProfile currentProfile, UserPossibleMatch match) {
        UserProfile otherProfile = Objects.equals(match.starterProfile.id, currentProfile.id)
                ? match.pendingProfile
                : match.starterProfile;
        UserProfileImage activeProfilePicture = otherProfile == null ? null : UserProfileImage.findActiveProfilePicture(otherProfile);
        return new MutualMatchResponse(
                otherProfile == null ? null : otherProfile.id,
                activeProfilePicture == null ? null : toMatchImageResponse(activeProfilePicture)
        );
    }

    private UserProfileImageResponse toMatchImageResponse(UserProfileImage image) {
        return new UserProfileImageResponse(
                image.id,
                image.profilePicture,
                image.active,
                MATCH_IMAGE_DOWNLOAD_URL_PREFIX + image.id
        );
    }

    private byte[] readStoredImage(UserProfileImage image) {
        try {
            return image.oid.getBytes(1, (int) image.oid.length());
        } catch (SQLException exception) {
            throw new IllegalStateException("Não foi possível ler a imagem armazenada do match", exception);
        }
    }

    private Integer calculateAge(User user) {
        if (user == null || user.birthdate == null) {
            return null;
        }

        LocalDate today = LocalDate.now();
        if (user.birthdate.isAfter(today)) {
            return null;
        }

        return Period.between(user.birthdate, today).getYears();
    }

    private record CandidateSearchRow(UUID profileId, double distanceKm) {
    }

    private record ScoredCandidate(UUID profileId, double score, double distanceKm) {
    }
}