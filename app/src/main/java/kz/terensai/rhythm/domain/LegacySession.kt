package kz.terensai.rhythm.domain

/** Raw v0.1 snapshot: preserve original journal instead of inventing historical event timestamps. */
data class LegacySession(
    val startedAtMs: Long,
    val stageStartedAtMs: Long,
    val accumulatedStageMs: Long,
    val stageIndex: Int,
    val paused: Boolean,
    val rawJournal: String
) {
    fun hasData() = startedAtMs > 0L || rawJournal.isNotBlank()
    fun stableId() = "legacy-v01-" + startedAtMs.coerceAtLeast(0L)
}
