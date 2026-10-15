package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class StageReturnRulesTest {
    private val plans=listOf(
        StagePlan("a","Первый",Track.GUEST,0,1000),
        StagePlan("b","Второй",Track.GUEST,1000,1000)
    )
    private fun state(one:StageStatus,two:StageStatus)=SessionProgress(
        0,0,2000,plans.zip(listOf(one,two)).map{(p,s)->
            StageProgress(p,s,null,null,0,null)
        }
    )
    @Test fun previousFinishedStageCanReturn(){
        assertEquals("a",StageReturnRules.target(state(
            StageStatus.COMPLETED,StageStatus.RUNNING))?.plan?.id)
    }
    @Test fun finishedStagesPickMostRecentUntilSessionClosed(){
        assertEquals("b",StageReturnRules.target(state(
            StageStatus.COMPLETED,StageStatus.SKIPPED))?.plan?.id)
    }
    @Test fun nothingToReturnAtFirstStage(){
        assertNull(StageReturnRules.target(state(StageStatus.RUNNING,StageStatus.NOT_STARTED)))
    }
}
