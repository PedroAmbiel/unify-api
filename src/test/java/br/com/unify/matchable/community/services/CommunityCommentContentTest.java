package br.com.unify.matchable.community.services;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CommunityCommentContentTest {
    @Test void allowsImageOnlyAndNormalizesDescription() {
        var content = CommunityCommentContent.normalize(null, " Uma árvore ", true);
        assertEquals("", content.body());
        assertEquals("Uma árvore", content.imageDescription());
    }
    @Test void preservesTextCommentsAndOptionalDescriptions() {
        assertEquals("Olá", CommunityCommentContent.normalize(" Olá ", null, false).body());
        assertNull(CommunityCommentContent.normalize("", "  ", true).imageDescription());
    }
    @Test void rejectsEmptyAndOrphanDescriptions() {
        assertThrows(IllegalArgumentException.class, () -> CommunityCommentContent.normalize(" ", null, false));
        assertThrows(IllegalArgumentException.class, () -> CommunityCommentContent.normalize("Olá", "Uma árvore", false));
    }
    @Test void enforcesStorageLimits() {
        assertThrows(IllegalArgumentException.class, () -> CommunityCommentContent.normalize("Olá", "x".repeat(241), true));
        assertThrows(IllegalArgumentException.class, () -> CommunityCommentContent.normalize("x".repeat(2001), null, false));
    }
}
