package kz.terensai.rhythm.domain

/** A catalog card is a preview; only B2 provides a persisted, versioned executable stage plan. */
enum class TemplateStatus { PILOT, PREVIEW, NEEDS_VERIFICATION }

data class ProgrammeSummary(
  val id: String,
  val name: String,
  val monogram: String,
  val durationMinutes: Int?,
  val summary: String,
  val structure: String,
  val status: TemplateStatus
)

object ProgrammeCatalog {
  val programmes = listOf(
    ProgrammeSummary("tanysu","Танысу","Т",180,"Знакомство с банной традицией","3 коллективных парения",TemplateStatus.PILOT),
    ProgrammeSummary("light","Light","L",240,"Индивидуальный формат","Прогрев и индивидуальное парение",TemplateStatus.PREVIEW),
    ProgrammeSummary("grand","Grand","G",240,"Программа для компании","2 коллективных парения",TemplateStatus.PREVIEW),
    ProgrammeSummary("premium","Premium","P",300,"Насыщенный банный ритуал","Парения, SPA и массаж",TemplateStatus.PREVIEW),
    ProgrammeSummary("supreme","Supreme","S",300,"Персональный банный ритуал","3 этапа парения и SPA",TemplateStatus.PREVIEW),
    ProgrammeSummary("select","Select","Se",null,"Детали уточняются","Время пока не подтверждено",TemplateStatus.NEEDS_VERIFICATION),
    ProgrammeSummary("orda","Orda","O",null,"Детали уточняются","Время пока не подтверждено",TemplateStatus.NEEDS_VERIFICATION),
    ProgrammeSummary("tan-light","Tan Light","TL",null,"Детали уточняются","Время пока не подтверждено",TemplateStatus.NEEDS_VERIFICATION),
    ProgrammeSummary("special","Special","Sp",null,"Детали уточняются","Время пока не подтверждено",TemplateStatus.NEEDS_VERIFICATION)
  )
  fun get(id:String) = programmes.firstOrNull { it.id == id }
}
fun displayDuration(minutes: Int?):String {
  if (minutes == null) return "Время уточняется"
  require(minutes >= 0)
  val h = minutes / 60; val m = minutes % 60
  val ending = when {
    h % 100 in 11..14 -> "часов"
    h % 10 == 1 -> "час"
    h % 10 in 2..4 -> "часа"
    else -> "часов"
  }
  return if (h == 0) "$m мин" else if (m == 0) "$h $ending" else "$h ч $m мин"
}
