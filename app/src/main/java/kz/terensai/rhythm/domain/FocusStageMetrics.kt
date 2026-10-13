package kz.terensai.rhythm.domain

/** Pure projection for the large focus clock: never modifies a programme or event. */
data class FocusStageMetrics(
    val plannedMs: Long,
    val elapsedMs: Long,
    val remainingMs: Long,
    val overtimeMs: Long,
    val status: StageStatus,
    val overallElapsedMs: Long,
    val overallRemainingMs: Long,
    val startDeltaMs: Long?
) {
    val overtime: Boolean get() = overtimeMs > 0L
    val stageProgress: Float get() =
        if (plannedMs <= 0L) 1f else (elapsedMs.toDouble()/plannedMs).toFloat().coerceIn(0f,1f)
}

object FocusStageProjection {
    fun of(stage:StageProgress,progress:SessionProgress):FocusStageMetrics {
        val planned=stage.plan.plannedDurationMs.coerceAtLeast(0L)
        val elapsed=stage.elapsedMs.coerceAtLeast(0L)
        return FocusStageMetrics(
            plannedMs=planned,
            elapsedMs=elapsed,
            remainingMs=(planned-elapsed).coerceAtLeast(0L),
            overtimeMs=(elapsed-planned).coerceAtLeast(0L),
            status=stage.status,
            overallElapsedMs=progress.elapsedWallClockMs.coerceAtLeast(0L),
            overallRemainingMs=progress.remainingPlannedMs.coerceAtLeast(0L),
            startDeltaMs=stage.startDeltaMs
        )
    }
}
