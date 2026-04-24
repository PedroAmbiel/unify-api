package br.com.unify.matchable.user.services;

import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.common.enums.ErrorCode;
import br.com.unify.matchable.common.exceptions.ValidationException;
import br.com.unify.matchable.common.validation.PasswordValidator;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Instant;

@ApplicationScoped
public class ServicesUserImplementation implements ServicesUser {

    @Override
    @Transactional
    public User createUser(SignUpRequest request) {
        validatePassword(request.password());

        if (User.findByEmail(request.email()) != null) {
            throw new IllegalArgumentException("Usuário já existe!");
        }

        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = request.name();
        user.lastName = request.lastName();
        user.password = BcryptUtil.bcryptHash(request.password());
        user.email = request.email();
        user.verified = false;
        user.lastUpdatedAt = Instant.now();

        user.persist();
        return user;
    }

    @Override
    public User findByEmail(String email) {
        return User.findByEmail(email);
    }

    private void validatePassword(String password) {
        if (!PasswordValidator.hasMinimumLength(password)) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_PASSWORD_TOO_SHORT.getCode(),
                    ErrorCode.VALIDATION_PASSWORD_TOO_SHORT.name(),
                    ErrorCode.VALIDATION_PASSWORD_TOO_SHORT.getDefaultMessage()
            );
        }
        if (!PasswordValidator.isValid(password)) {
            throw new ValidationException(
                    ErrorCode.VALIDATION_INVALID_FORMAT.getCode(),
                    ErrorCode.VALIDATION_INVALID_FORMAT.name(),
                    ErrorCode.VALIDATION_INVALID_FORMAT.getMessage(PasswordValidator.COMPLEXITY_REQUIREMENTS_MESSAGE)
            );
        }
    }
}
