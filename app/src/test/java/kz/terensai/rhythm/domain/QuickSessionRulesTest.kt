package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class QuickSessionRulesTest {
    @Test fun minuteButtonsModifyOnlyRequestedEntry() {
        val source=listOf("15","12","35","12","35","12")
        val minus=QuickMinuteRules.adjust(source,1,-1)!!
        assertEquals("11",minus[1])
        assertEquals("15",minus[0])
        assertEquals("12",source[1])
        assertEquals("17",QuickMinuteRules.adjust(source,1,5)!![1])
    }
    @Test fun buttonsCannotRemoveFinalMinuteOrExceedTotalPlan() {
        val source=listOf("1","35","35","35","35","35")
        assertNull(QuickMinuteRules.adjust(source,0,-1))
        assertNull(QuickMinuteRules.adjust(source,0,-5))
        assertNull(QuickMinuteRules.adjust(source,0,5))
    }
    @Test fun malformedKeyboardInputMustBeFixedBeforeQuickAdjustOrLaunch() {
        assertNull(QuickMinuteRules.adjust(listOf("15","","35"),0,1))
        assertNull(QuickMinuteRules.adjust(listOf("-1","12"),1,1))
    }
    @Test fun scheduleStartDeviationIsNotLocalOverrun() {
        val early=ScheduleOffsets.classify(-26*60_000L-38_000L)
        assertEquals(ScheduleOffsetKind.EARLY,early.kind)
        assertEquals(1_598_000L,early.absoluteMillis)
        val late=ScheduleOffsets.classify(6*60_000L)
        assertEquals(ScheduleOffsetKind.LATE,late.kind)
        assertEquals(360_000L,late.absoluteMillis)
        assertEquals(ScheduleOffsetKind.ON_TIME,ScheduleOffsets.classify(0L).kind)
    }
    @Test fun guestAndMasterRouteDraftsKeepIndependentStageIdentity() {
        val guest=RouteChangeDraft("s","original-180","steam-extra-2",Track.GUEST,
            WorkStageType.STEAM,15*60_000L)
        val master=RouteChangeDraft("s","original-180","cleanup-1",Track.MASTER,
            WorkStageType.CLEANUP,5*60_000L)
        assertEquals("original-180",guest.originalPlanId)
        assertEquals(Track.MASTER,master.track)
        assertNotEquals(guest.stageInstanceId,master.stageInstanceId)
    }
}
