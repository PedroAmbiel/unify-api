package br.com.unify.matchable.scoring.enums;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import br.com.unify.matchable.admin.enums.LookupType;

class ScoringDimensionTest {

    @Test
    @DisplayName("Todo grupo tem dimensões e só os Encontros exigem soma 100")
    void everyGroupHasDimensions() {
        for (ScoringGroup group : ScoringGroup.values()) {
            assertFalse(group.dimensions().isEmpty(), group + " sem dimensões");
        }
        assertEquals(0, ScoringGroup.MATCH.requiredPercentTotal().compareTo(BigDecimal.valueOf(100)));
        assertNull(ScoringGroup.FEED.requiredPercentTotal());
        assertNull(ScoringGroup.COMMUNITY_DISCOVERY.requiredPercentTotal());
    }

    @Test
    @DisplayName("Padrão dentro dos limites; meia-vida nunca chega a zero (divisor do SQL do feed)")
    void defaultsRespectLimits() {
        for (ScoringDimension dimension : ScoringDimension.values()) {
            assertTrue(dimension.minWeight() >= 0d, dimension + ": mínimo negativo");
            assertTrue(dimension.defaultWeight() >= dimension.minWeight(), dimension + ": padrão abaixo do mínimo");
            assertTrue(dimension.defaultWeight() <= dimension.maxWeight(), dimension + ": padrão acima do máximo");
            if (dimension.unit() == ScoringUnit.HOURS) {
                assertTrue(dimension.minWeight() >= 1d, dimension + ": horas precisam de mínimo >= 1");
            }
            if (dimension.unit() == ScoringUnit.PERCENT) {
                assertEquals(100d, dimension.maxWeight(), dimension + ": fatia acima de 100%");
            }
            assertNotNull(dimension.section(), dimension + ": sem seção");
            assertTrue(dimension.name().length() <= 60, dimension + ": maior que scoring_weights.dimension");
            assertTrue(dimension.name().startsWith(dimension.group() == ScoringGroup.COMMUNITY_DISCOVERY
                    ? "COMMUNITY_DISCOVERY_" : dimension.group().name() + "_"), dimension + ": prefixo do grupo");
        }
        for (ScoringGroup group : ScoringGroup.values()) {
            assertTrue(group.name().length() <= 40, group + ": maior que scoring_group_revisions.scoring_group");
        }
    }

    @Test
    @DisplayName("Dimensão ligada a catálogo aponta para um catálogo do backoffice que existe")
    void lookupCodesExist() {
        for (ScoringDimension dimension : ScoringDimension.values()) {
            if (dimension.lookupCode() != null) {
                assertTrue(LookupType.fromCode(dimension.lookupCode()).isPresent(),
                        dimension + " aponta para o catálogo inexistente " + dimension.lookupCode());
            }
        }
    }

    @Test
    @DisplayName("fromCode aceita código da URL e nome da constante")
    void codesResolve() {
        assertEquals(ScoringGroup.COMMUNITY_DISCOVERY, ScoringGroup.fromCode("community-discovery").orElseThrow());
        assertEquals(ScoringGroup.MATCH, ScoringGroup.fromCode(" MATCH ").orElseThrow());
        assertTrue(ScoringGroup.fromCode("ranking").isEmpty());
        assertEquals(ScoringDimension.FEED_LIKE, ScoringDimension.fromCode("feed_like").orElseThrow());
        assertTrue(ScoringDimension.fromCode("FEED_NOPE").isEmpty());
        assertTrue(ScoringDimension.fromCode(null).isEmpty());
    }

    /**
     * %dev/%test sobem pelo import.sql (Hibernate drop-and-create, sem Flyway):
     * toda dimensão precisa estar lá com o padrão do código, e todo grupo com
     * revisão 0. Os seeds da V33 não entram aqui — migration aplicada não muda.
     */
    @Test
    @DisplayName("import.sql semeia toda dimensão com o padrão e todo grupo com revisão 0")
    void importSqlSeedsEveryDimension() throws IOException {
        Map<String, Double> seeded = new HashMap<>();
        Matcher matcher = Pattern.compile("\\('([A-Z_]+)',\\s*([0-9.]+)\\)").matcher(readResource("/import.sql"));
        while (matcher.find()) {
            seeded.put(matcher.group(1), Double.valueOf(matcher.group(2)));
        }

        for (ScoringDimension dimension : ScoringDimension.values()) {
            assertEquals(dimension.defaultWeight(), seeded.get(dimension.name()),
                    dimension + " sem seed (ou com valor diferente do padrão) no import.sql");
        }
        for (ScoringGroup group : ScoringGroup.values()) {
            assertEquals(0d, seeded.get(group.name()), group + " sem linha em scoring_group_revisions no import.sql");
        }
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = ScoringDimensionTest.class.getResourceAsStream(path)) {
            assertNotNull(input, path + " não está no classpath");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
