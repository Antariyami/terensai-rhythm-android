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
