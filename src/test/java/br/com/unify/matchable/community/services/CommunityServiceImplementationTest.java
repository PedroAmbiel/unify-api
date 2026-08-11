package br.com.unify.matchable.community.services;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToLongFunction;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.community.dto.CommunityMemberHeaderResponse;
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

    @Test
    void listCommunitiesOrderingPutsMostPopularFirst() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community popular = buildNamedCommunity("Comunidade popular");
        Community quiet = buildNamedCommunity("Comunidade tranquila");
        ToLongFunction<Community> memberCounter = memberCounts(Map.of(popular.id, 40L, quiet.id, 2L));

        List<Community> ordered = service.sortByPopularity(List.of(quiet, popular), memberCounter);

        assertEquals(List.of("Comunidade popular", "Comunidade tranquila"), namesOf(ordered));
    }

    @Test
    void listCommunitiesOrderingBreaksPopularityTiesByName() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community alpha = buildNamedCommunity("alfa");
        Community bravo = buildNamedCommunity("Bravo");
        ToLongFunction<Community> memberCounter = memberCounts(Map.of(alpha.id, 5L, bravo.id, 5L));

        List<Community> ordered = service.sortByPopularity(List.of(bravo, alpha), memberCounter);

        assertEquals(List.of("alfa", "Bravo"), namesOf(ordered));
    }

    @Test
    void searchOrderingPutsNameMatchBeforeDescriptionMatch() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community nameMatch = buildNamedCommunity("Xadrez adaptado");
        nameMatch.description = "Encontros semanais";
        Community descriptionMatch = buildNamedCommunity("Clube de tabuleiro");
        descriptionMatch.description = "Também jogamos xadrez";
        // a comunidade com match apenas na descricao e mais popular: relevancia vence popularidade
        ToLongFunction<Community> memberCounter = memberCounts(Map.of(nameMatch.id, 1L, descriptionMatch.id, 99L));

        List<Community> ordered = service.sortByRelevance(List.of(descriptionMatch, nameMatch), "xadrez", memberCounter);

        assertEquals(List.of("Xadrez adaptado", "Clube de tabuleiro"), namesOf(ordered));
    }

    @Test
    void searchOrderingFallsBackToPopularityForEquivalentMatches() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community small = buildNamedCommunity("Xadrez iniciantes");
        Community big = buildNamedCommunity("Xadrez avançado");
        ToLongFunction<Community> memberCounter = memberCounts(Map.of(small.id, 3L, big.id, 30L));

        List<Community> ordered = service.sortByRelevance(List.of(small, big), "xadrez", memberCounter);

        assertEquals(List.of("Xadrez avançado", "Xadrez iniciantes"), namesOf(ordered));
    }

    @Test
    void paginateReturnsRequestedSliceAndClampsOutOfRangePages() throws Exception {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        List<String> items = List.of("a", "b", "c", "d", "e");

        assertEquals(List.of("a", "b"), invokePaginate(service, items, 0, 2));
        assertEquals(List.of("c", "d"), invokePaginate(service, items, 1, 2));
        assertEquals(List.of("e"), invokePaginate(service, items, 2, 2));
        assertEquals(List.of(), invokePaginate(service, items, 9, 2));
    }

    @Test
    void ensureAdminMembershipAllowsAdminOnly() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Community community = buildCommunity();

        assertDoesNotThrow(() -> service.ensureAdminMembership(
                buildMembership(community, buildUser(), CommunityMemberRole.ADMIN)
        ));

        SecurityException memberException = assertThrows(
                SecurityException.class,
                () -> service.ensureAdminMembership(buildMembership(community, buildUser(), CommunityMemberRole.MEMBER))
        );
        assertEquals("Apenas administradores da comunidade podem editar os dados dela", memberException.getMessage());

        SecurityException moderatorException = assertThrows(
                SecurityException.class,
                () -> service.ensureAdminMembership(buildMembership(community, buildUser(), CommunityMemberRole.MODERATOR))
        );
        assertEquals("Apenas administradores da comunidade podem editar os dados dela", moderatorException.getMessage());

        assertThrows(SecurityException.class, () -> service.ensureAdminMembership(null));
    }

    @Test
    void resolveCategoryReturnsNullWhenCategoryIdIsAbsent() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();

        assertNull(service.resolveCategory(null));
    }

    @Test
    void memberHeaderResponseCarriesJoinedAt() {
        CommunityServiceImplementation service = new CommunityServiceImplementation();
        Instant joinedAt = Instant.parse("2026-08-10T12:00:00Z");
        CommunityMembership membership = new CommunityMembership();
        membership.id = UUID.randomUUID();
        membership.community = buildCommunity();
        membership.role = CommunityMemberRole.MEMBER;
        membership.joinedAt = joinedAt;

        CommunityMemberHeaderResponse header = service.toMemberHeaderResponse(membership);

        assertEquals(joinedAt, header.joinedAt());
        assertEquals(CommunityMemberRole.MEMBER, header.role());
    }

    @SuppressWarnings("unchecked")
    private List<String> invokePaginate(
            CommunityServiceImplementation service,
            List<String> items,
            int page,
            int size
    ) throws Exception {
        Method method = CommunityServiceImplementation.class.getDeclaredMethod("paginate", List.class, int.class, int.class);
        method.setAccessible(true);
        return (List<String>) method.invoke(service, items, page, size);
    }

    private ToLongFunction<Community> memberCounts(Map<UUID, Long> counts) {
        return community -> counts.getOrDefault(community.id, 0L);
    }

    private List<String> namesOf(List<Community> communities) {
        return communities.stream().map(community -> community.name).toList();
    }

    private Community buildNamedCommunity(String name) {
        Community community = buildCommunity();
        community.name = name;
        return community;
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