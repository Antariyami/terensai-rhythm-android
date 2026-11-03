package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class StageFactRulesTest {
    private val stage=StagePlan("s","Встреча",Track.GUEST,0,1_800_000L)
    private val started=1_000_000L
    @Test fun correctedCompletedStageHasNewRealityButOriginalPlan(){
        val progress=SessionProgress(2_000_000L,0L,2_800_000L,listOf(
            StageProgress(stage,StageStatus.COMPLETED,1_100_000L,2_900_000L,
                1_800_000L,100_000L)))
        val out=StageFactRules.overlay(progress,started,3_000_000L,listOf(
            EffectiveStageFact("s",1_200_000L,2_880_000L)))
        val changed=out.stages.single()
        assertEquals(1_680_000L,changed.elapsedMs)
        assertEquals(1_800_000L,changed.plan.plannedDurationMs)
        assertEquals(1_200_000L,changed.actualStartedAtMs)
        assertEquals(2_880_000L,changed.actualFinishedAtMs)
        assertEquals(200_000L,changed.startDeltaMs)
    }
    @Test fun activeCorrectionRecomputesWithoutInventingCompletion(){
        val progress=SessionProgress(300_000L,1_500_000L,2_800_000L,listOf(
            StageProgress(stage,StageStatus.RUNNING,1_100_000L,null,200_000L,100_000L)))
        val result=StageFactRules.overlay(progress,started,1_300_000L,listOf(
            EffectiveStageFact("s",1_050_000L,null))).stages.single()
        assertEquals(StageStatus.RUNNING,result.status)
        assertNull(result.actualFinishedAtMs)
        assertEquals(250_000L,result.elapsedMs)
    }
}
