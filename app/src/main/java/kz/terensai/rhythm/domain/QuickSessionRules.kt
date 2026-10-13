package kz.terensai.rhythm.domain

/** Pure rules shared by on-screen minute buttons and keyboard validation. */
object QuickMinuteRules {
    fun adjust(values: List<String>, index: Int, delta: Int, total: Int = 180): List<String>? {
        if (index !in values.indices || delta !in setOf(-5, -1, 1, 5)) return null
        val minutes = values.map { it.toIntOrNull() ?: return null }
        if (minutes.any { it <= 0 }) return null
        val next = minutes[index] + delta
        if (next !in 1 until total) return null
        if (minutes.sum() - minutes[index] + next >= total) return null
        return values.toMutableList().also { it[index] = next.toString() }
    }
}

/** Independent timing signals: planned start deviation is NOT an overrun of stage duration. */
enum class ScheduleOffsetKind { EARLY, LATE, ON_TIME }
data class ScheduleOffset(val kind: ScheduleOffsetKind, val absoluteMillis: Long)
object ScheduleOffsets {
    fun classify(deltaMillis: Long): ScheduleOffset = when {
        deltaMillis < 0L -> ScheduleOffset(ScheduleOffsetKind.EARLY, -deltaMillis)
        deltaMillis > 0L -> ScheduleOffset(ScheduleOffsetKind.LATE, deltaMillis)
        else -> ScheduleOffset(ScheduleOffsetKind.ON_TIME, 0L)
    }
}

/** Guest and master tracks have separate stage intervals sharing the SAME overall clock.
 * For B3.3 draft/revision plans: do not modify the frozen ProgramPlan saved at session start.
 */
enum class WorkStageType { STEAM, GUEST_REST, CLEANUP, MASTER_RECOVERY, REPORT, PREPARATION, OTHER }
data class RouteChangeDraft(
    val sessionId: String,
    val originalPlanId: String,
    val stageInstanceId: String,
    val track: Track,
    val type: WorkStageType,
    val proposedDurationMs: Long,
    val reason: String = ""
) {
    init {
        require(sessionId.isNotBlank() && originalPlanId.isNotBlank())
        require(stageInstanceId.isNotBlank() && proposedDurationMs > 0L)
    }
}
