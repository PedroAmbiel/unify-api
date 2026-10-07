package br.com.unify.matchable.community.services;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PostImageDescriptionTest {
    @Test void optionalForOldPostsAndPostsWithoutImages() {
        assertNull(PostImageDescription.normalize(null, false));
        assertNull(PostImageDescription.normalize(null, true));
        assertNull(PostImageDescription.normalize("  ", true));
    }
    @Test void preservesContentSeparatelyAndTrims() {
        assertEquals("Uma árvore", PostImageDescription.normalize(" Uma árvore ", true));
        assertEquals(240, PostImageDescription.normalize("a".repeat(240), true).length());
    }
    @Test void rejectsOrphansAndOverflow() {
        assertThrows(IllegalArgumentException.class, () -> PostImageDescription.normalize("Foto", false));
        assertThrows(IllegalArgumentException.class, () -> PostImageDescription.normalize("a".repeat(241), true));
    }
}
