package br.com.unify.matchable.user.dto;

import java.math.BigDecimal;

public record LocationResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        boolean active
) {
}