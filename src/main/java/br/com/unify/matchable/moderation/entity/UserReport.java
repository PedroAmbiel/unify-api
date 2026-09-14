package br.com.unify.matchable.moderation.entity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.Check;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import br.com.unify.matchable.post.entity.Post;
import br.com.unify.matchable.moderation.enums.ReportReason;
import br.com.unify.matchable.moderation.enums.UserReportStatus;
import br.com.unify.matchable.user.entity.User;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Denúncia de perfil (reportedPost nulo) ou de publicação de comunidade
 * (reportedPost preenchido; reportedUser = autor do post). Um único conceito
 * para que o backoffice da semana 06 trate tudo numa tabela só.
 * Migration: V12__create_user_reports.sql.
 */
@Entity
@Table(name = "user_reports")
@Check(constraints = "fk_reporter <> fk_reported_user")
public class UserReport extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_reporter", nullable = false, foreignKey = @ForeignKey(name = "fk_user_reports_reporter"))
    public User reporter;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_reported_user", nullable = false, foreignKey = @ForeignKey(name = "fk_user_reports_reported_user"))
    public User reportedUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JoinColumn(name = "fk_reported_post", foreignKey = @ForeignKey(name = "fk_user_reports_reported_post"))
    public Post reportedPost;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    public ReportReason reason;

    @Column(name = "description", columnDefinition = "text")
    public String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    public UserReportStatus status;

    @Column(name = "created_at", nullable = false)
    public Instant createdAt;

    @Column(name = "resolved_at")
    public Instant resolvedAt;

    public static boolean existsOpenReport(User reporter, User reportedUser, Post reportedPost) {
        if (reportedPost == null) {
            return count(
                    "reporter = ?1 and reportedUser = ?2 and reportedPost is null and status = ?3",
                    reporter, reportedUser, UserReportStatus.OPEN
            ) > 0;
        }

        return count(
                "reporter = ?1 and reportedUser = ?2 and reportedPost = ?3 and status = ?4",
                reporter, reportedUser, reportedPost, UserReportStatus.OPEN
        ) > 0;
    }

    public static List<UserReport> listByReportedUser(User reportedUser) {
        return list("reportedUser = ?1 order by createdAt desc", reportedUser);
    }
}
