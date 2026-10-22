package br.com.unify.matchable.user.services;

import br.com.unify.matchable.user.dto.CurrentUserResponse;
import br.com.unify.matchable.user.dto.UserAccountUpdateRequest;
import br.com.unify.matchable.user.dto.UserPasswordChangeRequest;
import br.com.unify.matchable.user.entity.User;

public interface UserAccountService {

    CurrentUserResponse updateAccount(User user, UserAccountUpdateRequest request);

    /** Confere a senha atual e grava a nova. Quem chama decide o que fazer com as sessões. */
    void changePassword(User user, UserPasswordChangeRequest request);
}
