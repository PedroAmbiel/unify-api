package br.com.unify.matchable.user.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import br.com.unify.matchable.user.dto.LocationRequest;
import br.com.unify.matchable.user.entity.UserCoordinates;
import br.com.unify.matchable.user.entity.UserProfile;
import jakarta.persistence.EntityManager;

class UserProfileServiceImplementationTest {

    @Test
    void replaceActiveLocationFlushesBeforeAddingReplacementCoordinate() throws Exception {
        AtomicInteger flushCalls = new AtomicInteger();
        UserProfileServiceImplementation service = new UserProfileServiceImplementation();
        service.entityManager = entityManager(flushCalls);

        UserProfile profile = new UserProfile();
        UserCoordinates existingCoordinate = new UserCoordinates();
        existingCoordinate.active = true;
        profile.coordinates.add(existingCoordinate);

        invokeReplaceActiveLocation(service, profile, new LocationRequest(BigDecimal.valueOf(-23.55052), BigDecimal.valueOf(-46.633308)));

        assertEquals(1, flushCalls.get());
        assertFalse(existingCoordinate.active);
        assertEquals(2, profile.coordinates.size());

        UserCoordinates replacementCoordinate = profile.coordinates.getFirst();
        assertTrue(replacementCoordinate.active);
        assertSame(profile, replacementCoordinate.userProfile);
        assertEquals(BigDecimal.valueOf(-23.55052), replacementCoordinate.latitude);
        assertEquals(BigDecimal.valueOf(-46.633308), replacementCoordinate.longitude);
    }

    @Test
    void replaceActiveLocationSkipsFlushWhenThereIsNoActiveCoordinate() throws Exception {
        AtomicInteger flushCalls = new AtomicInteger();
        UserProfileServiceImplementation service = new UserProfileServiceImplementation();
        service.entityManager = entityManager(flushCalls);

        UserProfile profile = new UserProfile();

        invokeReplaceActiveLocation(service, profile, new LocationRequest(BigDecimal.ONE, BigDecimal.TEN));

        assertEquals(0, flushCalls.get());
        assertEquals(1, profile.coordinates.size());
        assertTrue(profile.coordinates.getFirst().active);
    }

    private void invokeReplaceActiveLocation(
            UserProfileServiceImplementation service,
            UserProfile profile,
            LocationRequest locationRequest
    ) throws Exception {
        Method replaceActiveLocation = UserProfileServiceImplementation.class.getDeclaredMethod(
                "replaceActiveLocation",
                UserProfile.class,
                LocationRequest.class
        );
        replaceActiveLocation.setAccessible(true);
        replaceActiveLocation.invoke(service, profile, locationRequest);
    }

    private EntityManager entityManager(AtomicInteger flushCalls) {
        return (EntityManager) Proxy.newProxyInstance(
                EntityManager.class.getClassLoader(),
                new Class<?>[] { EntityManager.class },
                (proxy, method, args) -> {
                    if ("flush".equals(method.getName())) {
                        flushCalls.incrementAndGet();
                        return null;
                    }
                    if ("toString".equals(method.getName())) {
                        return "TestEntityManager";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}