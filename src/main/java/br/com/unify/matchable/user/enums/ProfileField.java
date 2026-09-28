package br.com.unify.matchable.user.enums;

/**
 * Partes do perfil público cuja visibilidade o dono controla uma a uma
 * (ver {@link ProfileFieldVisibility}). Idade e distância continuam nos
 * booleanos {@code showAge}/{@code showDistance} de UserPrivacySettings.
 *
 * <p>O nome do enum é gravado em {@code user_profile_field_visibilities.field}
 * e é a chave do mapa na API: não renomear sem migration.
 */
public enum ProfileField {
    BIO,
    GENDER,
    PRONOUNS,
    DISABILITIES,
    ACCESSIBILITY_NEEDS,
    AUTONOMY_LEVEL,
    COMMUNICATION_FORMS,
    LIFESTYLE_TYPES,
    LOVE_LANGUAGES,
    ENERGY_LEVEL,
    INTEREST_TYPES,
    /** Fotos da galeria (a foto de perfil fica sempre visível). */
    GALLERY,
    PRESENTATION_AUDIO
}
