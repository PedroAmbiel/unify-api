package br.com.unify.matchable.common.geo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GeoUtilsTest {

    @Test
    void samePointIsZeroKilometers() {
        assertEquals(0d, GeoUtils.distanceKm(-23.5505, -46.6333, -23.5505, -46.6333), 1e-9);
    }

    @Test
    void saoPauloToRioIsAboutThreeHundredSixtyKilometers() {
        double distance = GeoUtils.distanceKm(-23.5505, -46.6333, -22.9068, -43.1729);
        assertEquals(357d, distance, 5d);
    }

    @Test
    void isSymmetric() {
        assertEquals(
                GeoUtils.distanceKm(-23.5505, -46.6333, -22.9068, -43.1729),
                GeoUtils.distanceKm(-22.9068, -43.1729, -23.5505, -46.6333),
                1e-9
        );
    }
}
