package br.com.unify.matchable.user.enums;

public enum FontScaleOption {
    SMALL(0.85),
    MEDIUM(1.0),
    LARGE(1.15),
    EXTRA_LARGE(1.30);

    private final double multiplier;

    FontScaleOption(double multiplier) {
        this.multiplier = multiplier;
    }

    public double getMultiplier() {
        return multiplier;
    }
}
