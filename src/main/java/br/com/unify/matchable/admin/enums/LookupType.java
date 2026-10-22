package br.com.unify.matchable.admin.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import br.com.unify.matchable.community.entity.CommunityCategory;
import br.com.unify.matchable.user.entity.AccessibilityNeed;
import br.com.unify.matchable.user.entity.AutonomyLevel;
import br.com.unify.matchable.user.entity.CommunicationForm;
import br.com.unify.matchable.user.entity.ConnectionType;
import br.com.unify.matchable.user.entity.Disability;
import br.com.unify.matchable.user.entity.EnergyLevel;
import br.com.unify.matchable.user.entity.Gender;
import br.com.unify.matchable.user.entity.InterestType;
import br.com.unify.matchable.user.entity.LifestyleType;
import br.com.unify.matchable.user.entity.LookupEntity;
import br.com.unify.matchable.user.entity.LoveLanguage;
import br.com.unify.matchable.user.entity.Pronoun;

/**
 * Registro central dos catálogos administráveis (semana 06). Um catálogo novo
 * no backoffice = uma linha aqui + migration da coluna {@code active} e da
 * sequência {@code seq_<tabela>_id}.
 *
 * {@code tableName} e {@code usageRefs} montam SQL dinâmico (contagem de uso,
 * nextval). São constantes deste enum e NUNCA vêm de input do usuário — o
 * código público da URL só serve para achar a constante.
 */
public enum LookupType {

    GENDERS("genders", "Gêneros", Gender.class, "genders", LookupIconMode.NONE, List.of(
            new UsageRef("user_profiles", "fk_gender"),
            new UsageRef("user_match_preference_desired_genders", "fk_gender"))),

    PRONOUNS("pronouns", "Pronomes", Pronoun.class, "pronouns", LookupIconMode.NONE, List.of(
            new UsageRef("user_profiles", "fk_pronouns"))),

    DISABILITIES("disabilities", "Deficiências", Disability.class, "disabilities", LookupIconMode.REQUIRED, List.of(
            new UsageRef("user_profile_disabilities", "fk_disability"))),

    ACCESSIBILITY_NEEDS("accessibility-needs", "Necessidades de acessibilidade", AccessibilityNeed.class,
            "accessibility_needs", LookupIconMode.NONE, List.of(
            new UsageRef("user_profile_accessibility_needs", "fk_accessibility_need"))),

    AUTONOMY_LEVELS("autonomy-levels", "Níveis de autonomia", AutonomyLevel.class, "autonomy_levels",
            LookupIconMode.NONE, List.of(
            new UsageRef("user_profiles", "fk_autonomy_level"))),

    COMMUNICATION_FORMS("communication-forms", "Formas de comunicação", CommunicationForm.class,
            "communication_forms", LookupIconMode.NONE, List.of(
            new UsageRef("user_profile_communication_forms", "fk_communication_form"))),

    LIFESTYLE_TYPES("lifestyle-types", "Estilos de vida", LifestyleType.class, "lifestyle_types",
            LookupIconMode.NONE, List.of(
            new UsageRef("user_profile_lifestyle_types", "fk_lifestyle_type"))),

    LOVE_LANGUAGES("love-languages", "Linguagens do amor", LoveLanguage.class, "love_languages",
            LookupIconMode.NONE, List.of(
            new UsageRef("user_profile_love_languages", "fk_love_language"))),

    ENERGY_LEVELS("energy-levels", "Níveis de energia", EnergyLevel.class, "energy_levels",
            LookupIconMode.NONE, List.of(
            new UsageRef("user_profiles", "fk_energy_level"))),

    INTEREST_TYPES("interest-types", "Interesses", InterestType.class, "interest_types",
            LookupIconMode.NONE, List.of(
            new UsageRef("user_profile_interest_types", "fk_interest_type"))),

    CONNECTION_TYPES("connection-types", "Tipos de conexão", ConnectionType.class, "connection_types",
            LookupIconMode.NONE, List.of(
            new UsageRef("user_match_preferences", "fk_connection_type"))),

    COMMUNITY_CATEGORIES("community-categories", "Categorias de comunidade", CommunityCategory.class,
            "community_categories", LookupIconMode.OPTIONAL, List.of(
            new UsageRef("communities", "fk_category")));

    /** Coluna FK que aponta para o catálogo: base da contagem de uso. */
    public record UsageRef(String table, String column) {
    }

    private final String code;
    private final String label;
    private final Class<? extends LookupEntity> entityClass;
    private final String tableName;
    private final LookupIconMode iconMode;
    private final List<UsageRef> usageRefs;

    LookupType(String code, String label, Class<? extends LookupEntity> entityClass,
               String tableName, LookupIconMode iconMode, List<UsageRef> usageRefs) {
        this.code = code;
        this.label = label;
        this.entityClass = entityClass;
        this.tableName = tableName;
        this.iconMode = iconMode;
        this.usageRefs = usageRefs;
    }

    public String code() {
        return code;
    }

    public String label() {
        return label;
    }

    public Class<? extends LookupEntity> entityClass() {
        return entityClass;
    }

    public String tableName() {
        return tableName;
    }

    public LookupIconMode iconMode() {
        return iconMode;
    }

    public List<UsageRef> usageRefs() {
        return usageRefs;
    }

    public String sequenceName() {
        return "seq_" + tableName + "_id";
    }

    /**
     * Itens referenciados por número fixo em regra de negócio: não podem ser
     * desativados nem excluídos (o SQL de discovery usa {@code fk_gender = 4}).
     */
    public boolean isProtectedId(Integer id) {
        return this == GENDERS && Integer.valueOf(Gender.UNDISCLOSED_ID).equals(id);
    }

    /** Aceita o código da URL ("interest-types") ou o nome da constante ("INTEREST_TYPES"). */
    public static Optional<LookupType> fromCode(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.code.equals(normalized) || type.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}
