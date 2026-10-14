package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class ProgrammeCatalogTest {
  @Test fun onlySixUserApprovedCardsInExactOrder() {
    assertEquals(
      listOf("tanysu","light","grand","premium","supreme","custom"),
      ProgrammeCatalog.programmes.map{it.id}
    )
    for(old in listOf("select","orda","tan-light","sunlight","special")){
      assertNull("retired package must not be displayed: $old",ProgrammeCatalog.get(old))
    }
  }
  @Test fun onlyPilotCanExecuteBuiltInItineraryAndFourTemplatesHaveUnverifiedTimes() {
    val p=ProgrammeCatalog.programmes.associateBy{it.id}
    assertEquals(180,p.getValue("tanysu").durationMinutes)
    assertEquals(TemplateStatus.PILOT,p.getValue("tanysu").status)
    for(id in listOf("light","grand","premium","supreme")){
      assertNull(p.getValue(id).durationMinutes)
      assertEquals(TemplateStatus.PREVIEW,p.getValue(id).status)
    }
    assertEquals(TemplateStatus.CUSTOM,p.getValue("custom").status)
    assertNull(p.getValue("custom").durationMinutes)
  }
  @Test fun uniqueStableIdentifiersAndRussianDurations(){
    val ids=ProgrammeCatalog.programmes.map{it.id}
    assertEquals(ids.size,ids.toSet().size)
    assertEquals("3 часа",displayDuration(180))
    assertEquals("4 часа",displayDuration(240))
    assertEquals("5 часов",displayDuration(300))
    assertEquals("1 ч 15 мин",displayDuration(75))
    assertEquals("Время уточняется",displayDuration(null))
  }
}
