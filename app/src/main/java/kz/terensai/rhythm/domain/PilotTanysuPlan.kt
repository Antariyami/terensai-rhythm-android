package kz.terensai.rhythm.domain

/** B2.2 exploratory route, NOT an approved TERENSAI timing standard. */
data class PilotStageInput(val id: String, val label: String, val minutes: Int)
data class PilotTanysuRoute(
    val stages: List<PilotStageInput>,
    val remainingMinutes: Int,
    val valid: Boolean,
    val message: String
)

object PilotTanysuPlan {
    const val TOTAL_MINUTES = 180
    val defaults = listOf(
        PilotStageInput("welcome", "Встреча гостей", 15),
        PilotStageInput("steam1", "Коллективное парение №1", 12),
        PilotStageInput("rest1", "Отдых гостей №1", 35),
        PilotStageInput("steam2", "Коллективное парение №2", 12),
        PilotStageInput("rest2", "Отдых гостей №2", 35),
        PilotStageInput("steam3", "Коллективное парение №3", 12)
    )

    fun evaluate(stageMinutes: List<Int?>): PilotTanysuRoute {
        if (stageMinutes.size != defaults.size || stageMinutes.any { it == null || it <= 0 || it > TOTAL_MINUTES }) {
            return PilotTanysuRoute(defaults, 0, false, "Каждый этап должен содержать положительное количество минут")
        }
        val values = stageMinutes.filterNotNull()
        val used = values.sum()
        if (used >= TOTAL_MINUTES) return PilotTanysuRoute(
            defaults.mapIndexed { index, stage -> stage.copy(minutes = values[index]) },
            TOTAL_MINUTES - used, false,
            "Оставь время для завершающего отдыха и завершения программы"
        )
        return PilotTanysuRoute(
            defaults.mapIndexed { index, stage -> stage.copy(minutes = values[index]) },
            TOTAL_MINUTES - used,
            true, "Остаток автоматически выделен на свободное время и завершение"
        )
    }

    fun toPlan(stageMinutes: List<Int?>): ProgramPlan {
        val result = evaluate(stageMinutes)
        require(result.valid) { result.message }
        val all = result.stages + PilotStageInput(
            "finish", "Свободное время и завершение", result.remainingMinutes
        )
        var offset = 0L
        val stages = all.map {
            val start = offset
            val duration = it.minutes * 60_000L
            offset += duration
            StagePlan(it.id,it.label,Track.GUEST,start,duration)
        }
        check(offset == TOTAL_MINUTES * 60_000L)
        return ProgramPlan(
            "tanysu-pilot-b22",
            "Танысу · личный пилот",
            TOTAL_MINUTES * 60_000L,
            stages
        )
    }
}
