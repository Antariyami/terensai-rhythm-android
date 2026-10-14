package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class CustomProgrammePlanTest {
    @Test fun usesOnlyRealUserEnteredStagesAndCorrectOffsets() {
        val d=CustomProgrammeDraft("Семейная программа",listOf(
            CustomStageInput("Встреча гостей",10,Track.GUEST),
            CustomStageInput("Подготовка парной",15,Track.MASTER),
            CustomStageInput("Индивидуальное парение",20,Track.GUEST)
        ))
        assertNull(CustomProgrammePlan.validate(d))
        val p=CustomProgrammePlan.toPlan(d)
        assertEquals("custom-v1",p.id)
        assertEquals("Семейная программа",p.title)
        assertEquals(45*60000L,p.plannedDurationMs)
        assertEquals(listOf(0L,10*60000L,25*60000L),p.stages.map{it.startOffsetMs})
        assertEquals(listOf("custom-stage-1","custom-stage-2","custom-stage-3"),
            p.stages.map{it.id})
        assertEquals(listOf(Track.GUEST,Track.MASTER,Track.GUEST),p.stages.map{it.track})
    }
    @Test fun refuseBlankNamesInvalidDurationsAndTooManyStages(){
        assertNotNull(CustomProgrammePlan.validate(
            CustomProgrammeDraft("",listOf(CustomStageInput("Парение",15)))
        ))
        assertNotNull(CustomProgrammePlan.validate(
            CustomProgrammeDraft("Программа",listOf(CustomStageInput("X",15)))
        ))
        assertNotNull(CustomProgrammePlan.validate(
            CustomProgrammeDraft("Программа",listOf(CustomStageInput("Парение",0)))
        ))
        assertNotNull(CustomProgrammePlan.validate(
            CustomProgrammeDraft("Программа",List(13){CustomStageInput("Процедура",10)})
        ))
        assertNotNull(CustomProgrammePlan.validate(
            CustomProgrammeDraft("Программа",List(4){CustomStageInput("Процедура",240)})
        ))
    }
}
