package com.diet.service.session;

import com.diet.exception.SessionConflictException;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RedisSessionLockServiceTest {

    @Test
    void acquiresAndReleasesLockWithRedissonWatchdogMode() {
        AtomicInteger tryLockCalls = new AtomicInteger();
        AtomicInteger unlockCalls = new AtomicInteger();
        RLock lock = fakeLock(true, true, tryLockCalls, unlockCalls);
        RedisSessionLockService service = new RedisSessionLockService(clientReturning(lock), "diet:session-lock:", 5000);

        try (RedisSessionLockService.LockHandle handle = service.acquire(7L, "sess-1")) {
            handle.assertHeld();
        }

        assertEquals(1, tryLockCalls.get());
        assertEquals(1, unlockCalls.get());
    }

    @Test
    void returnsConflictWhenAnotherReplicaOwnsTheSession() {
        RLock lock = fakeLock(false, false, new AtomicInteger(), new AtomicInteger());
        RedisSessionLockService service = new RedisSessionLockService(clientReturning(lock), "diet:session-lock:", 5000);

        assertThrows(SessionConflictException.class, () -> service.acquire(7L, "sess-1"));
    }

    private RedissonClient clientReturning(RLock lock) {
        return (RedissonClient) Proxy.newProxyInstance(
                RedissonClient.class.getClassLoader(),
                new Class<?>[]{RedissonClient.class},
                (proxy, method, args) -> {
                    if ("getLock".equals(method.getName())) {
                        return lock;
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
    }

    private RLock fakeLock(
            boolean acquired,
            boolean initiallyHeld,
            AtomicInteger tryLockCalls,
            AtomicInteger unlockCalls
    ) {
        AtomicBoolean held = new AtomicBoolean(initiallyHeld);
        return (RLock) Proxy.newProxyInstance(
                RLock.class.getClassLoader(),
                new Class<?>[]{RLock.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "tryLock" -> {
                        tryLockCalls.incrementAndGet();
                        held.set(acquired);
                        yield acquired;
                    }
                    case "isHeldByCurrentThread" -> held.get();
                    case "unlock" -> {
                        unlockCalls.incrementAndGet();
                        held.set(false);
                        yield null;
                    }
                    case "toString" -> "fake-redisson-lock";
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
    }
}
