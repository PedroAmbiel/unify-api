package br.com.unify.matchable.user.services;

import br.com.unify.matchable.auth.dto.SignUpRequest;
import br.com.unify.matchable.common.UUIDv7Generator;
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
}
