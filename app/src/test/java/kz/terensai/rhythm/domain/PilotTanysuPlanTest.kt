package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class PilotTanysuPlanTest {
    @Test fun defaultPilotHasExactlyThreeHoursAndThreeSteamEntries() {
        val durations=PilotTanysuPlan.defaults.map { it.minutes }
        val route=PilotTanysuPlan.evaluate(durations)
        assertTrue(route.valid)
        val plan=PilotTanysuPlan.toPlan(durations)
        assertEquals(180*60_000L,plan.plannedDurationMs)
        assertEquals(180*60_000L,
            plan.stages.sumOf { it.plannedDurationMs })
        assertEquals(3,plan.stages.count { it.id.startsWith("steam") })
        assertEquals("finish",plan.stages.last().id)
    }

    @Test fun editedRouteUpdatesOffsetsAndBalancingStage() {
        val edited=PilotTanysuPlan.defaults.map { it.minutes }.toMutableList()
        edited[0]=25
        val plan=PilotTanysuPlan.toPlan(edited)
        assertEquals(25*60_000L,plan.stages[1].startOffsetMs)
        assertEquals(180*60_000L,
            plan.stages.last().startOffsetMs+plan.stages.last().plannedDurationMs)
    }

    @Test fun invalidOrNegativeInputsNeverCreateExecutableTemplate() {
        assertFalse(PilotTanysuPlan.evaluate(listOf(0,1,1,1,1,1)).valid)
        assertFalse(PilotTanysuPlan.evaluate(listOf(50,50,50,50,50,50)).valid)
        assertFalse(PilotTanysuPlan.evaluate(listOf(null,12,12,12,12,12)).valid)
        try {
            PilotTanysuPlan.toPlan(listOf(50,50,50,50,50,50))
            fail("Should reject overbooked route")
        } catch (_: IllegalArgumentException) { }
    }
}
