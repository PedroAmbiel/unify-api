package br.com.unify.matchable.user.dto;

import java.util.Map;

/**
 * Corpo de PUT /users/me/privacy-settings. Atualização PARCIAL: campo nulo =
 * mantém o valor atual (a tela salva um toggle por vez). O mesmo vale para
 * {@code profileFieldVisibility}: só as partes enviadas mudam.
 */
public record UserPrivacySettingsUpdateRequest(
        Boolean discoverable,
        String feedVisibility,
        Boolean showDistance,
        Boolean showAge,
        Boolean followApprovalRequired,
        Map<String, String> profileFieldVisibility
) {

    public UserPrivacySettingsUpdateRequest(
            Boolean discoverable,
            String feedVisibility,
            Boolean showDistance,
            Boolean showAge
    ) {
        this(discoverable, feedVisibility, showDistance, showAge, null, null);
    }
}
