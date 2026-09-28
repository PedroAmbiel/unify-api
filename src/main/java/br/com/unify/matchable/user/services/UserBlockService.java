package br.com.unify.matchable.user.services;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import br.com.unify.matchable.user.dto.UserBlockResponse;
import br.com.unify.matchable.user.entity.User;

/**
 * Bloqueio entre usuários. Os efeitos em outros módulos (discovery, perfil
 * público, feed, follow, chat) consultam {@link #isBlockedEitherDirection} ou
 * {@link #relatedUserIds} — nunca a tabela direto — para a regra ficar num
 * lugar só. Exceção: as queries nativas de discovery/feed, que fazem o
 * mesmo filtro em SQL por performance.
 */
public interface UserBlockService {

    UserBlockResponse blockUser(User blocker, UUID blockedUserProfileId);

    void unblockUser(User blocker, UUID blockedUserProfileId);

    List<UserBlockResponse> listBlocked(User blocker);

    /** {@code users.id} dos dois lados. Simétrico. */
    boolean isBlockedEitherDirection(UUID userIdA, UUID userIdB);

    /** {@code users.id} de quem eu bloqueei e de quem me bloqueou. */
    Set<UUID> relatedUserIds(UUID userId);
}
