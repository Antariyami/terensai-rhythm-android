package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class TimelineEngineTest {
    private class TestClock(var now: Long) : Clock {
        override fun epochMillis() = now
    }
    private fun plan() = ProgramPlan(
        id = "demo", title = "Демо", plannedDurationMs = 180_000,
        stages = listOf(
            StagePlan("heat", "Парение", Track.GUEST, 10_000, 20_000),
            StagePlan("rest", "Отдых", Track.GUEST, 30_000, 40_000),
            StagePlan("clean", "Уборка", Track.MASTER, 30_000, 15_000)
        )
    )
    private fun session() = SessionPlan("session", plan().snapshot(), 1_000)
    private fun e(id: String, stage: String, kind: EventKind, at: Long) =
        SessionEvent(id, stage, kind, at)

    @Test fun pauseDoesNotCountRestInterval() {
        val c = TestClock(41_000)
        val actual = TimelineEngine(c).project(session(), listOf(
            e("a", "heat", EventKind.START, 11_000),
            e("b", "heat", EventKind.PAUSE, 16_000),
            e("c", "heat", EventKind.RESUME, 31_000)
        )).stages.first()
        assertEquals(15_000L, actual.elapsedMs)
        assertEquals(StageStatus.RUNNING, actual.status)
    }
    @Test fun programKeepsRunningWhenStagePaused() {
        val c = TestClock(121_000)
        val out = TimelineEngine(c).project(session(), listOf(
            e("1","heat", EventKind.START, 11_000),
            e("2","heat", EventKind.PAUSE, 16_000)
        ))
        assertEquals(120_000L, out.elapsedWallClockMs)
        assertEquals(60_000L, out.remainingPlannedMs)
        assertEquals(5_000L, out.stages[0].elapsedMs)
    }
    @Test fun repeatedCompletesDoNotOverwriteFirstFinish() {
        val out = TimelineEngine(TestClock(99_000)).project(session(), listOf(
            e("start", "heat", EventKind.START, 11_000),
            e("done", "heat", EventKind.COMPLETE, 21_000),
            e("done", "heat", EventKind.COMPLETE, 51_000),
            e("otherDone", "heat", EventKind.COMPLETE, 61_000)
        )).stages.first()
        assertEquals(10_000L, out.elapsedMs)
        assertEquals(21_000L, out.actualFinishedAtMs)
    }
    @Test fun parallelTracksNeverDoubleCountOverallSession() {
        val out = TimelineEngine(TestClock(61_000)).project(session(), listOf(
            e("g", "rest", EventKind.START, 31_000),
            e("m", "clean", EventKind.START, 31_000)
        ))
        assertEquals(60_000L, out.elapsedWallClockMs)
        assertEquals(30_000L, out.stages[1].elapsedMs)
        assertEquals(30_000L, out.stages[2].elapsedMs)
    }
    @Test fun deviationIsBasedOnFrozenPlan() {
        val p = plan()
        val session = SessionPlan("one", p.snapshot(), 1_000)
        val out = TimelineEngine(TestClock(80_000)).project(session, listOf(
            e("late", "heat", EventKind.START, 17_000)
        ))
        assertEquals(6_000L, out.stages[0].startDeltaMs)
        assertEquals(31_000L, out.stages[1].plan.startOffsetMs + 1_000)
    }
    @Test fun skipCannotBeCompletedLater() {
        val out = TimelineEngine(TestClock(81_000)).project(session(), listOf(
            e("skip", "heat", EventKind.SKIP, 15_000),
            e("complete", "heat", EventKind.COMPLETE, 25_000)
        )).stages.first()
        assertEquals(StageStatus.SKIPPED, out.status)
        assertEquals(null, out.actualStartedAtMs)
    }
    @Test fun ignoresUnknownStagesAndFutureEvents() {
        val out = TimelineEngine(TestClock(30_000)).project(session(), listOf(
            e("unknown", "missing", EventKind.START, 12_000),
            e("future", "heat", EventKind.START, 50_000)
        ))
        assertEquals(StageStatus.NOT_STARTED, out.stages[0].status)
    }
    @Test fun clockAfterLockScreenCalculatesFromRealTimestamps() {
        val c = TestClock(21_000)
        val events = listOf(e("go","heat",EventKind.START, 11_000))
        val engine = TimelineEngine(c)
        assertEquals(10_000L, engine.project(session(),events).stages[0].elapsedMs)
        c.now = 121_000
        assertEquals(110_000L, engine.project(session(),events).stages[0].elapsedMs)
    }
    @Test fun reopeningKeepsPriorTimeButExcludesGap() {
        val progress = TimelineEngine(TestClock(61_000)).project(session(), listOf(
            e("a","heat",EventKind.START,11_000),
            e("b","heat",EventKind.COMPLETE,21_000),
            e("c","heat",EventKind.REOPEN,41_000),
            e("d","heat",EventKind.COMPLETE,51_000)
        )).stages.first()
        assertEquals(StageStatus.COMPLETED,progress.status)
        assertEquals(20_000L,progress.elapsedMs)
        assertEquals(51_000L,progress.actualFinishedAtMs)
    }
    @Test fun previouslySkippedStageStartsOnReturn() {
        val progress = TimelineEngine(TestClock(61_000)).project(session(), listOf(
            e("a","heat",EventKind.SKIP,11_000),
            e("b","heat",EventKind.REOPEN,41_000),
            e("c","heat",EventKind.COMPLETE,51_000)
        )).stages.first()
        assertEquals(10_000L,progress.elapsedMs)
        assertEquals(41_000L,progress.actualStartedAtMs)
    }
    @Test fun duplicateStageIdsAreRejected() {
        try {
            ProgramPlan("bad","Bad", 10_000, listOf(
                StagePlan("a","One",Track.GUEST,0,1000),
                StagePlan("a","Two",Track.MASTER,0,1000)
            ))
            fail("Expected validation")
        } catch (_: IllegalArgumentException) { }
    }
    @Test fun legacyRecordKeepsOriginalUnverifiedHistory() {
        val old = LegacySession(1000,3000,24000,2,true,"Парение 1 — 11 минут")
        assertTrue(old.hasData())
        assertEquals("legacy-v01-1000", old.stableId())
        assertEquals("Парение 1 — 11 минут", old.rawJournal)
    }
    @Test fun emptyLegacyStateIsNotImported() {
        assertFalse(LegacySession(0,0,0,0,false,"").hasData())
    }
}
