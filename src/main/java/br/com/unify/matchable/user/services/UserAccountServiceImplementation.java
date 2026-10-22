package br.com.unify.matchable.user.services;

import java.time.Instant;

import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.exceptions.ValidationException;
import br.com.unify.matchable.user.dto.CurrentUserResponse;
import br.com.unify.matchable.user.dto.UserAccountUpdateRequest;
import br.com.unify.matchable.user.dto.UserPasswordChangeRequest;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserAccountServiceImplementation implements UserAccountService {

    static final int NAME_MAX_LENGTH = 80;
    static final int CELLPHONE_MIN_DIGITS = 10;
    static final int CELLPHONE_MAX_DIGITS = 13;

    static final String REQUEST_REQUIRED_MESSAGE = "Corpo da requisição não informado";
    static final String NAME_REQUIRED_MESSAGE = "Informe o nome";
    static final String LAST_NAME_REQUIRED_MESSAGE = "Informe o sobrenome";
    static final String NAME_TOO_LONG_MESSAGE =
            "Nome e sobrenome aceitam até " + NAME_MAX_LENGTH + " caracteres cada";
    static final String CELLPHONE_INVALID_MESSAGE =
            "Celular deve ter de " + CELLPHONE_MIN_DIGITS + " a " + CELLPHONE_MAX_DIGITS + " dígitos, com DDD";
    static final String CELLPHONE_TAKEN_MESSAGE = "Este celular já está cadastrado em outra conta";
    static final String CURRENT_PASSWORD_REQUIRED_MESSAGE = "Informe a senha atual";
    static final String SAME_PASSWORD_MESSAGE = "A nova senha precisa ser diferente da atual";

    @Inject
    ServicesUser servicesUser;

    @Override
    @Transactional
    public CurrentUserResponse updateAccount(User user, UserAccountUpdateRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(REQUEST_REQUIRED_MESSAGE);
        }
        String name = requiredName(request.name(), NAME_REQUIRED_MESSAGE);
        String lastName = requiredName(request.lastName(), LAST_NAME_REQUIRED_MESSAGE);
        String cellphone = normalizeCellphone(request.cellphone());

        if (cellphone != null && User.count("cellphone = ?1 and id <> ?2", cellphone, user.id) > 0) {
            throw new IllegalStateException(CELLPHONE_TAKEN_MESSAGE);
        }

        user.name = name;
        user.lastName = lastName;
        user.cellphone = cellphone;
        user.lastUpdatedAt = Instant.now();
        return CurrentUserResponse.from(user);
    }

    @Override
    @Transactional
    public void changePassword(User user, UserPasswordChangeRequest request) {
        if (request == null) {
            throw new IllegalArgumentException(REQUEST_REQUIRED_MESSAGE);
        }
        if (request.currentPassword() == null || request.currentPassword().isEmpty()) {
            throw new IllegalArgumentException(CURRENT_PASSWORD_REQUIRED_MESSAGE);
        }
        if (!BcryptUtil.matches(request.currentPassword(), user.password)) {
            throw new ValidationException(
                    ErrorCode.USER_CURRENT_PASSWORD_INVALID.getCode(),
                    ErrorCode.USER_CURRENT_PASSWORD_INVALID.name(),
                    ErrorCode.USER_CURRENT_PASSWORD_INVALID.getDefaultMessage()
            );
        }
        if (request.currentPassword().equals(request.newPassword())) {
            throw new IllegalArgumentException(SAME_PASSWORD_MESSAGE);
        }
        // Tamanho e complexidade: mesma regra do cadastro e da redefinição.
        servicesUser.updatePassword(user, request.newPassword());
    }

    private static String requiredName(String value, String requiredMessage) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException(requiredMessage);
        }
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException(NAME_TOO_LONG_MESSAGE);
        }
        return trimmed;
    }

    /** Guarda só os dígitos (mesmo formato dos dados existentes, ex.: 5511990000001). */
    private static String normalizeCellphone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String digits = value.replaceAll("\\D", "");
        if (digits.length() < CELLPHONE_MIN_DIGITS || digits.length() > CELLPHONE_MAX_DIGITS) {
            throw new IllegalArgumentException(CELLPHONE_INVALID_MESSAGE);
        }
        return digits;
    }
}
