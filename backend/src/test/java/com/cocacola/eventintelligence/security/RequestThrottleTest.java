package com.cocacola.eventintelligence.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RequestThrottleTest {

    private static final String LIMIT_MESSAGE = "Demasiados intentos. Intenta nuevamente en unos minutos.";
    private static final String CAPACITY_MESSAGE = "Intenta nuevamente en unos minutos.";

    private RequestThrottle throttle;

    @BeforeEach
    void setUp() {
        throttle = new RequestThrottle();
    }

    // ---- límite por clave ----

    @Test
    void check_allowsRequestsUpToTheLimit() {
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> throttle.check("login:1.1.1.1", 3));
        }
    }

    @Test
    void check_rejectsRequestOverTheLimitWith429() {
        for (int i = 0; i < 3; i++) {
            throttle.check("login:1.1.1.1", 3);
        }

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class, () -> throttle.check("login:1.1.1.1", 3));

        assertEquals(429, thrown.getStatusCode().value());
        assertEquals(LIMIT_MESSAGE, thrown.getReason());
    }

    @Test
    void check_keepsRejectingWhileTheWindowIsActive() {
        throttle.check("k", 1);

        assertThrows(ResponseStatusException.class, () -> throttle.check("k", 1));
        assertThrows(ResponseStatusException.class, () -> throttle.check("k", 1));
    }

    @Test
    void check_rejectsImmediatelyWhenLimitIsZero() {
        assertThrows(ResponseStatusException.class, () -> throttle.check("k", 0));
    }

    @Test
    void check_tracksEachKeyIndependently() {
        throttle.check("a", 1);
        assertThrows(ResponseStatusException.class, () -> throttle.check("a", 1));

        assertDoesNotThrow(() -> throttle.check("b", 1));
    }

    // ---- expiración de la ventana (600 s) ----

    @Test
    void check_resetsCounterWhenWindowHasExpired() throws Exception {
        long now = Instant.now().getEpochSecond();
        attempts().put("k", window(now - 700, 99));

        assertDoesNotThrow(() -> throttle.check("k", 1));
        assertThrows(ResponseStatusException.class, () -> throttle.check("k", 1));
    }

    @Test
    void check_keepsCountingWhenWindowIsStillActive() throws Exception {
        long now = Instant.now().getEpochSecond();
        attempts().put("k", window(now - 100, 3));

        assertThrows(ResponseStatusException.class, () -> throttle.check("k", 3));
    }

    // ---- limpieza (más de 10 000 entradas) ----

    @Test
    void check_removesExpiredEntriesWhenMapExceeds10000() throws Exception {
        long now = Instant.now().getEpochSecond();
        fill("expired-", 10_000, now - 1_000);
        fill("fresh-", 5, now);

        throttle.check("new", 5);

        ConcurrentHashMap<String, Object> map = attempts();
        assertEquals(6, map.size());
        assertFalse(map.containsKey("expired-0"));
        assertTrue(map.containsKey("fresh-0"));
        assertTrue(map.containsKey("new"));
    }

    @Test
    void check_doesNotCleanUpWhenMapHasExactly10000Entries() throws Exception {
        long now = Instant.now().getEpochSecond();
        fill("expired-", 10_000, now - 1_000);

        throttle.check("new", 5);

        ConcurrentHashMap<String, Object> map = attempts();
        assertEquals(10_001, map.size());
        assertTrue(map.containsKey("expired-0"));
    }

    // ---- protección de capacidad (más de 20 000 entradas) ----

    @Test
    void check_rejectsNewKeysWhenMapExceeds20000ActiveEntries() throws Exception {
        long now = Instant.now().getEpochSecond();
        fill("active-", 20_001, now);

        ResponseStatusException thrown = assertThrows(
                ResponseStatusException.class, () -> throttle.check("new", 5));

        assertEquals(429, thrown.getStatusCode().value());
        assertEquals(CAPACITY_MESSAGE, thrown.getReason());
        assertFalse(attempts().containsKey("new"));
    }

    @Test
    void check_acceptsNewKeysWhenMapHasExactly20000Entries() throws Exception {
        long now = Instant.now().getEpochSecond();
        fill("active-", 20_000, now);

        assertDoesNotThrow(() -> throttle.check("new", 5));

        assertTrue(attempts().containsKey("new"));
        assertEquals(20_001, attempts().size());
    }

    // ---- concurrencia ----

    @Test
    void check_isAtomicUnderConcurrentAccess() throws Exception {
        int threads = 50;
        int limit = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startSignal = new CountDownLatch(1);
        AtomicInteger accepted = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        List<Future<Object>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                Callable<Object> task = () -> {
                    startSignal.await();
                    try {
                        throttle.check("shared", limit);
                        accepted.incrementAndGet();
                    } catch (ResponseStatusException ex) {
                        rejected.incrementAndGet();
                    }
                    return null;
                };
                futures.add(pool.submit(task));
            }
            startSignal.countDown();
            for (Future<Object> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(limit, accepted.get());
        assertEquals(threads - limit, rejected.get());
    }

    // ---- helpers (reflexión sobre el estado interno) ----

    @SuppressWarnings("unchecked")
    private ConcurrentHashMap<String, Object> attempts() throws Exception {
        Field field = RequestThrottle.class.getDeclaredField("attempts");
        field.setAccessible(true);
        return (ConcurrentHashMap<String, Object>) field.get(throttle);
    }

    private static Object window(long start, int count) throws Exception {
        Class<?> windowClass = Class.forName(RequestThrottle.class.getName() + "$Window");
        Constructor<?> constructor = windowClass.getDeclaredConstructor(long.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(start, count);
    }

    private void fill(String prefix, int amount, long start) throws Exception {
        ConcurrentHashMap<String, Object> map = attempts();
        Object window = window(start, 1);
        for (int i = 0; i < amount; i++) {
            map.put(prefix + i, window);
        }
    }
}