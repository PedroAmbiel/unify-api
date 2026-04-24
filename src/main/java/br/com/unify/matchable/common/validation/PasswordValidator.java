package br.com.unify.matchable.common.validation;

import java.util.regex.Pattern;

public final class PasswordValidator {

    public static final String COMPLEXITY_REQUIREMENTS_MESSAGE = "senha deve conter ao menos uma letra maiuscula, uma minuscula, um numero e um caractere especial";

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9\\s]).{8,}$"
    );

    private PasswordValidator() {
    }

    public static boolean hasMinimumLength(String password) {
        return password != null && password.length() >= 8;
    }

    public static boolean isValid(String password) {
        if (!hasMinimumLength(password)) {
            return false;
        }
        return PASSWORD_PATTERN.matcher(password).matches();
    }
}