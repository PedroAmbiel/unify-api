package br.com.unify.matchable.auth.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.enums.UserRole;

class TokenServiceGroupsTest {

    @Test
    void regularUserGetsOnlyUserGroup() {
        assertEquals(Set.of("user"), TokenService.resolveGroups(userWith(UserRole.USER)));
    }

    @Test
    void adminAccumulatesUserAndAdminGroups() {
        assertEquals(Set.of("user", "admin"), TokenService.resolveGroups(userWith(UserRole.ADMIN)));
    }

    @Test
    void missingRoleFallsBackToUser() {
        assertEquals(Set.of("user"), TokenService.resolveGroups(userWith(null)));
    }

    private static User userWith(UserRole role) {
        User user = new User();
        user.role = role;
        return user;
    }
}
