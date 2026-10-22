package br.com.unify.matchable.scoring.enums;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * Toda dimensão com peso na pontuação da Unify: compatibilidade dos Encontros
 * (UserMatchServiceImplementation), ranking do feed da aba Início
 * (HomeFeedRankingPolicy) e descoberta de comunidades
 * (CommunityServiceImplementation.discoverCommunities).
 *
 * A REGRA de cada dimensão é código; o PESO é dado. O banco guarda só o peso
 * (scoring_weights, chave = nome da constante), a API mantém tudo em memória
 * (ScoringWeightRegistry) e o backoffice altera por
 * {@code PUT /admin/scoring-weights/{grupo}}. Os valores daqui são o padrão:
 * valem enquanto a dimensão não tiver linha no banco e voltam com "Restaurar
 * padrão" no backoffice.
 *
 * Dimensão nova = constante aqui + uso no cálculo + linha no seed da migration
 * e do import.sql. Renomear uma constante abandona o peso salvo da antiga.
 */
public enum ScoringDimension {

    // ---- Encontros: compatibilidade (fatias, somam 100) ------------------
    MATCH_COMMUNICATION(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 22, 100,
            "communication-forms", "Formas de comunicação",
            "Um canal em comum já garante 60% da fatia; o restante cresce com os canais compartilhados."),
    MATCH_INTERESTS(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 18, 100,
            "interest-types", "Interesses",
            "Interesses em comum, atenuados quando um perfil tem muito mais interesses que o outro."),
    MATCH_CONNECTION_TYPE(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 12, 100,
            "connection-types", "Tipo de conexão",
            "Mesmo tipo vale a fatia inteira; Amizade, Networking e Comunidade entre si valem metade."),
    MATCH_ACCESSIBILITY_NEEDS(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 12, 100,
            "accessibility-needs", "Necessidades de acessibilidade",
            "Segue a preferência de cada um (semelhante, diferente ou tanto faz). Dois perfis sem "
                    + "necessidades declaradas ganham a fatia inteira."),
    MATCH_LIFESTYLE(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 10, 100,
            "lifestyle-types", "Estilos de vida",
            "Semelhança ou diferença de estilos, conforme a preferência de cada um."),
    MATCH_AUTONOMY(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 8, 100,
            "autonomy-levels", "Nível de autonomia",
            "Distância entre os níveis, conforme a preferência de cada um."),
    MATCH_LOVE_LANGUAGE(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 6, 100,
            "love-languages", "Linguagens do amor",
            "Semelhança ou diferença, conforme a preferência de cada um."),
    MATCH_ENERGY(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 5, 100,
            "energy-levels", "Nível de energia",
            "Distância entre os níveis, conforme a preferência de cada um."),
    MATCH_DISTANCE(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 4, 100,
            null, "Distância",
            "Fatia inteira até 10 km, caindo até zero em 120 km. Sem localização, sai da conta."),
    MATCH_AGE_DIFFERENCE(ScoringGroup.MATCH, "Compatibilidade", ScoringUnit.PERCENT, 3, 100,
            null, "Diferença de idade",
            "Fatia inteira até 3 anos de diferença, caindo até zero em 20 anos."),

    // ---- Encontros: penalidades (pontos descontados depois da normalização)
    MATCH_PENALTY_GENDER_NOT_RECIPROCAL(ScoringGroup.MATCH, "Penalidades", ScoringUnit.POINTS, 8, 100,
            "genders", "Gênero fora da preferência do outro",
            "Descontado quando o gênero da pessoa não está entre os que o candidato procura. "
                    + "A preferência de gênero de quem busca continua sendo filtro, não peso."),
    MATCH_PENALTY_AGE_NOT_RECIPROCAL(ScoringGroup.MATCH, "Penalidades", ScoringUnit.POINTS, 5, 100,
            null, "Idade fora da faixa do outro",
            "Descontado quando a idade da pessoa fica fora da faixa etária que o candidato procura."),
    MATCH_PENALTY_RESHOWN_AFTER_COOLDOWN(ScoringGroup.MATCH, "Penalidades", ScoringUnit.POINTS, 10, 100,
            null, "Perfil recusado que voltou",
            "Descontado de quem já foi recusado e reaparece depois da carência, para ir ao fim da fila."),

    // ---- Feed da aba Início: origem do post --------------------------------
    FEED_FOLLOWING(ScoringGroup.FEED, "Origem do post", ScoringUnit.POINTS, 60, 1000,
            null, "Post de quem a pessoa segue",
            "Publicação pessoal de um perfil seguido."),
    FEED_MEMBER_COMMUNITY(ScoringGroup.FEED, "Origem do post", ScoringUnit.POINTS, 50, 1000,
            null, "Post de comunidade em que é membro",
            "Publicação de comunidade da qual a pessoa participa (pública ou privada)."),
    FEED_FOLLOWED_AUTHOR_IN_COMMUNITY(ScoringGroup.FEED, "Origem do post", ScoringUnit.POINTS, 25, 1000,
            null, "Pessoa seguida postando em comunidade pública",
            "Publicação de comunidade pública da qual a pessoa não participa, escrita por alguém que ela segue."),

    // ---- Feed: afinidade ------------------------------------------------------
    FEED_SHARED_INTEREST(ScoringGroup.FEED, "Afinidade", ScoringUnit.POINTS, 15, 1000,
            "interest-types", "Interesse em comum com o autor",
            "Por interesse em comum, nas publicações pessoais."),
    FEED_SHARED_INTERESTS_CAP(ScoringGroup.FEED, "Afinidade", ScoringUnit.POINTS, 45, 1000,
            "interest-types", "Teto dos interesses em comum",
            "Máximo que os interesses em comum podem somar num post."),
    FEED_SAME_CATEGORY(ScoringGroup.FEED, "Afinidade", ScoringUnit.POINTS, 20, 1000,
            "community-categories", "Categoria de comunidade afim",
            "Comunidade da mesma categoria de alguma em que a pessoa já participa."),
    FEED_FEATURED_COMMUNITY(ScoringGroup.FEED, "Afinidade", ScoringUnit.POINTS, 5, 1000,
            null, "Comunidade em destaque",
            "Comunidade marcada como destaque."),
    FEED_FOLLOWED_MEMBER(ScoringGroup.FEED, "Afinidade", ScoringUnit.POINTS, 10, 1000,
            null, "Pessoa seguida que é membro",
            "Por pessoa seguida que participa da comunidade do post."),
    FEED_FOLLOWED_MEMBERS_CAP(ScoringGroup.FEED, "Afinidade", ScoringUnit.POINTS, 30, 1000,
            null, "Teto das pessoas seguidas membros",
            "Máximo que as pessoas seguidas membros podem somar num post."),

    // ---- Feed: engajamento ----------------------------------------------------
    FEED_LIKE(ScoringGroup.FEED, "Engajamento", ScoringUnit.POINTS, 2, 1000,
            null, "Curtida", "Por curtida no post."),
    FEED_COMMENT(ScoringGroup.FEED, "Engajamento", ScoringUnit.POINTS, 4, 1000,
            null, "Comentário", "Por comentário ativo no post."),
    FEED_ENGAGEMENT_CAP(ScoringGroup.FEED, "Engajamento", ScoringUnit.POINTS, 30, 1000,
            null, "Teto do engajamento", "Máximo que curtidas e comentários somam juntos num post."),

    // ---- Feed: recência ---------------------------------------------------------
    FEED_RECENCY(ScoringGroup.FEED, "Recência", ScoringUnit.POINTS, 40, 1000,
            null, "Recência", "Pontos de um post recém-publicado; diminuem conforme o post envelhece."),
    FEED_RECENCY_HALF_LIFE_HOURS(ScoringGroup.FEED, "Recência", ScoringUnit.HOURS, 48, 1, 720,
            null, "Meia-vida da recência", "Horas até os pontos de recência caírem à metade."),

    // ---- Descoberta de comunidades: engajamento (desempate dentro da faixa) ----
    COMMUNITY_DISCOVERY_MEMBER(ScoringGroup.COMMUNITY_DISCOVERY, "Engajamento", ScoringUnit.POINTS, 3, 100,
            null, "Membro", "Por membro da comunidade."),
    COMMUNITY_DISCOVERY_POST(ScoringGroup.COMMUNITY_DISCOVERY, "Engajamento", ScoringUnit.POINTS, 2, 100,
            null, "Publicação", "Por publicação na comunidade."),
    COMMUNITY_DISCOVERY_LIKE(ScoringGroup.COMMUNITY_DISCOVERY, "Engajamento", ScoringUnit.POINTS, 1, 100,
            null, "Curtida", "Por curtida nas publicações da comunidade.");

    private final ScoringGroup group;
    private final String section;
    private final ScoringUnit unit;
    private final double defaultWeight;
    private final double minWeight;
    private final double maxWeight;
    private final String lookupCode;
    private final String label;
    private final String description;

    ScoringDimension(ScoringGroup group, String section, ScoringUnit unit, double defaultWeight, double maxWeight,
                     String lookupCode, String label, String description) {
        this(group, section, unit, defaultWeight, 0d, maxWeight, lookupCode, label, description);
    }

    ScoringDimension(ScoringGroup group, String section, ScoringUnit unit, double defaultWeight,
                     double minWeight, double maxWeight, String lookupCode, String label, String description) {
        this.group = group;
        this.section = section;
        this.unit = unit;
        this.defaultWeight = defaultWeight;
        this.minWeight = minWeight;
        this.maxWeight = maxWeight;
        this.lookupCode = lookupCode;
        this.label = label;
        this.description = description;
    }

    public ScoringGroup group() {
        return group;
    }

    /** Subtítulo do backoffice dentro do grupo (Compatibilidade, Penalidades, Afinidade...). */
    public String section() {
        return section;
    }

    public ScoringUnit unit() {
        return unit;
    }

    public double defaultWeight() {
        return defaultWeight;
    }

    public BigDecimal defaultWeightDecimal() {
        return BigDecimal.valueOf(defaultWeight);
    }

    public double minWeight() {
        return minWeight;
    }

    public double maxWeight() {
        return maxWeight;
    }

    /** Código do catálogo de perfil que alimenta a dimensão (LookupType.code()); nulo se não vem de catálogo. */
    public String lookupCode() {
        return lookupCode;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Nome da constante, sem diferenciar maiúsculas ("MATCH_COMMUNICATION"). */
    public static Optional<ScoringDimension> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(dimension -> dimension.name().equals(normalized)).findFirst();
    }
}
