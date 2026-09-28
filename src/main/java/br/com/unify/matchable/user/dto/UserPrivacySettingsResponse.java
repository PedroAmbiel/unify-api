package br.com.unify.matchable.user.dto;

import java.util.Map;

/**
 * Retorno de GET/PUT /users/me/privacy-settings.
 *
 * @param profileFieldVisibility visibilidade de cada parte do perfil
 *                               (chave = ProfileField, valor = ProfileFieldVisibility).
 *                               Sempre traz todas as partes, com PUBLIC como default.
 */
public record UserPrivacySettingsResponse(
        boolean discoverable,
        String feedVisibility,
        boolean showDistance,
        boolean showAge,
        /** Quem quer me seguir precisa ser aceito (pedido para seguir). */
        boolean followApprovalRequired,
        Map<String, String> profileFieldVisibility
) {
}
