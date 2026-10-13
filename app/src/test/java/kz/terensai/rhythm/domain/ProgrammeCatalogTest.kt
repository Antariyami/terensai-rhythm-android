package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class ProgrammeCatalogTest {
  @Test fun durationsFollowApprovedPackageCatalog() {
    val p=ProgrammeCatalog.programmes.associateBy { it.id }
    assertEquals(180,p.getValue("tanysu").durationMinutes)
    assertEquals(240,p.getValue("light").durationMinutes)
    assertEquals(240,p.getValue("grand").durationMinutes)
    assertEquals(300,p.getValue("premium").durationMinutes)
    assertEquals(300,p.getValue("supreme").durationMinutes)
  }
  @Test fun draftsHaveNoInventedDuration() {
    assertTrue(ProgrammeCatalog.programmes
      .filter { it.status==TemplateStatus.NEEDS_VERIFICATION }
      .all { it.durationMinutes==null })
  }
  @Test fun onlyPilotCanOpenPreviousTimer() {
    assertEquals(listOf("tanysu"),ProgrammeCatalog.programmes
      .filter { it.status==TemplateStatus.PILOT }.map { it.id })
  }
  @Test fun uniqueIdentifiersForNavigationAndArchives() {
    val ids=ProgrammeCatalog.programmes.map { it.id }
    assertEquals(ids.size,ids.toSet().size)
  }
  @Test fun russianDurationIsCorrect() {
    assertEquals("3 часа",displayDuration(180))
    assertEquals("4 часа",displayDuration(240))
    assertEquals("5 часов",displayDuration(300))
    assertEquals("1 ч 15 мин",displayDuration(75))
    assertEquals("Время уточняется",displayDuration(null))
  }
}
