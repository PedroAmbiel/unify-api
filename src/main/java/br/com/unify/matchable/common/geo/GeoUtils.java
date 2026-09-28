package br.com.unify.matchable.common.geo;

/**
 * Distância entre coordenadas em Java puro. É a MESMA fórmula (lei dos
 * cossenos esférica, raio 6371 km, argumento do acos limitado a [-1, 1]) do
 * SQL nativo de discovery em UserMatchServiceImplementation — o discovery
 * continua calculando no banco por performance; aqui serve para o perfil
 * público, que é um par só.
 */
public final class GeoUtils {

    public static final double EARTH_RADIUS_KM = 6371.0d;

    private GeoUtils() {
    }

    public static double distanceKm(double latitudeA, double longitudeA, double latitudeB, double longitudeB) {
        double latA = Math.toRadians(latitudeA);
        double latB = Math.toRadians(latitudeB);
        double deltaLongitude = Math.toRadians(longitudeB) - Math.toRadians(longitudeA);
        double cosine = Math.cos(latA) * Math.cos(latB) * Math.cos(deltaLongitude)
                + Math.sin(latA) * Math.sin(latB);
        return EARTH_RADIUS_KM * Math.acos(Math.min(1.0d, Math.max(-1.0d, cosine)));
    }
}
