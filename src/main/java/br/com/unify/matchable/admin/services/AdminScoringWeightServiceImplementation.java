package br.com.unify.matchable.admin.services;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import br.com.unify.matchable.admin.dto.ScoringDimensionResponse;
import br.com.unify.matchable.admin.dto.ScoringGroupResponse;
import br.com.unify.matchable.admin.dto.ScoringWeightsUpdateRequest;
import br.com.unify.matchable.admin.enums.LookupType;
import br.com.unify.matchable.scoring.entity.ScoringGroupRevision;
import br.com.unify.matchable.scoring.entity.ScoringWeight;
import br.com.unify.matchable.scoring.enums.ScoringDimension;
import br.com.unify.matchable.scoring.enums.ScoringGroup;
import br.com.unify.matchable.scoring.enums.ScoringUnit;
import br.com.unify.matchable.scoring.services.ScoringWeightsChanged;
import br.com.unify.matchable.user.entity.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AdminScoringWeightServiceImplementation implements AdminScoringWeightService {

    /** Mesma escala de scoring_weights.weight numeric(7,2). */
    static final int MAX_DECIMAL_PLACES = 2;

    static final String WEIGHTS_REQUIRED_MESSAGE = "Informe ao menos um peso para alterar";
    static final String REVISION_REQUIRED_MESSAGE =
            "Informe a revisão que você estava editando (expectedRevision): ela impede sobrescrever "
                    + "a alteração de outra pessoa";
    static final String STALE_REVISION_MESSAGE =
            "Os pesos deste grupo foram alterados por outra pessoa enquanto você editava. "
                    + "Recarregue para ver os valores atuais e refaça a alteração.";

    @Inject
    EntityManager entityManager;

    @Inject
    AdminAuditService auditService;

    @Inject
    Event<ScoringWeightsChanged> weightsChanged;

    @Override
    @Transactional
    public List<ScoringGroupResponse> listGroups() {
        Map<ScoringDimension, BigDecimal> stored = loadStoredWeights();
        Map<String, ScoringGroupRevision> revisions = new HashMap<>();
        for (ScoringGroupRevision revision : ScoringGroupRevision.<ScoringGroupRevision>listAll()) {
            revisions.put(revision.scoringGroup, revision);
        }
        return Arrays.stream(ScoringGroup.values())
                .map(group -> toResponse(group, revisions.get(group.name()), effectiveWeights(group, stored)))
                .toList();
    }

    @Override
    @Transactional
    public ScoringGroupResponse getGroup(ScoringGroup group) {
        ScoringGroupRevision revision = ScoringGroupRevision.findById(group.name());
        return toResponse(group, revision, effectiveWeights(group, loadStoredWeights()));
    }

    @Override
    @Transactional
    public ScoringGroupResponse update(User admin, ScoringGroup group, ScoringWeightsUpdateRequest request) {
        if (request == null || request.weights() == null || request.weights().isEmpty()) {
            throw new IllegalArgumentException(WEIGHTS_REQUIRED_MESSAGE);
        }
        if (request.expectedRevision() == null) {
            throw new IllegalArgumentException(REVISION_REQUIRED_MESSAGE);
        }
        Map<ScoringDimension, BigDecimal> requested = parseRequested(group, request.weights());

        ScoringGroupRevision revision = lockRevision(group);
        if (revision.revision != request.expectedRevision()) {
            throw new IllegalStateException(STALE_REVISION_MESSAGE);
        }

        // Valida o grupo como ficará (o PUT pode mandar só parte das dimensões).
        EnumMap<ScoringDimension, BigDecimal> current = effectiveWeights(group, loadStoredWeights());
        EnumMap<ScoringDimension, BigDecimal> resulting = new EnumMap<>(current);
        resulting.putAll(requested);
        validatePercentTotal(group, resulting);

        List<String> changes = new ArrayList<>();
        for (Map.Entry<ScoringDimension, BigDecimal> entry : requested.entrySet()) {
            ScoringDimension dimension = entry.getKey();
            BigDecimal previous = current.get(dimension);
            BigDecimal next = entry.getValue();
            if (previous.compareTo(next) != 0) {
                store(dimension, next);
                changes.add(dimension.label() + ": " + format(previous) + " -> " + format(next));
            }
        }

        if (changes.isEmpty()) {
            return toResponse(group, revision, resulting); // nada mudou: sem nova revisão nem auditoria
        }

        revision.revision += 1;
        revision.updatedAt = Instant.now();
        revision.updatedBy = admin;

        auditService.record(admin, AdminAuditService.ACTION_SCORING_WEIGHTS_UPDATE,
                AdminAuditService.TARGET_SCORING_WEIGHTS, group.name(),
                "revisão " + revision.revision + " | " + String.join(" | ", changes));

        // A memória da API (ScoringWeightRegistry) só troca depois do commit.
        Map<ScoringDimension, Double> published = new EnumMap<>(ScoringDimension.class);
        resulting.forEach((dimension, weight) -> published.put(dimension, weight.doubleValue()));
        weightsChanged.fire(new ScoringWeightsChanged(group, revision.revision, published));

        return toResponse(group, revision, resulting);
    }

    // ------------------------------------------------------------------ apoio

    static Map<ScoringDimension, BigDecimal> parseRequested(ScoringGroup group, Map<String, BigDecimal> raw) {
        Map<ScoringDimension, BigDecimal> parsed = new EnumMap<>(ScoringDimension.class);
        for (Map.Entry<String, BigDecimal> entry : raw.entrySet()) {
            ScoringDimension dimension = ScoringDimension.fromCode(entry.getKey())
                    .orElseThrow(() -> new IllegalArgumentException("Dimensão '" + entry.getKey() + "' não existe"));
            if (dimension.group() != group) {
                throw new IllegalArgumentException(
                        "A dimensão '" + dimension.label() + "' não pertence ao grupo " + group.label());
            }
            parsed.put(dimension, validateWeight(dimension, entry.getValue()));
        }
        return parsed;
    }

    static BigDecimal validateWeight(ScoringDimension dimension, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException("Informe o peso de '" + dimension.label() + "'");
        }
        if (value.stripTrailingZeros().scale() > MAX_DECIMAL_PLACES) {
            throw new IllegalArgumentException("O peso de '" + dimension.label() + "' aceita no máximo "
                    + MAX_DECIMAL_PLACES + " casas decimais");
        }
        BigDecimal min = BigDecimal.valueOf(dimension.minWeight());
        BigDecimal max = BigDecimal.valueOf(dimension.maxWeight());
        if (value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw new IllegalArgumentException("O peso de '" + dimension.label() + "' deve ficar entre "
                    + format(min) + " e " + format(max) + unitSuffix(dimension.unit()));
        }
        return value.setScale(MAX_DECIMAL_PLACES);
    }

    /** As dimensões PERCENT do grupo são fatias do score: precisam fechar 100. */
    static void validatePercentTotal(ScoringGroup group, Map<ScoringDimension, BigDecimal> resulting) {
        BigDecimal required = group.requiredPercentTotal();
        if (required == null) {
            return;
        }
        BigDecimal total = resulting.entrySet().stream()
                .filter(entry -> entry.getKey().unit() == ScoringUnit.PERCENT)
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(required) != 0) {
            throw new IllegalArgumentException("As fatias em % de " + group.label() + " precisam somar "
                    + format(required) + "% (com estes valores a soma dá " + format(total) + "%)");
        }
    }

    /** "12,5" — o backoffice e as mensagens são pt-BR. */
    static String format(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String unitSuffix(ScoringUnit unit) {
        return switch (unit) {
            case PERCENT -> "%";
            case POINTS -> " pontos";
            case HOURS -> " horas";
        };
    }

    private ScoringGroupRevision lockRevision(ScoringGroup group) {
        // Os seeds (V33/import.sql) criam a linha; o insert cobre um banco em que ela sumiu.
        entityManager.createNativeQuery("insert into scoring_group_revisions (scoring_group, revision) "
                        + "values (:scoringGroup, 0) on conflict (scoring_group) do nothing")
                .setParameter("scoringGroup", group.name())
                .executeUpdate();
        // Trava até o commit: dois saves do mesmo grupo (mesmo em instâncias diferentes) entram em fila,
        // e o segundo encontra a revisão já incrementada.
        return entityManager.find(ScoringGroupRevision.class, group.name(), LockModeType.PESSIMISTIC_WRITE);
    }

    private static Map<ScoringDimension, BigDecimal> loadStoredWeights() {
        Map<ScoringDimension, BigDecimal> stored = new EnumMap<>(ScoringDimension.class);
        for (ScoringWeight row : ScoringWeight.<ScoringWeight>listAll()) {
            ScoringDimension.fromCode(row.dimension).ifPresent(dimension -> stored.put(dimension, row.weight));
        }
        return stored;
    }

    /** Pesos do grupo: o salvo ou, sem linha no banco, o padrão do código. */
    private static EnumMap<ScoringDimension, BigDecimal> effectiveWeights(
            ScoringGroup group, Map<ScoringDimension, BigDecimal> stored) {
        EnumMap<ScoringDimension, BigDecimal> weights = new EnumMap<>(ScoringDimension.class);
        for (ScoringDimension dimension : group.dimensions()) {
            weights.put(dimension, stored.getOrDefault(dimension, dimension.defaultWeightDecimal()));
        }
        return weights;
    }

    private void store(ScoringDimension dimension, BigDecimal weight) {
        // loadStoredWeights já trouxe as linhas para a sessão: o find não vai ao banco.
        ScoringWeight row = entityManager.find(ScoringWeight.class, dimension.name());
        if (row == null) {
            row = new ScoringWeight();
            row.dimension = dimension.name();
            row.weight = weight;
            row.persist();
            return;
        }
        row.weight = weight;
    }

    private static ScoringGroupResponse toResponse(
            ScoringGroup group, ScoringGroupRevision revision, Map<ScoringDimension, BigDecimal> weights) {
        List<ScoringDimensionResponse> dimensions = group.dimensions().stream()
                .map(dimension -> new ScoringDimensionResponse(
                        dimension.name(),
                        dimension.section(),
                        dimension.label(),
                        dimension.description(),
                        dimension.unit(),
                        dimension.lookupCode(),
                        lookupLabel(dimension),
                        weights.get(dimension).doubleValue(),
                        dimension.defaultWeight(),
                        dimension.minWeight(),
                        dimension.maxWeight()))
                .toList();
        BigDecimal requiredTotal = group.requiredPercentTotal();
        return new ScoringGroupResponse(
                group.code(),
                group.label(),
                group.description(),
                requiredTotal == null ? null : requiredTotal.doubleValue(),
                revision == null ? 0L : revision.revision,
                revision == null ? null : revision.updatedAt,
                revision == null || revision.updatedBy == null ? null : fullName(revision.updatedBy),
                dimensions);
    }

    private static String lookupLabel(ScoringDimension dimension) {
        return dimension.lookupCode() == null
                ? null
                : LookupType.fromCode(dimension.lookupCode()).map(LookupType::label).orElse(null);
    }

    private static String fullName(User user) {
        return (user.name + " " + user.lastName).trim();
    }
}
