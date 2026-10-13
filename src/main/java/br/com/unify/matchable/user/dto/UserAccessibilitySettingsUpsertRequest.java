package br.com.unify.matchable.user.dto;

import br.com.unify.matchable.user.enums.FontScaleOption;

public record UserAccessibilitySettingsUpsertRequest(
        FontScaleOption fontScale,
        Boolean highContrast,
        Boolean reduceMotion,
        /** Nulo = mantém o valor salvo (clientes antigos não mandam o campo). */
        Boolean autoplayPresentationAudio
) {
}
