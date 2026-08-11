package br.com.unify.matchable.user.entity;

import java.time.Instant;
import java.util.UUID;

import br.com.unify.matchable.user.enums.FontScaleOption;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "user_accessibility_settings")
public class UserAccessibilitySettings extends PanacheEntityBase {

    @Id
    @Column(name = "id", updatable = false, nullable = false)
    public UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_user", nullable = false, unique = true, foreignKey = @ForeignKey(name = "fk_user_accessibility_settings_user"))
    public User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "font_scale", nullable = false, length = 20)
    public FontScaleOption fontScale = FontScaleOption.MEDIUM;

    @Column(name = "high_contrast", nullable = false)
    public boolean highContrast = false;

    @Column(name = "screen_reader_optimized", nullable = false)
    public boolean screenReaderOptimized = false;

    @Column(name = "reduce_motion", nullable = false)
    public boolean reduceMotion = false;

    @Column(name = "last_updated_at", nullable = false)
    public Instant lastUpdatedAt;

    public static UserAccessibilitySettings findByUser(User user) {
        return find("user", user).firstResult();
    }
}
