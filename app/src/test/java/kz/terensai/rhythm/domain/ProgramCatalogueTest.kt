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

    @Test fun stableUniqueCatalogueKeys() {
        val keys = ProgramCatalogue.programmes.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        assertEquals(ProgramCatalogue.programmes.first(), ProgramCatalogue.byKey("tanysu"))
    }
}
