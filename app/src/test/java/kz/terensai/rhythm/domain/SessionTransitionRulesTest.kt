package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class SessionTransitionRulesTest {
    @Test fun lifecycleRulesDoNotAllowDuplicateCommands() {
        val r = SessionTransitionRules
        assertTrue(r.allowed(StageStatus.NOT_STARTED, EventKind.START))
        assertFalse(r.allowed(StageStatus.RUNNING, EventKind.START))
        assertTrue(r.allowed(StageStatus.RUNNING, EventKind.PAUSE))
        assertFalse(r.allowed(StageStatus.PAUSED, EventKind.PAUSE))
        assertTrue(r.allowed(StageStatus.PAUSED, EventKind.RESUME))
        assertFalse(r.allowed(StageStatus.COMPLETED, EventKind.RESUME))
        assertFalse(r.allowed(StageStatus.SKIPPED, EventKind.COMPLETE))
        assertTrue(r.allowed(StageStatus.NOT_STARTED, EventKind.SKIP))
    }

    @Test fun remainingAndOverrunDoNotConflict() {
        assertEquals(165_000L, SessionTransitionRules.remaining(900_000, 735_000))
        assertEquals(0L, SessionTransitionRules.overrun(900_000, 735_000))
        assertEquals(0L, SessionTransitionRules.remaining(900_000, 1_065_000))
        assertEquals(165_000L, SessionTransitionRules.overrun(900_000, 1_065_000))
    }

    @Test fun negativeIntervalsAreNotAccepted() {
        try {
            SessionTransitionRules.remaining(-1L, 5L)
            fail("Negative plan")
        } catch (_: IllegalArgumentException) { }
    }
}
