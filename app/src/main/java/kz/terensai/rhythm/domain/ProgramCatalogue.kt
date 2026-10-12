package kz.terensai.rhythm.domain

/**
 * Read-only discovery catalogue for B1. Not an executable timeline:
 * only a validated ProgramPlan + session snapshot may launch a new B2 session.
 */
data class ProgramCard(
    val key: String,
    val title: String,
    val durationMinutes: Int?,
    val description: String,
    val ritualSummary: String,
    val status: TemplateStatus
)

enum class TemplateStatus { PILOT, PREVIEW, UNVERIFIED }

object ProgramCatalogue {
    val programmes: List<ProgramCard> = listOf(
        ProgramCard("tanysu", "Танысу", 180, "Для первого знакомства", "3 коллективных парения", TemplateStatus.PILOT),
        ProgramCard("light", "Light", 240, "Базовая индивидуальная программа", "Прогрев и индивидуальная программа парения", TemplateStatus.PREVIEW),
        ProgramCard("grand", "Grand", 240, "Для компании и совместного отдыха", "2 коллективных парения", TemplateStatus.PREVIEW),
        ProgramCard("premium", "Premium", 300, "Насыщенная программа", "Парения, SPA и массаж", TemplateStatus.PREVIEW),
        ProgramCard("supreme", "Supreme", 300, "Индивидуальный ритуал", "Три этапа парения и SPA", TemplateStatus.PREVIEW),
        ProgramCard("select", "Select", null, "Дополнительный шаблон", "Временные рамки уточняются", TemplateStatus.UNVERIFIED),
        ProgramCard("orda", "Orda", null, "Дополнительный шаблон", "Временные рамки уточняются", TemplateStatus.UNVERIFIED),
        ProgramCard("tan-light", "Tan Light", null, "Дополнительный шаблон", "Временные рамки уточняются", TemplateStatus.UNVERIFIED),
        ProgramCard("special", "Special", null, "Дополнительный шаблон", "Временные рамки уточняются", TemplateStatus.UNVERIFIED)
    )
    fun byKey(key: String): ProgramCard? = programmes.firstOrNull { it.key == key }
}

/** Localized compact duration without attaching unsafe programme assumptions. */
fun formatDuration(minutes: Int): String {
    require(minutes >= 0)
    val hours = minutes / 60
    val rest = minutes % 60
    if (hours == 0) return "$rest мин"
    val suffix = when {
        hours % 100 in 11..14 -> "часов"
        hours % 10 == 1 -> "час"
        hours % 10 in 2..4 -> "часа"
        else -> "часов"
    }
    return if (rest == 0) "$hours $suffix" else "$hours ч $rest мин"
}
