package br.com.unify.matchable.community.services;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import br.com.unify.matchable.community.services.ImageDescriptionGenerationService.Snapshot;
class ImageDescriptionGenerationServiceTest {
    static class VisionStub extends ImageVisionService {
        int calls; byte[] received;
        @Override public String describe(byte[] image) { calls++; received = image; return "Gato dormindo na janela."; }
    }
    /** Runs the work inline: the transaction boundary itself is covered by the HTTP integration test. */
    static class Fake extends ImageDescriptionGenerationService {
        final VisionStub stub = new VisionStub(); int transactions;
        Fake() { vision = stub; }
        @Override protected <T> T inNewTransaction(Supplier<T> work) { transactions++; return work.get(); }
    }
    private final byte[] image = {1, 2, 3};

    @Test void storedDescriptionIsReusedWithoutCallingProvider() {
        Fake service = new Fake(); List<String> saved = new ArrayList<>();
        var result = service.describe(() -> new Snapshot(image, "  Descrição do autor.  ", null),
            (snapshot, text) -> { saved.add(text); return text; });
        assertEquals("Descrição do autor.", result.description());
        assertFalse(result.generated());
        assertEquals(0, service.stub.calls);
        assertTrue(saved.isEmpty());
    }

    @Test void missingDescriptionIsGeneratedOnceAndSavedInSeparateTransaction() {
        Fake service = new Fake(); Instant editedAt = Instant.now(); List<Snapshot> seen = new ArrayList<>();
        var result = service.describe(() -> new Snapshot(image, " ", editedAt),
            (snapshot, text) -> { seen.add(snapshot); return text; });
        assertEquals("Gato dormindo na janela.", result.description());
        assertTrue(result.generated());
        assertEquals(1, service.stub.calls);
        assertSame(image, service.stub.received);
        assertEquals(editedAt, seen.get(0).editedAt());
        assertEquals(2, service.transactions, "load and save run in different transactions, provider call outside both");
    }

    @Test void concurrentWinnerIsReturned() {
        Fake service = new Fake();
        var result = service.describe(() -> new Snapshot(image, null, null), (snapshot, text) -> "Gerada antes por outra requisição.");
        assertEquals("Gerada antes por outra requisição.", result.description());
    }
}
