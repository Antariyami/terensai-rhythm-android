package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class StandardProgrammeRouteTest {
    @Test fun grandHasTwoFiftyMinuteBlocksIncludingInternalBreak() {
        val route=StandardProgrammeRoute.preview(StandardRouteDraft("grand",guests=8))
        assertTrue(route.problem,route.valid)
        val collective=route.steps.filter{it.id.startsWith("grand-collective")}
        assertEquals(2,collective.size)
        assertEquals(listOf(50,50),collective.map{it.minutes})
        assertTrue(collective.all{it.title.contains("5–7")})
        assertEquals(20,route.steps.first{it.id=="grand-break"}.minutes)
        assertEquals(240,route.steps.sumOf{it.minutes})
        assertEquals(80,route.availableMinutes)
    }

    @Test fun lightSixGuestsAtTwoConcurrentOverrunsFourHours() {
        val input=StandardRouteDraft("light",guests=6,simultaneousGuests=2)
        val route=StandardProgrammeRoute.preview(input)
        assertEquals(3,route.waves)
        assertFalse(route.valid)
        assertEquals(265,route.fixedMinutes)
        assertEquals(25,route.overrunMinutes)
        assertTrue(route.steps.any{it.id=="individual-3"})
        assertTrue(route.steps.any{it.id=="wave-rest-3"})
        try {StandardProgrammeRoute.toPlan(input);fail("Must not silently launch a 4-hour overrun")}
        catch (_:IllegalArgumentException){}
    }

    @Test fun changingParallelCapacityOrAgreedHoursReplansWithoutChangingCatalogue() {
        val biggerCapacity=StandardProgrammeRoute.preview(
            StandardRouteDraft("light",guests=6,simultaneousGuests=3))
        assertEquals(2,biggerCapacity.waves)
        assertTrue(biggerCapacity.problem,biggerCapacity.valid)
        val booked=StandardRouteDraft("light",guests=6,
            simultaneousGuests=2,bookedExtraHours=1)
        val plan=StandardProgrammeRoute.toPlan(booked)
        assertEquals(300*60_000L,plan.plannedDurationMs)
        assertEquals(300*60_000L,plan.stages.sumOf{it.plannedDurationMs})
        assertEquals("LIGHT · согласовано +1 ч",plan.title)
        assertEquals(240,ProgrammeCatalog.get("light")!!.durationMinutes)
    }

    @Test fun premiumParallelSpaIsNeverAutomaticallyMarkedCompleted() {
        val route=StandardProgrammeRoute.preview(StandardRouteDraft(
            "premium",guests=6,simultaneousGuests=2))
        assertTrue(route.problem,route.valid)
        assertEquals(3,route.waves)
        assertEquals(300,route.steps.sumOf{it.minutes})
        assertFalse(route.steps.any{it.title.contains("Массаж")})
        assertEquals(3,route.steps.count{it.id.startsWith("individual-")})
    }

    @Test fun editableTimesAndStageIdsAreStableAcrossReplanning() {
        val original=StandardProgrammeRoute.preview(StandardRouteDraft("light"))
        val changed=StandardProgrammeRoute.preview(StandardRouteDraft(
            "light",minuteOverrides=mapOf("welcome" to 18,"individual-1" to 45)))
        assertEquals(original.steps.map{it.id},changed.steps.map{it.id})
        assertEquals(18,changed.steps.first{it.id=="welcome"}.minutes)
        assertEquals(45,changed.steps.first{it.id=="individual-1"}.minutes)
        assertEquals(original.availableMinutes+2,changed.availableMinutes)
    }

    @Test fun supremeStillHasNoExecutableRoute() {
        val result=StandardProgrammeRoute.preview(StandardRouteDraft("supreme"))
        assertFalse(result.valid)
        assertTrue(result.steps.isEmpty())
    }
}
