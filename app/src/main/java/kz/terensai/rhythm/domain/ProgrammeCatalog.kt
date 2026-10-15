package kz.terensai.rhythm.domain

/**
 * The six approved catalogue cards. ONLY Tanysu has an executable built-in
 * itinerary. Advertised package durations are sourced from TERENSAI
 * price screenshots (15.10.2026); their exact stage timings remain unapproved.
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
      "light","LIGHT","L",240,
      "Ознакомительная программа для первого знакомства","Бесконтактный прогрев (экспресс) · индивидуальное парение",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "grand","GRAND","G",240,
      "Программа для компании 4–20 гостей","2 полноценных коллективных парения · отдых и уборка",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "premium","PREMIUM","P",300,
      "Банная программа с SPA и массажем","1 коллективное · индивидуальные парения · SPA",TemplateStatus.PREVIEW
    ),
    ProgrammeSummary(
      "supreme","SUPREME","S",300,
      "Индивидуальная программа для одного гостя","3 этапа индивидуального парения · 4 вида веников · SPA",TemplateStatus.PREVIEW
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
