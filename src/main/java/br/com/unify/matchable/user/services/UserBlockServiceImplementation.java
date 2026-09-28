package br.com.unify.matchable.user.services;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.social.entity.UserFollow;
import br.com.unify.matchable.social.entity.UserFollowRequest;
import br.com.unify.matchable.user.dto.UserBlockResponse;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserBlock;
import br.com.unify.matchable.user.entity.UserPossibleMatch;
import br.com.unify.matchable.user.entity.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class UserBlockServiceImplementation implements UserBlockService {

    static final String TARGET_REQUIRED_MESSAGE = "Informe o perfil a ser bloqueado";
    static final String TARGET_NOT_FOUND_MESSAGE = "Perfil não encontrado";
    static final String SELF_BLOCK_MESSAGE = "Você não pode bloquear a si mesmo";
    static final String ALREADY_BLOCKED_MESSAGE = "Este usuário já está bloqueado";
    static final String NOT_BLOCKED_MESSAGE = "Este usuário não está bloqueado";

    @Override
    @Transactional
    public UserBlockResponse blockUser(User blocker, UUID blockedUserProfileId) {
        UserProfile blockedProfile = requireTargetProfile(blockedUserProfileId);
        User blocked = blockedProfile.user;

        if (blocker.id.equals(blocked.id)) {
            throw new IllegalArgumentException(SELF_BLOCK_MESSAGE);
        }
        if (UserBlock.findByBlockerAndBlocked(blocker, blocked) != null) {
            throw new IllegalStateException(ALREADY_BLOCKED_MESSAGE);
        }

        UserBlock block = new UserBlock();
        block.id = UUIDv7Generator.generate();
        block.blocker = blocker;
        block.blocked = blocked;
        block.createdAt = Instant.now();
        block.persist();

        UserProfile blockerProfile = UserProfile.findByUser(blocker);
        if (blockerProfile != null) {
            removeFollowsBothDirections(blockerProfile, blockedProfile);
            closePendingMatch(blockerProfile, blockedProfile, block.createdAt);
        }

        return new UserBlockResponse(blockedProfile.id, buildFullName(blocked), block.createdAt);
    }

    @Override
    @Transactional
    public void unblockUser(User blocker, UUID blockedUserProfileId) {
        UserProfile blockedProfile = requireTargetProfile(blockedUserProfileId);

        UserBlock block = UserBlock.findByBlockerAndBlocked(blocker, blockedProfile.user);
        if (block == null) {
            throw new NoSuchElementException(NOT_BLOCKED_MESSAGE);
        }
        // Só desfaz o bloqueio: follows removidos NÃO voltam (cada um decide seguir de novo).
        block.delete();
    }

    @Override
    public List<UserBlockResponse> listBlocked(User blocker) {
        return UserBlock.listByBlocker(blocker).stream()
                .map(block -> {
                    UserProfile blockedProfile = UserProfile.findByUser(block.blocked);
                    return new UserBlockResponse(
                            blockedProfile == null ? null : blockedProfile.id,
                            buildFullName(block.blocked),
                            block.createdAt
                    );
                })
                .toList();
    }

    @Override
    public boolean isBlockedEitherDirection(UUID userIdA, UUID userIdB) {
        return UserBlock.existsEitherDirection(userIdA, userIdB);
    }

    @Override
    public Set<UUID> relatedUserIds(UUID userId) {
        return UserBlock.listRelatedUserIds(userId);
    }

    /** EFEITO OBRIGATÓRIO: bloqueio remove follows (e pedidos para seguir) nos dois sentidos. */
    private void removeFollowsBothDirections(UserProfile blockerProfile, UserProfile blockedProfile) {
        UserFollow.delete(
                "(follower = ?1 and followed = ?2) or (follower = ?2 and followed = ?1)",
                blockerProfile, blockedProfile
        );
        // Pedidos pendentes em qualquer direção também somem.
        UserFollowRequest.deleteBetween(blockerProfile, blockedProfile);
    }

    /**
     * Convite de match ainda sem resposta entre as partes vira recusa de quem
     * bloqueou: sai da fila de convites dos dois lados. NUNCA delete em
     * user_possible_matches (V9: conversations tem on delete cascade para a
     * linha) — mesma regra de UserMatchServiceImplementation.registerDecision.
     * Match mútuo já confirmado fica como está: o perfil passa a dar 404 e o
     * chat recusa envio, então o efeito prático já é o do bloqueio.
     */
    private void closePendingMatch(UserProfile blockerProfile, UserProfile blockedProfile, Instant now) {
        UserPossibleMatch match = UserPossibleMatch.findBetween(blockerProfile, blockedProfile);
        if (match == null || match.declinedAt != null || match.pendingAccepted != null) {
            return;
        }
        match.declinedAt = now;
        match.declinedByProfile = blockerProfile;
    }

    private UserProfile requireTargetProfile(UUID userProfileId) {
        if (userProfileId == null) {
            throw new IllegalArgumentException(TARGET_REQUIRED_MESSAGE);
        }
        UserProfile profile = UserProfile.findById(userProfileId);
        if (profile == null || profile.user == null) {
            throw new NoSuchElementException(TARGET_NOT_FOUND_MESSAGE);
        }
        return profile;
    }

    static String buildFullName(User user) {
        String firstName = user.name == null ? "" : user.name.trim();
        String lastName = user.lastName == null ? "" : user.lastName.trim();
        String fullName = (firstName + " " + lastName).trim();
        return fullName.isEmpty() ? "Usuário" : fullName;
    }
}
