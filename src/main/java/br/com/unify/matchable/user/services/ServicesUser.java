package br.com.unify.matchable.user.services;

import java.time.Instant;

import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.user.entity.User;

public interface ServicesUser {

    User createUser(SignUpRequest request);

    User findByEmail(String email);

    void updatePassword(User user, String password, Instant updatedAt);

    default void updatePassword(User user, String password) {
        updatePassword(user, password, Instant.now());
    }
}
