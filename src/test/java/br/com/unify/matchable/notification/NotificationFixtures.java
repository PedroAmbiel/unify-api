package br.com.unify.matchable.notification;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import br.com.unify.matchable.common.UUIDv7Generator;
import br.com.unify.matchable.notification.entity.Notification;
import br.com.unify.matchable.notification.enums.NotificationType;
import br.com.unify.matchable.user.entity.User;
import br.com.unify.matchable.user.entity.UserBlock;
import br.com.unify.matchable.user.entity.UserProfile;

/** Pessoas e consultas reaproveitadas pelos testes de notificação (dentro de transação). */
public final class NotificationFixtures {

    private NotificationFixtures() {
    }

    public record Person(User user, UserProfile profile) {
    }

    public static Person person(String name) {
        User user = new User();
        user.id = UUIDv7Generator.generate();
        user.name = name;
        user.lastName = "Aviso";
        user.email = "notification-" + UUID.randomUUID() + "@example.com";
        user.password = "hash";
        user.birthdate = LocalDate.now().minusYears(30);
        user.verified = true;
        user.lastUpdatedAt = Instant.now();
        user.persist();

        UserProfile profile = new UserProfile();
        profile.id = UUIDv7Generator.generate();
        profile.user = user;
        profile.bio = "bio";
        profile.persist();
        return new Person(user, profile);
    }

    public static void block(User blocker, User blocked) {
        UserBlock block = new UserBlock();
        block.id = UUIDv7Generator.generate();
        block.blocker = blocker;
        block.blocked = blocked;
        block.createdAt = Instant.now();
        block.persistAndFlush();
    }

    public static List<Notification> notificationsOf(User recipient) {
        return Notification.list("recipient = ?1 order by createdAt desc, id desc", recipient);
    }

    public static List<Notification> notificationsOf(User recipient, NotificationType type) {
        return Notification.list("recipient = ?1 and type = ?2 order by createdAt desc, id desc", recipient, type);
    }
}
