package br.com.unify.matchable.community.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.community.entity.CommunityMembership;
import br.com.unify.matchable.community.enums.CommunityMemberRole;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserProfile;

class CommunityServiceImplementationTest {

    @Test
    void moderatorCannotPromoteAnotherMemberToAdmin() throws Exception {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community community = buildCommunity();
        CommunityMembership moderatorMembership = buildMembership(community, buildUser(), CommunityMemberRole.MODERATOR);
        CommunityMembership targetMembership = buildMembership(community, buildUser(), CommunityMemberRole.MEMBER);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeEnsureCanUpdateRole(
                        service,
                moderatorMembership.userProfile.user,
                        community,
                        moderatorMembership,
                        targetMembership,
                        CommunityMemberRole.ADMIN
                )
        );

        assertInstanceOf(SecurityException.class, exception.getCause());
        assertEquals("Você não tem permissão para alterar o nível deste membro", exception.getCause().getMessage());
    }

    @Test
    void adminCanPromoteMemberToModerator() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community community = buildCommunity();
        CommunityMembership adminMembership = buildMembership(community, buildUser(), CommunityMemberRole.ADMIN);
        CommunityMembership targetMembership = buildMembership(community, buildUser(), CommunityMemberRole.MEMBER);

        assertDoesNotThrow(() -> invokeEnsureCanUpdateRole(
                service,
            adminMembership.userProfile.user,
                community,
                adminMembership,
                targetMembership,
                CommunityMemberRole.MODERATOR
        ));
    }

    @Test
    void ownerRoleCannotBeChanged() throws Exception {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community community = buildCommunity();
        User owner = community.owner;
        CommunityMembership adminMembership = buildMembership(community, buildUser(), CommunityMemberRole.ADMIN);
        CommunityMembership ownerMembership = buildMembership(community, owner, CommunityMemberRole.ADMIN);

        InvocationTargetException exception = assertThrows(
                InvocationTargetException.class,
                () -> invokeEnsureCanUpdateRole(
                        service,
                adminMembership.userProfile.user,
                        community,
                        adminMembership,
                        ownerMembership,
                        CommunityMemberRole.MEMBER
                )
        );

        assertInstanceOf(SecurityException.class, exception.getCause());
        assertEquals("Não é possível alterar o nível do proprietário da comunidade", exception.getCause().getMessage());
    }

    private void invokeEnsureCanUpdateRole(
            CommunityServiceImplementation service,
            User actor,
            Community community,
            CommunityMembership actorMembership,
            CommunityMembership targetMembership,
            CommunityMemberRole desiredRole
    ) throws Exception {
        Method method = CommunityServiceImplementation.class.getDeclaredMethod(
                "ensureCanUpdateRole",
                User.class,
                Community.class,
                CommunityMembership.class,
                CommunityMembership.class,
                CommunityMemberRole.class
        );
        method.setAccessible(true);
        method.invoke(service, actor, community, actorMembership, targetMembership, desiredRole);
    }

    private Community buildCommunity() {
        Community community = new Community();
        community.id = UUID.randomUUID();
        community.owner = buildUser();
        community.name = "Comunidade";
        return community;
    }

    private CommunityMembership buildMembership(Community community, User user, CommunityMemberRole role) {
        CommunityMembership membership = new CommunityMembership();
        membership.id = UUID.randomUUID();
        membership.community = community;
        membership.userProfile = buildUserProfile(user);
        membership.role = role;
        return membership;
    }

    private UserProfile buildUserProfile(User user) {
        UserProfile profile = new UserProfile();
        profile.id = UUID.randomUUID();
        profile.user = user;
        return profile;
    }

    private User buildUser() {
        User user = new User();
        user.id = UUID.randomUUID();
        user.email = "user@example.com";
        return user;
    }
}