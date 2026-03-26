package br.com.unify.matchable.auth.dto;

import br.com.unify.matchable.user.entity.SubscriptionMethod;

public record SignUpRequest(
        String name,
        String lastName,
        String login,
        String password,
        SubscriptionMethod subscriptionMethod
) {
}
