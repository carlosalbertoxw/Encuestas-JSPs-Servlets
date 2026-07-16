package encuestas.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AccountLockoutTest {

    @Test
    void locksAfterFiveFailures() {
        AccountLockout lockout = new AccountLockout();
        for (int i = 0; i < 4; i++) {
            lockout.recordFailure("c@c.c");
            assertFalse(lockout.isLocked("c@c.c"));
        }
        lockout.recordFailure("c@c.c");
        assertTrue(lockout.isLocked("c@c.c"));
    }

    @Test
    void lockIsCaseInsensitiveOnEmail() {
        AccountLockout lockout = new AccountLockout();
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure("C@C.C");
        }
        assertTrue(lockout.isLocked("c@c.c"));
    }

    @Test
    void resetClearsFailures() {
        AccountLockout lockout = new AccountLockout();
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure("c@c.c");
        }
        lockout.reset("c@c.c");
        assertFalse(lockout.isLocked("c@c.c"));
    }

    @Test
    void otherAccountsAreNotAffected() {
        AccountLockout lockout = new AccountLockout();
        for (int i = 0; i < 5; i++) {
            lockout.recordFailure("c@c.c");
        }
        assertFalse(lockout.isLocked("a@a.a"));
    }
}
