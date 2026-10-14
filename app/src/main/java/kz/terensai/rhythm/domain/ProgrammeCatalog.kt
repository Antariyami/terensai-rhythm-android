package kz.terensai.rhythm.domain

/**
 * The six approved catalogue cards. ONLY Tanysu has an executable built-in
 * itinerary. Non-pilot package durations/stages are never guessed.
 * Custom programme is created from explicitly entered stages.
 */
enum class TemplateStatus { PILOT, PREVIEW, NEEDS_VERIFICATION, CUSTOM }

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
    ProgrammeSummary(
      "tanysu","ТАНЫСУ","Т",180,
      "Пилотный банный маршрут","3 коллективных парения · тестовый план",TemplateStatus.PILOT
    ),
    ProgrammeSummary(
      "light","LIGHT","L",null,
      "Штатная программа TERENSAI","Маршрут и длительности ожидают утверждения",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "grand","GRAND","G",null,
      "Штатная программа TERENSAI","Маршрут и длительности ожидают утверждения",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "premium","PREMIUM","P",null,
      "Штатная программа TERENSAI","Маршрут и длительности ожидают утверждения",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "supreme","SUPREME","S",null,
      "Штатная программа TERENSAI","Маршрут и длительности ожидают утверждения",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "custom","Своя программа","+",null,
      "Создай маршрут из собственных этапов","Название, этапы, время и сьют задаёт мастер",TemplateStatus.CUSTOM
    )
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
