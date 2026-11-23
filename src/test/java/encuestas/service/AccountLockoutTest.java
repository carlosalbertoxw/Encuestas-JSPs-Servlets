package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AccountLockoutTest {

    private final InMemoryAttemptCounterStore store = new InMemoryAttemptCounterStore();
    private final AccountLockout lockout = new AccountLockout(store);

    private void fail(String email, int times) {
        for (int i = 0; i < times; i++) {
            lockout.recordFailure(email);
        }
    }

    @Test
    void locksAfterFiveFailures() {
        fail("c@c.c", AccountLockout.MAX_ATTEMPTS - 1);
        assertFalse(lockout.isLocked("c@c.c"));
        fail("c@c.c", 1);
        assertTrue(lockout.isLocked("c@c.c"));
    }

    @Test
    void lockIsCaseInsensitiveOnEmail() {
        fail("C@C.C", AccountLockout.MAX_ATTEMPTS);
        assertTrue(lockout.isLocked("c@c.c"));
    }

    @Test
    void resetClearsFailures() {
        fail("c@c.c", AccountLockout.MAX_ATTEMPTS);
        lockout.reset("c@c.c");
        assertFalse(lockout.isLocked("c@c.c"));
    }

    @Test
    void otherAccountsAreNotAffected() {
        fail("c@c.c", AccountLockout.MAX_ATTEMPTS);
        assertFalse(lockout.isLocked("a@a.a"));
    }

    @Test
    void lockExpiresWithTheWindow() {
        fail("c@c.c", AccountLockout.MAX_ATTEMPTS);
        store.advance(AccountLockout.WINDOW.plus(Duration.ofSeconds(1)));
        assertFalse(lockout.isLocked("c@c.c"));
    }

    @Test
    void rateLimiterRejectsAboveTheLimit() {
        RateLimiter limiter = new RateLimiter(store, "auth", 2, Duration.ofMinutes(1));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.1"));
        assertFalse(limiter.tryAcquire("10.0.0.1"));
        assertTrue(limiter.tryAcquire("10.0.0.2"));
    }
}
