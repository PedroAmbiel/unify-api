package br.com.unify.matchable.user.dto;

import br.com.unify.matchable.user.enums.FontScaleOption;

public record UserAccessibilitySettingsUpsertRequest(
        FontScaleOption fontScale,
        Boolean highContrast,
        Boolean screenReaderOptimized,
        Boolean reduceMotion
) {
}
