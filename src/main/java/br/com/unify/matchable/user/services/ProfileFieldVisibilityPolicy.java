package br.com.unify.matchable.user.services;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import br.com.unify.matchable.user.enums.ProfileField;
import br.com.unify.matchable.user.enums.ProfileFieldVisibility;

/**
 * Regra pura (sem banco) de quais partes do perfil um visitante NÃO vê.
 * Quem chama resolve as relações de follow; o dono do perfil nunca passa por
 * aqui (vê tudo).
 */
public final class ProfileFieldVisibilityPolicy {

    private ProfileFieldVisibilityPolicy() {
    }

    /** As relações só são consultadas se alguma parte depender delas. */
    public interface Relationship {
        /** O visitante segue o dono do perfil. */
        boolean viewerFollowsOwner();

        /** O dono do perfil segue o visitante. */
        boolean ownerFollowsViewer();
    }

    public static Set<ProfileField> hiddenFields(
            Map<ProfileField, ProfileFieldVisibility> configuration,
            Relationship relationship
    ) {
        Set<ProfileField> hidden = EnumSet.noneOf(ProfileField.class);
        Boolean viewerFollowsOwner = null;
        Boolean ownerFollowsViewer = null;

        for (Map.Entry<ProfileField, ProfileFieldVisibility> entry : configuration.entrySet()) {
            ProfileFieldVisibility visibility = entry.getValue();
            boolean visible;
            switch (visibility == null ? ProfileFieldVisibility.PUBLIC : visibility) {
                case HIDDEN -> visible = false;
                case FOLLOWERS_ONLY -> {
                    if (viewerFollowsOwner == null) {
                        viewerFollowsOwner = relationship.viewerFollowsOwner();
                    }
                    visible = viewerFollowsOwner;
                }
                case FOLLOWING_ONLY -> {
                    if (ownerFollowsViewer == null) {
                        ownerFollowsViewer = relationship.ownerFollowsViewer();
                    }
                    visible = ownerFollowsViewer;
                }
                default -> visible = true;
            }
            if (!visible) {
                hidden.add(entry.getKey());
            }
        }
        return hidden;
    }
}
