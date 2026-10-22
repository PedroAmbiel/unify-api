package br.com.unify.matchable.scoring.enums;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Onde cada conjunto de pesos é usado. Cada grupo é salvo de uma vez pelo
 * backoffice e tem a própria revisão (scoring_group_revisions, V33).
 */
public enum ScoringGroup {

    MATCH("match", "Encontros",
            "Compatibilidade entre perfis na tela Encontros. Cada dimensão vale uma fatia do score "
                    + "(as fatias somam 100%); as penalidades são descontadas depois, em pontos."),

    FEED("feed", "Feed da aba Início",
            "Ranking das publicações do Início. Cada sinal soma pontos ao post; os tetos limitam "
                    + "quanto um sinal repetido pode somar."),

    COMMUNITY_DISCOVERY("community-discovery", "Descoberta de comunidades",
            "Ordem da aba Descobrir das comunidades: primeiro as que a pessoa ainda não participa, "
                    + "depois as de categoria afim; dentro de cada faixa, o engajamento abaixo decide.");

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

    private final String code;
    private final String label;
    private final String description;

    ScoringGroup(String code, String label, String description) {
        this.code = code;
        this.label = label;
        this.description = description;
    }

    /** Código público da URL ({@code /admin/scoring-weights/match}). */
    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Dimensões do grupo, na ordem de exibição. */
    public List<ScoringDimension> dimensions() {
        return Arrays.stream(ScoringDimension.values())
                .filter(dimension -> dimension.group() == this)
                .toList();
    }

    /** 100 quando o grupo tem dimensões percentuais (elas precisam somar 100); nulo nos demais. */
    public BigDecimal requiredPercentTotal() {
        return dimensions().stream().anyMatch(dimension -> dimension.unit() == ScoringUnit.PERCENT)
                ? ONE_HUNDRED
                : null;
    }

    /** Aceita o código da URL ("community-discovery") ou o nome da constante ("COMMUNITY_DISCOVERY"). */
    public static Optional<ScoringGroup> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(group -> group.code.equals(normalized) || group.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
