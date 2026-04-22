package br.com.unify.matchable.user.services;

import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.user.entity.User;

public interface ServicesUser {

    User createUser(SignUpRequest request);

    User findByEmail(String email);
}
