package br.com.unify.matchable.auth.dto;

import java.time.LocalDate;

public record SignUpRequest(
        String name,
        String lastName,
        String email,
        String password,
        LocalDate birthdate
) {
}
