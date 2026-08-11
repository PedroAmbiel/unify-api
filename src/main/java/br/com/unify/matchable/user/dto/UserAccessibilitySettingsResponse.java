package br.com.unify.matchable.user.dto;

import br.com.unify.matchable.user.enums.FontScaleOption;

public record UserAccessibilitySettingsResponse(
        FontScaleOption fontScale,
        Double fontScaleMultiplier,
        boolean highContrast,
        boolean screenReaderOptimized,
        boolean reduceMotion
) {
}
