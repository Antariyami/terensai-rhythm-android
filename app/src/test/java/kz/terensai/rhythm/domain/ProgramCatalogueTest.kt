package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class ProgramCatalogueTest {
    @Test fun knownDurationAndCountsMatchPublishedProgrammes() {
        val programmes = ProgramCatalogue.programmes.associateBy { it.key }
        assertEquals(180, programmes.getValue("tanysu").durationMinutes)
        assertEquals(240, programmes.getValue("light").durationMinutes)
        assertEquals(240, programmes.getValue("grand").durationMinutes)
        assertEquals(300, programmes.getValue("premium").durationMinutes)
        assertEquals(300, programmes.getValue("supreme").durationMinutes)
    }

    @Test fun noUnknownProgrammePretendsToBeReady() {
        assertEquals(listOf("tanysu"),
            ProgramCatalogue.programmes.filter { it.status == TemplateStatus.PILOT }.map { it.key })
        assertTrue(ProgramCatalogue.programmes.filter {
            it.status == TemplateStatus.UNVERIFIED
        }.all { it.durationMinutes == null })
    }

    @Test fun russianDurationLabelsAreGrammatical() {
        assertEquals("3 часа", formatDuration(180))
        assertEquals("4 часа", formatDuration(240))
        assertEquals("5 часов", formatDuration(300))
        assertEquals("1 час", formatDuration(60))
        assertEquals("1 ч 15 мин", formatDuration(75))
    }

    @Test fun stableUniqueCatalogueKeys() {
        val keys = ProgramCatalogue.programmes.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(ProgramCatalogue.programmes.first(), ProgramCatalogue.byKey("tanysu"))
    }
}
