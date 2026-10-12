package kz.terensai.rhythm.domain

/** Immutable plan + append-only event log -> deterministic, testable plan/fact projection. */
class TimelineEngine(private val clock: Clock) {
    private class StageState(
        var status: StageStatus = StageStatus.NOT_STARTED,
        var start: Long? = null,
        var end: Long? = null,
        var completedMs: Long = 0,
        var runningSince: Long? = null
    )

    fun project(session: SessionPlan, events: List<SessionEvent>): SessionProgress {
        val now = maxOf(session.startedAtMs, clock.epochMillis())
        val states = session.planSnapshot.stages.associate { it.id to StageState() }
        events.distinctBy { it.id }
            .filter { it.atMs in session.startedAtMs..now }
            .sortedBy { it.atMs }
            .forEach { e ->
                val s = states[e.stageId] ?: return@forEach
                when (e.kind) {
                    EventKind.START -> if (s.status == StageStatus.NOT_STARTED) {
                        s.status = StageStatus.RUNNING; s.start = e.atMs; s.runningSince = e.atMs
                    }
                    EventKind.PAUSE -> if (s.status == StageStatus.RUNNING) {
                        s.completedMs += e.atMs - (s.runningSince ?: e.atMs)
                        s.runningSince = null; s.status = StageStatus.PAUSED
                    }
                    EventKind.RESUME -> if (s.status == StageStatus.PAUSED) {
                        s.runningSince = e.atMs; s.status = StageStatus.RUNNING
                    }
                    EventKind.COMPLETE, EventKind.SKIP -> {
                        val allowed = s.status == StageStatus.RUNNING || s.status == StageStatus.PAUSED ||
                            (e.kind == EventKind.SKIP && s.status == StageStatus.NOT_STARTED)
                        if (allowed) {
                            if (s.status == StageStatus.RUNNING) {
                                s.completedMs += e.atMs - (s.runningSince ?: e.atMs)
                            }
                            s.runningSince = null; s.end = e.atMs
                            s.status = if (e.kind == EventKind.COMPLETE) StageStatus.COMPLETED else StageStatus.SKIPPED
                        }
                    }
                }
            }
        val projected = session.planSnapshot.stages.map { plan ->
            val s = states.getValue(plan.id)
            val live = if (s.status == StageStatus.RUNNING) now - (s.runningSince ?: now) else 0L
            StageProgress(plan, s.status, s.start, s.end, s.completedMs + live,
                s.start?.minus(session.startedAtMs + plan.startOffsetMs))
        }
        val elapsed = now - session.startedAtMs
        return SessionProgress(
            elapsedWallClockMs = elapsed,
            remainingPlannedMs = maxOf(0L, session.planSnapshot.plannedDurationMs - elapsed),
            plannedEndMs = session.startedAtMs + session.planSnapshot.plannedDurationMs,
            stages = projected
        )
    }
}
