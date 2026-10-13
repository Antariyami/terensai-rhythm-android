package kz.terensai.rhythm.domain

/**
 * Pure transition contract. A duplicate command cannot invent an extra stage interval.
 * The repository serializes commands in a Room transaction before consulting these rules.
 */
object SessionTransitionRules {
    fun allowed(status: StageStatus, command: EventKind): Boolean = when (command) {
        EventKind.START -> status == StageStatus.NOT_STARTED
        EventKind.PAUSE -> status == StageStatus.RUNNING
        EventKind.RESUME -> status == StageStatus.PAUSED
        EventKind.COMPLETE -> status == StageStatus.RUNNING || status == StageStatus.PAUSED
        EventKind.SKIP -> status == StageStatus.NOT_STARTED ||
            status == StageStatus.RUNNING || status == StageStatus.PAUSED
    }

    fun remaining(planMs: Long, elapsedMs: Long): Long {
        require(planMs >= 0 && elapsedMs >= 0)
        return (planMs - elapsedMs).coerceAtLeast(0)
    }

    fun overrun(planMs: Long, elapsedMs: Long): Long {
        require(planMs >= 0 && elapsedMs >= 0)
        return (elapsedMs - planMs).coerceAtLeast(0)
    }
}
