package br.com.unify.matchable.user.dto;

import java.math.BigDecimal;

public record LocationRequest(
        BigDecimal latitude,
        BigDecimal longitude
) {
}