package br.com.unify.matchable.post.entity;

import java.sql.Blob;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import br.com.unify.matchable.post.enums.PostOrigin;
import br.com.unify.matchable.community.entity.Community;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tabela única de publicações (ver {@link PostOrigin}). Posts de comunidade
 * têm {@code origin = COMMUNITY} e {@code community} obrigatório; posts
 * pessoais têm {@code origin = PERSONAL} e {@code community} nulo. As consultas
 * de comunidade filtram por {@code community}, então nunca enxergam posts
 * pessoais; as consultas do feed pessoal filtram por {@code origin}.
 * {@code active = false} é o soft delete dos posts pessoais (V13).
 */
@Entity
@Table(name = "posts")
@Check(constraints = "(origin = 'COMMUNITY' and fk_community is not null) or (origin = 'PERSONAL' and fk_community is null)")
public class Post extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_community", foreignKey = @ForeignKey(name = "fk_posts_community"))
    public Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, foreignKey = @ForeignKey(name = "fk_posts_user"))
    public User author;

    @Column(name = "body", nullable = false, length = 4000)
    public String body;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Lob
    @JdbcTypeCode(SqlTypes.BLOB)
    @Column(name = "media_oid", columnDefinition = "oid")
    public Blob mediaOid;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'COMMUNITY'")
    @Column(name = "origin", nullable = false, length = 20)
    public PostOrigin origin = PostOrigin.COMMUNITY;

    @ColumnDefault("true")
    @Column(name = "active", nullable = false)
    public boolean active = true;

    /** Instante da última edição do texto (V14); nulo = nunca editada. */
    @Column(name = "edited_at")
    public Instant editedAt;

    /**
     * Posts ativos pelos ids, na MESMA ordem da lista recebida (o ranking do
     * feed da aba Início decide a ordem em SQL; aqui só se hidrata a entidade).
     * Ids sem post ativo são ignorados.
     */
    public static List<Post> listActiveByIdsInOrder(List<UUID> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return List.of();
        }
        Map<UUID, Post> byId = new HashMap<>();
        for (Post post : Post.<Post>list("id in ?1 and active = true", orderedIds)) {
            byId.put(post.id, post);
        }
        return orderedIds.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    public static List<Post> listByCommunity(Community community) {
        return queryByCommunity(community).list();
    }

    /** Ordenacao estavel para paginacao: mais recentes primeiro (indice created_at). */
    public static PanacheQuery<Post> queryByCommunity(Community community) {
        return find("community = ?1 and active = true order by createdAt desc, id desc", community);
    }

    public static Post findByIdWithActiveCommunity(UUID id) {
        return find("id = ?1 and active = true and community.active = true", id).firstResult();
    }

    // ------------------------------------------------- feed pessoal (origin = PERSONAL)

    public static Post findActivePersonalById(UUID id) {
        return find("id = ?1 and origin = ?2 and active = true", id, PostOrigin.PERSONAL).firstResult();
    }

    public static PanacheQuery<Post> queryPersonalByAuthor(User author) {
        return find(
                "author = ?1 and origin = ?2 and active = true order by createdAt desc, id desc",
                author, PostOrigin.PERSONAL
        );
    }

    public static PanacheQuery<Post> queryPersonalByAuthorIds(Collection<UUID> authorUserIds) {
        return find(
                "author.id in ?1 and origin = ?2 and active = true order by createdAt desc, id desc",
                authorUserIds, PostOrigin.PERSONAL
        );
    }

    public static long countPersonalByAuthor(User author) {
        return count("author = ?1 and origin = ?2 and active = true", author, PostOrigin.PERSONAL);
    }
}
