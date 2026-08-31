package br.com.unify.matchable.user.services;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.Hibernate;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.dto.PageParams;
import br.com.unify.matchable.user.dto.MatchDecisionRequest;
import br.com.unify.matchable.user.dto.MatchDecisionResponse;
import br.com.unify.matchable.user.dto.MutualMatchPageResponse;
import br.com.unify.matchable.user.dto.MutualMatchResponse;
import br.com.unify.matchable.user.dto.MutualMatchSummaryResponse;
import br.com.unify.matchable.user.dto.PotentialMatchesRequest;
import br.com.unify.matchable.user.dto.UserProfileImageResponse;
import br.com.unify.matchable.user.entity.CommunicationForm;
import br.com.unify.matchable.user.entity.Gender;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserCoordinates;
import br.com.unify.matchable.user.entity.UserMatchPreference;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import br.com.unify.matchable.user.entity.UserProfileImage;
import br.com.unify.matchable.user.enums.SimilarityPreference;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserMatchServiceImplementation implements UserMatchService {

    private static final int TARGET_MATCH_COUNT = 50;
    private static final int PRESELECTION_LIMIT = 250;
    private static final int MAX_AGE_EXPANSION_YEARS = 10;
    private static final int AGE_EXPANSION_STEP_YEARS = 5;
    private static final int MINIMUM_ALLOWED_AGE = 18;
    private static final int UNDISCLOSED_GENDER_ID = 4;
    private static final String MATCH_IMAGE_DOWNLOAD_URL_PREFIX = "/users/me/matches/images/";
    private static final String MATCH_IMAGE_NOT_FOUND_MESSAGE = "Imagem de match não encontrada";

    /**
     * Compatibilidade parcial entre tipos de conexão.
     *
     * Catálogo real (src/main/resources/import.sql):
     * 1 = Amizade, 2 = Relacionamento, 3 = Networking, 4 = Comunidade.
     *
     * Decisão: os três tipos platônicos (Amizade, Networking, Comunidade) são parcialmente
     * compatíveis entre si — quem procura amizade aceita razoavelmente uma conexão de
     * comunidade ou de rede profissional. "Relacionamento" (2) não tem par parcial: cruzar
     * quem procura namoro com quem procura amizade é justamente o erro que este fator existe
     * para evitar.
     *
     * O mapa fica aqui e não no banco porque o catálogo connection_types é pequeno e estável.
     * Se o catálogo crescer, mover para uma coluna `compatible_with` em connection_types.
     */
    static final Map<Integer, Set<Integer>> COMPATIBLE_CONNECTION_TYPES = Map.of(
            1, Set.of(3, 4),
            3, Set.of(1, 4),
            4, Set.of(1, 3)
    );

    @Inject
    EntityManager entityManager;

    @ConfigProperty(name = "unify.match.default-max-distance-km", defaultValue = "60")
    int defaultMaxDistanceKm = 60;

    @ConfigProperty(name = "unify.match.default-min-age", defaultValue = "18")
    int defaultMinAge = 18;

    @ConfigProperty(name = "unify.match.default-max-age", defaultValue = "99")
    int defaultMaxAge = 99;

    @ConfigProperty(name = "unify.match.allow-discovery-without-location", defaultValue = "true")
    boolean allowDiscoveryWithoutLocation = true;

    @ConfigProperty(name = "unify.match.no-location-candidate-quota", defaultValue = "0.15")
    double noLocationCandidateQuota = 0.15d;

    @ConfigProperty(name = "unify.match.decline-cooldown-days", defaultValue = "30")
    int declineCooldownDays = 30;

    @Override
    public List<UUID> getPotentialMatches(User user, PotentialMatchesRequest request) {
        return discoverPotentialMatches(user, request).profileIds();
    }

    @Override
    public DiscoveryResult discoverPotentialMatches(User user, PotentialMatchesRequest request) {
        UserProfile currentProfile = requireProfile(user);
        UserMatchPreference currentPreference = resolveMatchPreference(currentProfile);
        UserCoordinates currentCoordinate = requireActiveCoordinate(currentProfile);
        Integer currentAge = calculateAge(user);

        validateDiscoveryState(currentPreference, currentCoordinate, currentAge);

        String discoveryMode = currentCoordinate == null ? DISCOVERY_MODE_NO_LOCATION : DISCOVERY_MODE_GEO;
        Instant declineThreshold = declineThreshold();

        Set<UUID> alreadyUsedProfileIds = normalizeAlreadyUsed(request);
        List<UUID> priorityInboundProfileIds = collectPriorityInboundProfileIds(currentProfile, alreadyUsedProfileIds);
        Set<UUID> reshownProfileIds = collectReshownProfileIds(currentProfile, declineThreshold);

        LinkedHashMap<UUID, CandidateSearchRow> candidateRows = loadCandidateRowsWithAgeExpansion(
                currentProfile,
                currentPreference,
                currentCoordinate,
                declineThreshold
        );

        // A lista de já vistos é estado de sessão do cliente, não do servidor: filtra em memória.
        alreadyUsedProfileIds.forEach(candidateRows::remove);
        candidateRows.remove(currentProfile.id);

        if (candidateRows.isEmpty() && priorityInboundProfileIds.isEmpty()) {
            return new DiscoveryResult(List.of(), discoveryMode);
        }

        LinkedHashMap<UUID, UserProfile> candidateProfiles = loadProfiles(candidateRows.keySet());
        List<ScoredCandidate> scoredCandidates = scoreCandidates(
                currentProfile,
                currentPreference,
                currentAge,
                candidateRows,
                candidateProfiles,
                reshownProfileIds
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
            return new DiscoveryResult(List.copyOf(organicFeed.subList(0, TARGET_MATCH_COUNT)), discoveryMode);
        }

        return new DiscoveryResult(List.copyOf(organicFeed), discoveryMode);
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

        Instant now = Instant.now();
        boolean accepted = Boolean.TRUE.equals(request.accepted());

        // 1) Existe convite recebido? responde a ele.
        UserPossibleMatch inboundMatch = UserPossibleMatch.findByStarterAndPending(targetProfile, currentProfile);
        if (inboundMatch != null) {
            if (inboundMatch.pendingAccepted != null) {
                throw new IllegalStateException("Este match já foi respondido");
            }
            inboundMatch.pendingAccepted = request.accepted();
            if (!accepted) {
                inboundMatch.declinedAt = now;
                inboundMatch.declinedByProfile = currentProfile;
            }
            return toDecisionResponse(inboundMatch);
        }

        // 2) Já existe registro na direção oposta?
        UserPossibleMatch existingOutboundMatch =
                UserPossibleMatch.findByStarterAndPending(currentProfile, targetProfile);

        if (existingOutboundMatch != null) {
            // Reapresentação após cooldown: permitir sobrescrever uma recusa antiga.
            if (existingOutboundMatch.declinedAt != null) {
                existingOutboundMatch.starterAccepted = accepted;
                existingOutboundMatch.declinedAt = accepted ? null : now;
                existingOutboundMatch.declinedByProfile = accepted ? null : currentProfile;
                existingOutboundMatch.createdAt = now;
                return toDecisionResponse(existingOutboundMatch);
            }
            throw new IllegalStateException("Você já registrou uma decisão para este perfil");
        }

        // 3) Perfil novo: aceitar OU recusar — os dois passam a ser persistidos.
        UserPossibleMatch possibleMatch = new UserPossibleMatch();
        possibleMatch.id = UUIDv7Generator.generate();
        possibleMatch.starterProfile = currentProfile;
        possibleMatch.pendingProfile = targetProfile;
        possibleMatch.createdAt = now;
        possibleMatch.starterAccepted = accepted;
        possibleMatch.pendingAccepted = null;

        if (!accepted) {
            possibleMatch.declinedAt = now;
            possibleMatch.declinedByProfile = currentProfile;
        }

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
    public MutualMatchPageResponse getMutualMatchesPage(User user, Integer page, Integer size) {
        int resolvedPage = validatePage(page);
        int resolvedSize = validateSize(size);

        UserProfile currentProfile = UserProfile.findByUser(user);
        if (currentProfile == null) {
            return toMutualMatchPageResponse(List.of(), resolvedPage, resolvedSize, 0);
        }

        PanacheQuery<UserPossibleMatch> query = UserPossibleMatch.findConfirmedForProfile(currentProfile);
        long totalElements = query.count();
        List<MutualMatchSummaryResponse> matches = query.page(Page.of(resolvedPage, resolvedSize)).list().stream()
                .map(match -> toMutualMatchSummaryResponse(currentProfile, match))
                .toList();
        return toMutualMatchPageResponse(matches, resolvedPage, resolvedSize, totalElements);
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

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    /**
     * Score de compatibilidade normalizado (0..100).
     *
     * FÓRMULA:
     *   somaObtida  = Σ obtido_i     (apenas fatores applicable)
     *   somaPesos   = Σ peso_i       (apenas fatores applicable)
     *   scoreBruto  = 100 * somaObtida / somaPesos
     *   cobertura   = somaPesos / 100
     *   se cobertura &lt; 0.40  -&gt; score = min(scoreBruto, 70)   [baixa confiança]
     *   score       = max(0, min(100, score - penalidades))
     *
     * Por que normalizar: sem isso, um perfil que não preencheu tipo de conexão perde 12 pontos
     * absolutos enquanto um perfil que não preencheu NENHUMA preferência ganhava ~25 pontos
     * "neutros". A normalização faz o score responder à QUALIDADE das informações comuns,
     * e a cobertura responde pela QUANTIDADE (via teto de baixa confiança).
     */
    double calculateCompatibilityScore(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            Integer currentAge,
            UserProfile candidateProfile,
            UserMatchPreference candidatePreference,
            Integer candidateAge,
            Double distanceKm,
            ScoringContext context
    ) {
        List<FactorScore> factors = List.of(
                scoreCommunicationViability(currentProfile.communicationForms, candidateProfile.communicationForms),
                scoreSharedInterests(currentProfile.interestTypes, candidateProfile.interestTypes),
                scoreConnectionTypeCompatibility(currentPreference, candidatePreference),
                scoreAccessibilityNeeds(currentProfile, candidateProfile, currentPreference, candidatePreference),
                scoreReciprocalSetPreference(
                        currentProfile.lifestyleTypes,
                        candidateProfile.lifestyleTypes,
                        currentPreference == null ? null : currentPreference.lifestyleSimilarity,
                        candidatePreference == null ? null : candidatePreference.lifestyleSimilarity,
                        lifestyleType -> lifestyleType.id,
                        MatchScoringPolicy.WEIGHT_LIFESTYLE),
                scoreReciprocalLevelPreference(
                        currentProfile.autonomyLevel == null ? null : currentProfile.autonomyLevel.id,
                        candidateProfile.autonomyLevel == null ? null : candidateProfile.autonomyLevel.id,
                        currentPreference == null ? null : currentPreference.autonomyCompatibility,
                        candidatePreference == null ? null : candidatePreference.autonomyCompatibility,
                        MatchScoringPolicy.WEIGHT_AUTONOMY),
                scoreReciprocalSetPreference(
                        currentProfile.loveLanguages,
                        candidateProfile.loveLanguages,
                        currentPreference == null ? null : currentPreference.loveLanguageSimilarity,
                        candidatePreference == null ? null : candidatePreference.loveLanguageSimilarity,
                        loveLanguage -> loveLanguage.id,
                        MatchScoringPolicy.WEIGHT_LOVE_LANGUAGE),
                scoreReciprocalLevelPreference(
                        currentProfile.energyLevel == null ? null : currentProfile.energyLevel.id,
                        candidateProfile.energyLevel == null ? null : candidateProfile.energyLevel.id,
                        currentPreference == null ? null : currentPreference.energyLevelSimilarity,
                        candidatePreference == null ? null : candidatePreference.energyLevelSimilarity,
                        MatchScoringPolicy.WEIGHT_ENERGY),
                scoreDistance(distanceKm),
                scoreAgeDifference(currentAge, candidateAge)
        );

        double obtainedSum = 0d;
        double applicableWeightSum = 0d;
        for (FactorScore factor : factors) {
            if (!factor.applicable()) {
                continue;
            }
            obtainedSum += factor.obtained();
            applicableWeightSum += factor.weight();
        }

        if (applicableWeightSum <= 0d) {
            return 0d; // não há absolutamente nenhuma informação em comum
        }

        double score = 100d * obtainedSum / applicableWeightSum;
        double coverage = applicableWeightSum / MatchScoringPolicy.TOTAL_WEIGHT;

        if (coverage < MatchScoringPolicy.MIN_CONFIDENT_COVERAGE) {
            score = Math.min(score, MatchScoringPolicy.LOW_CONFIDENCE_SCORE_CAP);
        }

        score -= penalties(context);

        return roundToTwoDecimals(Math.max(0d, Math.min(100d, score)));
    }

    /** Sobrecarga de compatibilidade com o teste existente (distância sempre conhecida). */
    double calculateCompatibilityScore(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            Integer currentAge,
            UserProfile candidateProfile,
            UserMatchPreference candidatePreference,
            Integer candidateAge,
            double distanceKm
    ) {
        return calculateCompatibilityScore(
                currentProfile, currentPreference, currentAge,
                candidateProfile, candidatePreference, candidateAge,
                distanceKm, ScoringContext.empty()
        );
    }

    private double penalties(ScoringContext context) {
        if (context == null) {
            return 0d;
        }
        double total = 0d;
        if (context.genderNotReciprocal()) {
            total += MatchScoringPolicy.PENALTY_GENDER_NOT_RECIPROCAL;
        }
        if (context.ageNotReciprocal()) {
            total += MatchScoringPolicy.PENALTY_AGE_NOT_RECIPROCAL;
        }
        if (context.reshownAfterCooldown()) {
            total += MatchScoringPolicy.PENALTY_RESHOWN_AFTER_COOLDOWN;
        }
        return total;
    }

    /**
     * Reciprocidade não bloqueia o candidato: reduz o ranking.
     * (Regra do MATCH_ALG_GUIDE.md — "não bloquear, reduzir ranking".)
     */
    private ScoringContext buildScoringContext(
            UserProfile currentProfile,
            Integer currentAge,
            UserMatchPreference candidatePreference,
            boolean reshownAfterCooldown
    ) {
        boolean genderNotReciprocal = false;
        boolean ageNotReciprocal = false;

        if (candidatePreference != null && currentProfile.gender != null
                && candidatePreference.desiredGenders != null
                && !candidatePreference.desiredGenders.isEmpty()) {
            genderNotReciprocal = candidatePreference.desiredGenders.stream()
                    .map(gender -> gender.id)
                    .noneMatch(id -> Objects.equals(id, currentProfile.gender.id))
                    // id 4 = "prefiro não informar" sempre entra, como no filtro SQL
                    && !Objects.equals(currentProfile.gender.id, UNDISCLOSED_GENDER_ID);
        }

        if (candidatePreference != null && currentAge != null) {
            boolean belowMin = candidatePreference.minAge != null && currentAge < candidatePreference.minAge;
            boolean aboveMax = candidatePreference.maxAge != null && currentAge > candidatePreference.maxAge;
            ageNotReciprocal = belowMin || aboveMax;
        }

        return new ScoringContext(genderNotReciprocal, ageNotReciprocal, reshownAfterCooldown);
    }

    /**
     * Comunicação é PRATICIDADE, não semelhança.
     *
     * Regra:
     *   - nenhum canal em comum  -&gt; 0 (incompatibilidade real: as pessoas não conseguem se falar)
     *   - &gt;= 1 canal em comum    -&gt; 60% do peso imediatamente ("dá para conversar")
     *                               + 40% proporcional a compartilhados / min(|A|,|B|)
     *
     * Usar min() e não max() é o ponto central: dividir por max() penaliza quem declara MAIS
     * formas de comunicação, ou seja, quem é mais adaptável — exatamente o oposto do objetivo
     * de um app de inclusão.
     */
    private FactorScore scoreCommunicationViability(
            Collection<CommunicationForm> currentForms,
            Collection<CommunicationForm> candidateForms
    ) {
        double weight = MatchScoringPolicy.WEIGHT_COMMUNICATION;

        Set<Integer> currentIds = extractIds(currentForms, form -> form.id);
        Set<Integer> candidateIds = extractIds(candidateForms, form -> form.id);

        if (currentIds.isEmpty() || candidateIds.isEmpty()) {
            return FactorScore.notApplicable(weight); // ninguém declarou: não penaliza nem premia
        }

        long shared = currentIds.stream().filter(candidateIds::contains).count();
        if (shared == 0) {
            return FactorScore.of(0d, weight); // avaliável e incompatível
        }

        double depth = shared / (double) Math.min(currentIds.size(), candidateIds.size());
        double ratio = MatchScoringPolicy.COMMUNICATION_VIABILITY_BASE
                + (1d - MatchScoringPolicy.COMMUNICATION_VIABILITY_BASE) * depth;

        return FactorScore.ratio(ratio, weight);
    }

    /**
     * Interesses usam o coeficiente de sobreposição (shared / min) atenuado por um fator de
     * amplitude, para não dar nota máxima quando alguém tem 1 interesse e o outro tem 20.
     *
     *   overlap   = shared / min(|A|,|B|)
     *   breadth   = min(|A|,|B|) / max(|A|,|B|)     (1.0 quando os conjuntos têm o mesmo tamanho)
     *   ratio     = overlap * (0.75 + 0.25 * breadth)
     */
    private FactorScore scoreSharedInterests(
            Collection<InterestType> currentInterests,
            Collection<InterestType> candidateInterests
    ) {
        double weight = MatchScoringPolicy.WEIGHT_INTERESTS;

        Set<Integer> currentIds = extractIds(currentInterests, interest -> interest.id);
        Set<Integer> candidateIds = extractIds(candidateInterests, interest -> interest.id);

        if (currentIds.isEmpty() || candidateIds.isEmpty()) {
            return FactorScore.notApplicable(weight);
        }

        long shared = currentIds.stream().filter(candidateIds::contains).count();
        int smaller = Math.min(currentIds.size(), candidateIds.size());
        int larger = Math.max(currentIds.size(), candidateIds.size());

        double overlap = shared / (double) smaller;
        double breadth = smaller / (double) larger;

        return FactorScore.ratio(overlap * (0.75d + 0.25d * breadth), weight);
    }

    /**
     * Compatibilidade de tipo de conexão.
     *   - mesmo tipo                       -&gt; 100% do peso
     *   - tipos declarados compatíveis     -&gt;  50% do peso   (ver COMPATIBLE_CONNECTION_TYPES)
     *   - tipos diferentes e incompatíveis -&gt;   0
     *   - algum lado sem tipo definido     -&gt; não avaliável
     */
    private FactorScore scoreConnectionTypeCompatibility(
            UserMatchPreference currentPreference,
            UserMatchPreference candidatePreference
    ) {
        double weight = MatchScoringPolicy.WEIGHT_CONNECTION_TYPE;

        Integer currentTypeId = currentPreference == null || currentPreference.connectionType == null
                ? null
                : currentPreference.connectionType.id;
        Integer candidateTypeId = candidatePreference == null || candidatePreference.connectionType == null
                ? null
                : candidatePreference.connectionType.id;

        if (currentTypeId == null || candidateTypeId == null) {
            return FactorScore.notApplicable(weight);
        }

        if (Objects.equals(currentTypeId, candidateTypeId)) {
            return FactorScore.ratio(MatchScoringPolicy.CONNECTION_TYPE_EXACT_RATIO, weight);
        }

        boolean partial = COMPATIBLE_CONNECTION_TYPES
                .getOrDefault(currentTypeId, Set.of())
                .contains(candidateTypeId);

        return FactorScore.ratio(
                partial ? MatchScoringPolicy.CONNECTION_TYPE_PARTIAL_RATIO : 0d,
                weight
        );
    }

    /**
     * Diferença em relação ao código anterior: quando NENHUM dos dois declara necessidades de
     * acessibilidade, isso não é ausência de informação — é informação: ambos são compatíveis
     * nesse eixo. Antes o método devolvia 0, o que penalizava injustamente duas pessoas sem
     * necessidades específicas.
     */
    private FactorScore scoreAccessibilityNeeds(
            UserProfile currentProfile,
            UserProfile candidateProfile,
            UserMatchPreference currentPreference,
            UserMatchPreference candidatePreference
    ) {
        double weight = MatchScoringPolicy.WEIGHT_ACCESSIBILITY_NEEDS;

        boolean currentEmpty = currentProfile.accessibilityNeeds == null || currentProfile.accessibilityNeeds.isEmpty();
        boolean candidateEmpty = candidateProfile.accessibilityNeeds == null || candidateProfile.accessibilityNeeds.isEmpty();

        if (currentEmpty && candidateEmpty) {
            return FactorScore.of(weight, weight); // compatíveis por ausência mútua
        }

        return scoreReciprocalSetPreference(
                currentProfile.accessibilityNeeds,
                candidateProfile.accessibilityNeeds,
                currentPreference == null ? null : currentPreference.accessibilityNeedSimilarity,
                candidatePreference == null ? null : candidatePreference.accessibilityNeedSimilarity,
                accessibilityNeed -> accessibilityNeed.id,
                weight
        );
    }

    private <T> FactorScore scoreReciprocalSetPreference(
            Collection<T> currentValues,
            Collection<T> candidateValues,
            SimilarityPreference currentPreference,
            SimilarityPreference candidatePreference,
            Function<T, Integer> idExtractor,
            double weight
    ) {
        List<Double> directional = new ArrayList<>(2);

        Double currentScore = scoreDirectionalSetPreference(
                currentValues, candidateValues, currentPreference, idExtractor, weight);
        if (currentScore != null) {
            directional.add(currentScore);
        }

        Double candidateScore = scoreDirectionalSetPreference(
                candidateValues, currentValues, candidatePreference, idExtractor, weight);
        if (candidateScore != null) {
            directional.add(candidateScore);
        }

        if (directional.isEmpty()) {
            return FactorScore.notApplicable(weight); // ANTES: weight * neutralRatio
        }

        return FactorScore.of(
                directional.stream().mapToDouble(Double::doubleValue).average().orElse(0d),
                weight
        );
    }

    private <T> Double scoreDirectionalSetPreference(
            Collection<T> sourceValues,
            Collection<T> targetValues,
            SimilarityPreference preference,
            Function<T, Integer> idExtractor,
            double weight
    ) {
        if (preference == null) {
            return null; // sem preferência declarada nesta direção
        }
        if (preference == SimilarityPreference.ANY) {
            return weight * MatchScoringPolicy.NEUTRAL_PREFERENCE_RATIO;
        }

        Set<Integer> sourceIds = extractIds(sourceValues, idExtractor);
        Set<Integer> targetIds = extractIds(targetValues, idExtractor);
        if (sourceIds.isEmpty() || targetIds.isEmpty()) {
            return null; // ANTES: 0d — não dá para medir semelhança contra conjunto vazio
        }

        long shared = sourceIds.stream().filter(targetIds::contains).count();
        double ratio = shared / (double) Math.max(sourceIds.size(), targetIds.size());

        return preference == SimilarityPreference.SIMILAR
                ? weight * ratio
                : weight * (1d - ratio);
    }

    private FactorScore scoreReciprocalLevelPreference(
            Integer currentLevelId,
            Integer candidateLevelId,
            SimilarityPreference currentPreference,
            SimilarityPreference candidatePreference,
            double weight
    ) {
        List<Double> directional = new ArrayList<>(2);

        Double currentScore = scoreDirectionalLevelPreference(
                currentLevelId, candidateLevelId, currentPreference, weight);
        if (currentScore != null) {
            directional.add(currentScore);
        }

        Double candidateScore = scoreDirectionalLevelPreference(
                candidateLevelId, currentLevelId, candidatePreference, weight);
        if (candidateScore != null) {
            directional.add(candidateScore);
        }

        if (directional.isEmpty()) {
            return FactorScore.notApplicable(weight);
        }

        return FactorScore.of(
                directional.stream().mapToDouble(Double::doubleValue).average().orElse(0d),
                weight
        );
    }

    private Double scoreDirectionalLevelPreference(
            Integer sourceLevelId,
            Integer targetLevelId,
            SimilarityPreference preference,
            double weight
    ) {
        if (preference == null) {
            return null;
        }
        if (preference == SimilarityPreference.ANY) {
            return weight * MatchScoringPolicy.NEUTRAL_PREFERENCE_RATIO;
        }
        if (sourceLevelId == null || targetLevelId == null) {
            return null; // ANTES: 0d — nível ausente não é incompatibilidade
        }

        int distance = Math.abs(sourceLevelId - targetLevelId);
        if (preference == SimilarityPreference.SIMILAR) {
            if (distance == 0) {
                return weight;
            }
            if (distance == 1) {
                return weight * MatchScoringPolicy.LEVEL_SIMILAR_ADJACENT_RATIO;
            }
            return weight * MatchScoringPolicy.LEVEL_SIMILAR_FAR_RATIO;
        }

        if (distance >= 2) {
            return weight;
        }
        if (distance == 1) {
            return weight * MatchScoringPolicy.LEVEL_DIFFERENT_ADJACENT_RATIO;
        }
        return 0d;
    }

    /**
     * Curva linear decrescente. distanceKm == null significa que pelo menos um dos perfis
     * não tem coordenada ativa: o fator sai da conta (não é 1 ponto arbitrário, como antes).
     */
    private FactorScore scoreDistance(Double distanceKm) {
        double weight = MatchScoringPolicy.WEIGHT_DISTANCE;

        if (distanceKm == null || distanceKm < 0d) {
            return FactorScore.notApplicable(weight);
        }
        if (distanceKm <= MatchScoringPolicy.DISTANCE_FULL_SCORE_KM) {
            return FactorScore.of(weight, weight);
        }
        if (distanceKm >= MatchScoringPolicy.DISTANCE_ZERO_SCORE_KM) {
            return FactorScore.of(0d, weight);
        }

        double span = MatchScoringPolicy.DISTANCE_ZERO_SCORE_KM - MatchScoringPolicy.DISTANCE_FULL_SCORE_KM;
        double ratio = 1d - ((distanceKm - MatchScoringPolicy.DISTANCE_FULL_SCORE_KM) / span);
        return FactorScore.ratio(ratio, weight);
    }

    private FactorScore scoreAgeDifference(Integer currentAge, Integer candidateAge) {
        double weight = MatchScoringPolicy.WEIGHT_AGE_DIFFERENCE;

        if (currentAge == null || candidateAge == null) {
            return FactorScore.notApplicable(weight);
        }

        int difference = Math.abs(currentAge - candidateAge);
        if (difference <= MatchScoringPolicy.AGE_FULL_SCORE_YEARS) {
            return FactorScore.of(weight, weight);
        }
        if (difference >= MatchScoringPolicy.AGE_ZERO_SCORE_YEARS) {
            return FactorScore.of(0d, weight);
        }

        double span = MatchScoringPolicy.AGE_ZERO_SCORE_YEARS - MatchScoringPolicy.AGE_FULL_SCORE_YEARS;
        double ratio = 1d - ((difference - MatchScoringPolicy.AGE_FULL_SCORE_YEARS) / span);
        return FactorScore.ratio(ratio, weight);
    }

    private <T> Set<Integer> extractIds(Collection<T> values, Function<T, Integer> idExtractor) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }

        return values.stream()
                .map(idExtractor)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // ------------------------------------------------------------------
    // Feed orgânico
    // ------------------------------------------------------------------

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

    private List<UUID> selectDiscoveryIds(
            List<ScoredCandidate> scoredCandidates,
            Set<UUID> rankedIds,
            int discoveryTarget
    ) {
        if (discoveryTarget <= 0) {
            return List.of();
        }

        List<UUID> preferredPool = scoredCandidates.stream()
                .filter(candidate -> candidate.score() >= MatchScoringPolicy.MINIMUM_DISCOVERY_SCORE)
                .filter(candidate -> candidate.score() < MatchScoringPolicy.PREFERRED_DISCOVERY_MAX_SCORE)
                .map(ScoredCandidate::profileId)
                .filter(profileId -> !rankedIds.contains(profileId))
                .distinct()
                .collect(Collectors.toCollection(ArrayList::new));

        Collections.shuffle(preferredPool, ThreadLocalRandom.current());

        if (preferredPool.size() < discoveryTarget) {
            List<UUID> fallbackPool = scoredCandidates.stream()
                    .filter(candidate -> candidate.score() >= MatchScoringPolicy.MINIMUM_DISCOVERY_SCORE)
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

    private List<ScoredCandidate> scoreCandidates(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            Integer currentAge,
            LinkedHashMap<UUID, CandidateSearchRow> candidateRows,
            LinkedHashMap<UUID, UserProfile> candidateProfiles,
            Set<UUID> reshownProfileIds
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

            ScoringContext context = buildScoringContext(
                    currentProfile,
                    currentAge,
                    candidatePreference,
                    reshownProfileIds.contains(candidateRow.profileId())
            );

            double score = calculateCompatibilityScore(
                    currentProfile,
                    currentPreference,
                    currentAge,
                    candidateProfile,
                    candidatePreference,
                    candidateAge,
                    candidateRow.distanceKm(),
                    context
            );

            scoredCandidates.add(new ScoredCandidate(candidateRow.profileId(), score, candidateRow.distanceKm()));
        }

        scoredCandidates.sort(
                Comparator.comparingDouble(ScoredCandidate::score)
                        .reversed()
                        .thenComparingDouble(candidate -> candidate.distanceKm() == null
                                ? Double.MAX_VALUE
                                : candidate.distanceKm())
        );
        return scoredCandidates;
    }

    // ------------------------------------------------------------------
    // Estado do usuário atual e fallbacks (B5)
    // ------------------------------------------------------------------

    private void validateDiscoveryState(
            UserMatchPreference currentPreference,
            UserCoordinates currentCoordinate,
            Integer currentAge
    ) {
        if (currentPreference == null) {
            throw new IllegalArgumentException("Não foi possível resolver as preferências de match");
        }
        if (currentAge == null) {
            throw new IllegalArgumentException(
                    "Informe sua data de nascimento no perfil antes de buscar conexões");
        }
        if (currentCoordinate == null && !allowDiscoveryWithoutLocation) {
            throw new IllegalArgumentException(
                    "Defina uma localização ativa antes de buscar conexões");
        }
        // gênero do próprio perfil NÃO é mais exigido: ele não participa da query de candidatos.
        // Distância e gêneros desejados agora têm default (B5.2), então também saem daqui.
    }

    private UserProfile requireProfile(User user) {
        UserProfile profile = UserProfile.findByUser(user);
        if (profile == null) {
            throw new IllegalArgumentException("Complete o perfil antes de usar o algoritmo de match");
        }
        return profile;
    }

    /**
     * Preferências ausentes deixam de ser um erro 400: viram defaults em memória.
     * A entidade é DESANEXADA antes de receber defaults, para que o flush da transação
     * do resource não persista preferências que o usuário nunca escolheu.
     */
    private UserMatchPreference resolveMatchPreference(UserProfile profile) {
        UserMatchPreference preference = UserMatchPreference.findByUserProfile(profile);
        if (preference != null) {
            // As associações LAZY precisam ser inicializadas ANTES do detach.
            Hibernate.initialize(preference.desiredGenders);
            Hibernate.initialize(preference.connectionType);
            entityManager.detach(preference);
            return applyPreferenceDefaults(preference);
        }
        return buildDefaultPreference();
    }

    /**
     * Preenche apenas os campos NULOS de uma preferência já desanexada.
     * Nunca sobrescreve escolha do usuário e nunca chama persist().
     */
    private UserMatchPreference applyPreferenceDefaults(UserMatchPreference preference) {
        if (preference.minAge == null) {
            preference.minAge = defaultMinAge;
        }
        if (preference.maxAge == null) {
            preference.maxAge = defaultMaxAge;
        }
        if (preference.maxMatchDistanceKm == null || preference.maxMatchDistanceKm < 1) {
            preference.maxMatchDistanceKm = defaultMaxDistanceKm;
        }
        if (preference.desiredGenders == null || preference.desiredGenders.isEmpty()) {
            preference.desiredGenders = new LinkedHashSet<>(Gender.<Gender>listAll());
        }
        return preference;
    }

    private UserMatchPreference buildDefaultPreference() {
        UserMatchPreference preference = new UserMatchPreference();
        preference.minAge = defaultMinAge;
        preference.maxAge = defaultMaxAge;
        preference.maxMatchDistanceKm = defaultMaxDistanceKm;
        preference.desiredGenders = new LinkedHashSet<>(Gender.<Gender>listAll());
        preference.accessibilityNeedSimilarity = SimilarityPreference.ANY;
        preference.autonomyCompatibility = SimilarityPreference.ANY;
        preference.lifestyleSimilarity = SimilarityPreference.ANY;
        preference.loveLanguageSimilarity = SimilarityPreference.ANY;
        preference.energyLevelSimilarity = SimilarityPreference.ANY;
        preference.connectionType = null;
        return preference;
    }

    private UserCoordinates requireActiveCoordinate(UserProfile profile) {
        return profile.getActiveCoordinate();
    }

    private Instant declineThreshold() {
        return Instant.now().minus(Duration.ofDays(Math.max(0, declineCooldownDays)));
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

    /**
     * Perfis cuja recusa já saiu da carência: voltam ao pool, mas com penalidade
     * (MatchScoringPolicy.PENALTY_RESHOWN_AFTER_COOLDOWN) para reaparecerem no fim da fila.
     */
    private Set<UUID> collectReshownProfileIds(UserProfile currentProfile, Instant declineThreshold) {
        LinkedHashSet<UUID> reshownProfileIds = new LinkedHashSet<>();
        for (UserPossibleMatch match : UserPossibleMatch.listExpiredDeclines(currentProfile, declineThreshold)) {
            if (match.starterProfile != null && !Objects.equals(match.starterProfile.id, currentProfile.id)) {
                reshownProfileIds.add(match.starterProfile.id);
            }
            if (match.pendingProfile != null && !Objects.equals(match.pendingProfile.id, currentProfile.id)) {
                reshownProfileIds.add(match.pendingProfile.id);
            }
        }
        return reshownProfileIds;
    }

    // ------------------------------------------------------------------
    // Consultas de candidatos (B9)
    // ------------------------------------------------------------------

    private LinkedHashMap<UUID, CandidateSearchRow> loadCandidateRowsWithAgeExpansion(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            UserCoordinates currentCoordinate,
            Instant declineThreshold
    ) {
        LinkedHashMap<UUID, CandidateSearchRow> candidateRowsById = new LinkedHashMap<>();
        List<Integer> expansions = buildAgeExpansions(currentPreference);

        for (Integer expansion : expansions) {
            List<CandidateSearchRow> rows = currentCoordinate == null
                    ? executeNoLocationModeQuery(
                            currentProfile, currentPreference, expansion, PRESELECTION_LIMIT, declineThreshold)
                    : executeCandidateQuery(
                            currentProfile, currentPreference, currentCoordinate, expansion,
                            PRESELECTION_LIMIT, declineThreshold);

            for (CandidateSearchRow row : rows) {
                candidateRowsById.putIfAbsent(row.profileId(), row);
            }

            // B5.5: candidato sem coordenada entra por cota, não é excluído para sempre.
            int noCoordinateLimit = noCoordinateCandidateLimit();
            if (currentCoordinate != null
                    && noCoordinateLimit > 0
                    && candidateRowsById.size() < PRESELECTION_LIMIT) {
                List<CandidateSearchRow> noCoordinateRows = executeNoCoordinateCandidateQuery(
                        currentProfile, currentPreference, expansion, noCoordinateLimit, declineThreshold);
                for (CandidateSearchRow row : noCoordinateRows) {
                    candidateRowsById.putIfAbsent(row.profileId(), row);
                }
            }

            if (candidateRowsById.size() >= PRESELECTION_LIMIT) {
                break;
            }
        }

        return candidateRowsById;
    }

    private int noCoordinateCandidateLimit() {
        if (noLocationCandidateQuota <= 0d) {
            return 0;
        }
        return (int) Math.ceil(PRESELECTION_LIMIT * noLocationCandidateQuota);
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

    /**
     * Consulta principal: bounding box (indexável) antes do haversine e exclusão de decisões
     * por NOT EXISTS (substitui a lista de até 500 parâmetros nomeados `:excludedN`).
     */
    private List<CandidateSearchRow> executeCandidateQuery(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            UserCoordinates currentCoordinate,
            int ageExpansion,
            int limit,
            Instant declineThreshold
    ) {
        List<Integer> desiredGenderIds = desiredGenderIds(currentPreference);
        if (desiredGenderIds.isEmpty()) {
            return List.of();
        }

        Integer minAge = expandedMinAge(currentPreference.minAge, ageExpansion);
        Integer maxAge = expandedMaxAge(currentPreference.maxAge, ageExpansion);

        String sql = """
                with gender_filtered as (
                    select up.id as profile_id
                    from user_profiles up
                    join users u on u.id = up.fk_user
                    where up.id <> :currentProfileId
                      and u.verified = true
                      and (up.fk_gender in (%s) or up.fk_gender = 4) -- Id 4 é para prefiro não informar, então devem entrar em consideração
                      %s
                      %s
                ),
                bounded as (
                    select gf.profile_id, c.latitude, c.longitude
                    from gender_filtered gf
                    join user_coordinates c on c.fk_user_profile = gf.profile_id and c.active = true
                    where c.latitude between :currentLatitude - (:maxMatchDistanceKm / 111.045)
                                         and :currentLatitude + (:maxMatchDistanceKm / 111.045)
                      and c.longitude between :currentLongitude - (:maxMatchDistanceKm / (111.045 * greatest(cos(radians(:currentLatitude)), 0.01)))
                                          and :currentLongitude + (:maxMatchDistanceKm / (111.045 * greatest(cos(radians(:currentLatitude)), 0.01)))
                ),
                distance_filtered as (
                    select b.profile_id,
                           6371.0 * acos(least(1.0, greatest(-1.0,
                               cos(radians(:currentLatitude)) * cos(radians(b.latitude)) * cos(radians(b.longitude) - radians(:currentLongitude))
                               + sin(radians(:currentLatitude)) * sin(radians(b.latitude))
                           ))) as distance_km
                    from bounded b
                )
                select profile_id, distance_km
                from distance_filtered
                where distance_km <= :maxMatchDistanceKm
                order by distance_km asc
                limit :resultLimit
                """.formatted(namedParameters("gender", desiredGenderIds.size()), ageClause(minAge, maxAge), decisionExclusionClause());

        Query query = entityManager.createNativeQuery(sql);
        bindCommonParameters(query, currentProfile, desiredGenderIds, minAge, maxAge, declineThreshold);
        query.setParameter("currentLatitude", currentCoordinate.latitude.doubleValue());
        query.setParameter("currentLongitude", currentCoordinate.longitude.doubleValue());
        query.setParameter("maxMatchDistanceKm", currentPreference.maxMatchDistanceKm.doubleValue());
        query.setParameter("resultLimit", limit);

        return toCandidateRows(query);
    }

    /**
     * Modo sem geografia (B5.4): o usuário atual não tem coordenada ativa. Sem bounding box,
     * sem haversine — a distância vira null e o fator sai da normalização.
     */
    private List<CandidateSearchRow> executeNoLocationModeQuery(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            int ageExpansion,
            int limit,
            Instant declineThreshold
    ) {
        List<Integer> desiredGenderIds = desiredGenderIds(currentPreference);
        if (desiredGenderIds.isEmpty()) {
            return List.of();
        }

        Integer minAge = expandedMinAge(currentPreference.minAge, ageExpansion);
        Integer maxAge = expandedMaxAge(currentPreference.maxAge, ageExpansion);

        String sql = """
                select up.id as profile_id, cast(null as double precision) as distance_km
                from user_profiles up
                join users u on u.id = up.fk_user
                where up.id <> :currentProfileId
                  and u.verified = true
                  and (up.fk_gender in (%s) or up.fk_gender = 4)
                  %s
                  %s
                order by up.id desc
                limit :resultLimit
                """.formatted(namedParameters("gender", desiredGenderIds.size()), ageClause(minAge, maxAge), decisionExclusionClause());

        Query query = entityManager.createNativeQuery(sql);
        bindCommonParameters(query, currentProfile, desiredGenderIds, minAge, maxAge, declineThreshold);
        query.setParameter("resultLimit", limit);

        return toCandidateRows(query);
    }

    /**
     * Consulta complementar (B9.4): candidatos sem coordenada ativa, ordenados por id desc
     * (UUIDv7 ⇒ perfis mais recentes primeiro) e limitados pela cota configurada.
     */
    private List<CandidateSearchRow> executeNoCoordinateCandidateQuery(
            UserProfile currentProfile,
            UserMatchPreference currentPreference,
            int ageExpansion,
            int limit,
            Instant declineThreshold
    ) {
        List<Integer> desiredGenderIds = desiredGenderIds(currentPreference);
        if (desiredGenderIds.isEmpty()) {
            return List.of();
        }

        Integer minAge = expandedMinAge(currentPreference.minAge, ageExpansion);
        Integer maxAge = expandedMaxAge(currentPreference.maxAge, ageExpansion);

        String sql = """
                select up.id as profile_id, cast(null as double precision) as distance_km
                from user_profiles up
                join users u on u.id = up.fk_user
                where up.id <> :currentProfileId
                  and u.verified = true
                  and (up.fk_gender in (%s) or up.fk_gender = 4)
                  %s
                  and not exists (
                      select 1 from user_coordinates c
                      where c.fk_user_profile = up.id and c.active = true
                  )
                  %s
                order by up.id desc
                limit :noLocationLimit
                """.formatted(namedParameters("gender", desiredGenderIds.size()), ageClause(minAge, maxAge), decisionExclusionClause());

        Query query = entityManager.createNativeQuery(sql);
        bindCommonParameters(query, currentProfile, desiredGenderIds, minAge, maxAge, declineThreshold);
        query.setParameter("noLocationLimit", limit);

        return toCandidateRows(query);
    }

    /**
     * Faixa etária como range de datas: `birthdate` fica sozinha de um lado do operador,
     * então o índice idx_users_verified_birthdate é usado. A forma anterior
     * (`date_part('year', age(current_date, u.birthdate))`) é uma expressão sobre a coluna
     * e força seq scan.
     */
    private String ageClause(Integer minAge, Integer maxAge) {
        StringBuilder ageClause = new StringBuilder();
        if (minAge != null) {
            // idade >= minAge  <=>  birthdate <= hoje - minAge anos
            ageClause.append(" and u.birthdate <= current_date - make_interval(years => :minAge)");
        }
        if (maxAge != null) {
            // idade <= maxAge  <=>  birthdate > hoje - (maxAge + 1) anos
            ageClause.append(" and u.birthdate > current_date - make_interval(years => :maxAgePlusOne)");
        }
        return ageClause.toString();
    }

    /**
     * Exclusão de perfis já decididos e de recusas ainda em carência.
     *
     * Uma linha sem `declined_at` representa uma relação viva (eu já decidi, ou o outro já
     * respondeu) e exclui o perfil para sempre. Uma linha COM `declined_at` só exclui enquanto
     * a carência não expira — depois disso o perfil volta ao pool com penalidade (B6).
     */
    private String decisionExclusionClause() {
        return """
                 and not exists (
                     select 1
                     from user_possible_matches m
                     where (
                               (m.fk_starter_user_profile = :currentProfileId and m.fk_pending_user_profile = up.id)
                            or (m.fk_pending_user_profile = :currentProfileId and m.fk_starter_user_profile = up.id)
                           )
                       and (
                               (m.declined_at is null
                                   and (m.fk_starter_user_profile = :currentProfileId or m.pending_accepted is not null))
                            or (m.declined_at is not null and m.declined_at >= :declineThreshold)
                           )
                 )
                """;
    }

    private void bindCommonParameters(
            Query query,
            UserProfile currentProfile,
            List<Integer> desiredGenderIds,
            Integer minAge,
            Integer maxAge,
            Instant declineThreshold
    ) {
        query.setParameter("currentProfileId", currentProfile.id);
        query.setParameter("declineThreshold", OffsetDateTime.ofInstant(declineThreshold, ZoneOffset.UTC));

        for (int index = 0; index < desiredGenderIds.size(); index++) {
            query.setParameter("gender" + index, desiredGenderIds.get(index));
        }

        if (minAge != null) {
            query.setParameter("minAge", minAge);
        }
        if (maxAge != null) {
            query.setParameter("maxAgePlusOne", maxAge + 1);
        }
    }

    @SuppressWarnings("unchecked")
    private List<CandidateSearchRow> toCandidateRows(Query query) {
        List<Object[]> rawRows = query.getResultList();
        List<CandidateSearchRow> candidateRows = new ArrayList<>(rawRows.size());
        for (Object[] row : rawRows) {
            Double distanceKm = row[1] == null ? null : ((Number) row[1]).doubleValue();
            candidateRows.add(new CandidateSearchRow(toUuid(row[0]), distanceKm));
        }
        return candidateRows;
    }

    private List<Integer> desiredGenderIds(UserMatchPreference currentPreference) {
        if (currentPreference.desiredGenders == null || currentPreference.desiredGenders.isEmpty()) {
            return List.of();
        }

        return currentPreference.desiredGenders.stream()
                .map(gender -> gender.id)
                .filter(Objects::nonNull)
                .toList();
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

    /**
     * Uma única query com fetch join das associações @ManyToOne/@OneToOne usadas no scoring.
     * As coleções @ManyToMany continuam LAZY, mas são carregadas em lotes graças a
     * quarkus.hibernate-orm.fetch.batch-size — fazer fetch join das 6 coleções aqui geraria
     * produto cartesiano.
     */
    private LinkedHashMap<UUID, UserProfile> loadProfiles(Collection<UUID> profileIds) {
        LinkedHashMap<UUID, UserProfile> profilesById = new LinkedHashMap<>();
        if (profileIds.isEmpty()) {
            return profilesById;
        }

        List<UserProfile> profiles = UserProfile.<UserProfile>find(
                        "select distinct p from UserProfile p "
                                + "left join fetch p.user "
                                + "left join fetch p.matchPreference mp "
                                + "left join fetch mp.connectionType "
                                + "where p.id in ?1",
                        profileIds)
                .list();

        for (UserProfile profile : profiles) {
            profilesById.put(profile.id, profile);
        }
        return profilesById;
    }

    // ------------------------------------------------------------------
    // Conversões e utilitários
    // ------------------------------------------------------------------

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

    private MutualMatchSummaryResponse toMutualMatchSummaryResponse(UserProfile currentProfile, UserPossibleMatch match) {
        UserProfile otherProfile = Objects.equals(match.starterProfile.id, currentProfile.id)
                ? match.pendingProfile
                : match.starterProfile;
        User otherUser = otherProfile == null ? null : otherProfile.user;
        UserProfileImage activeProfilePicture = otherProfile == null ? null : UserProfileImage.findActiveProfilePicture(otherProfile);
        return new MutualMatchSummaryResponse(
                match.id,
                otherUser == null ? null : otherUser.id,
                otherProfile == null ? null : otherProfile.id,
                buildFullName(otherUser),
                calculateAge(otherUser),
                activeProfilePicture == null ? null : toMatchImageResponse(activeProfilePicture)
        );
    }

    private MutualMatchPageResponse toMutualMatchPageResponse(
            List<MutualMatchSummaryResponse> matches,
            int page,
            int size,
            long totalElements
    ) {
        int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new MutualMatchPageResponse(
                matches,
                page,
                size,
                totalElements,
                totalPages,
                page + 1 < totalPages
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

    private String buildFullName(User user) {
        if (user == null) {
            return null;
        }

        String firstName = user.name == null ? "" : user.name.trim();
        String lastName = user.lastName == null ? "" : user.lastName.trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? null : fullName;
    }

    private int validatePage(Integer page) {
        return PageParams.resolvePage(page);
    }

    private int validateSize(Integer size) {
        return PageParams.resolveSize(size);
    }

    /**
     * Resultado de um fator do scoring.
     *
     * @param applicable false quando não há informação suficiente dos dois lados para avaliar o fator;
     *                   nesse caso o peso é retirado do denominador da normalização.
     * @param obtained   pontos obtidos (0 .. weight)
     * @param weight     peso máximo do fator
     */
    private record FactorScore(boolean applicable, double obtained, double weight) {

        static FactorScore notApplicable(double weight) {
            return new FactorScore(false, 0d, weight);
        }

        static FactorScore of(double obtained, double weight) {
            return new FactorScore(true, Math.max(0d, Math.min(weight, obtained)), weight);
        }

        static FactorScore ratio(double ratio, double weight) {
            return of(weight * ratio, weight);
        }
    }

    /** Contexto de penalidades — reciprocidade e reapresentação pós-cooldown. */
    record ScoringContext(
            boolean genderNotReciprocal,
            boolean ageNotReciprocal,
            boolean reshownAfterCooldown
    ) {
        static ScoringContext empty() {
            return new ScoringContext(false, false, false);
        }
    }

    private record CandidateSearchRow(UUID profileId, Double distanceKm) {
    }

    private record ScoredCandidate(UUID profileId, double score, Double distanceKm) {
    }
}
