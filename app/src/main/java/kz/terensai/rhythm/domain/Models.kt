package kz.terensai.rhythm.domain

/** Domain is pure Kotlin, without Android or database imports. Timestamps use Unix epoch milliseconds. */
fun interface Clock { fun epochMillis(): Long }
enum class Track { GUEST, MASTER }
enum class EventKind { START, PAUSE, RESUME, COMPLETE, SKIP }
enum class StageStatus { NOT_STARTED, RUNNING, PAUSED, COMPLETED, SKIPPED }

data class StagePlan(
    val id: String,
    val title: String,
    val track: Track,
    val startOffsetMs: Long,
    val plannedDurationMs: Long
) {
    init {
        require(id.isNotBlank() && title.isNotBlank())
        require(startOffsetMs >= 0 && plannedDurationMs >= 0)
    }
}

data class ProgramPlan(
    val id: String,
    val title: String,
    val plannedDurationMs: Long,
    val stages: List<StagePlan>
) {
    init {
        require(id.isNotBlank() && title.isNotBlank() && plannedDurationMs > 0)
        require(stages.map { it.id }.distinct().size == stages.size) { "Duplicate stage IDs" }
        require(stages.all { it.startOffsetMs <= plannedDurationMs &&
            it.plannedDurationMs <= plannedDurationMs - it.startOffsetMs }) { "Stage outside plan" }
    }
    fun snapshot(): ProgramPlan = copy(stages = stages.toList())
}

data class SessionPlan(val sessionId: String, val planSnapshot: ProgramPlan, val startedAtMs: Long) {
    init { require(sessionId.isNotBlank() && startedAtMs > 0L) }
}

data class SessionEvent(val id: String, val stageId: String, val kind: EventKind, val atMs: Long) {
    init { require(id.isNotBlank() && stageId.isNotBlank() && atMs >= 0L) }
}

data class StageProgress(
    val plan: StagePlan,
    val status: StageStatus,
    val actualStartedAtMs: Long?,
    val actualFinishedAtMs: Long?,
    val elapsedMs: Long,
    val startDeltaMs: Long?
)

data class SessionProgress(
    val elapsedWallClockMs: Long,
    val remainingPlannedMs: Long,
    val plannedEndMs: Long,
    val stages: List<StageProgress>
)
