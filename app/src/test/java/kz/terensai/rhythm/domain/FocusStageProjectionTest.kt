package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class FocusStageProjectionTest {
    private val plan=StagePlan(
        id="steam",title="Парение",track=Track.MASTER,
        startOffsetMs=10L*60000L,plannedDurationMs=45L*60000L
    )
    private fun stage(status:StageStatus,elapsed:Long,delta:Long?=null)=StageProgress(
        plan=plan,status=status,actualStartedAtMs=null,actualFinishedAtMs=null,
        elapsedMs=elapsed,startDeltaMs=delta
    )
    private fun session(stage:StageProgress,elapsed:Long)=SessionProgress(
        elapsedWallClockMs=elapsed,remainingPlannedMs=(180L*60000L-elapsed).coerceAtLeast(0L),
        plannedEndMs=180L*60000L,stages=listOf(stage)
    )

    @Test fun runningStageShowsRemainingAndOverallWallTimeIndependently(){
        val current=stage(StageStatus.RUNNING,20L*60000L,120000L)
        val model=FocusStageProjection.of(current,session(current,90L*60000L))
        assertEquals(25L*60000L,model.remainingMs)
        assertEquals(0L,model.overtimeMs)
        assertEquals(20L*60000L,model.elapsedMs)
        assertEquals(90L*60000L,model.overallElapsedMs)
        assertEquals(90L*60000L,model.overallRemainingMs)
        assertEquals(120000L,model.startDeltaMs)
        assertEquals(20f/45f,model.stageProgress,0.0001f)
    }

    @Test fun pausedStageRetainsMeasuredTimeEvenWhenProgrammeClockAdvances(){
        val paused=stage(StageStatus.PAUSED,15L*60000L)
        val first=FocusStageProjection.of(paused,session(paused,60L*60000L))
        val later=FocusStageProjection.of(paused,session(paused,75L*60000L))
        assertEquals(first.elapsedMs,later.elapsedMs)
        assertEquals(first.remainingMs,later.remainingMs)
        assertEquals(15L*60000L,later.overallElapsedMs-first.overallElapsedMs)
    }

    @Test fun overtimeCountsUpAndVisualProgressClamps(){
        val over=stage(StageStatus.RUNNING,50L*60000L)
        val model=FocusStageProjection.of(over,session(over,181L*60000L))
        assertTrue(model.overtime)
        assertEquals(5L*60000L,model.overtimeMs)
        assertEquals(0L,model.remainingMs)
        assertEquals(0L,model.overallRemainingMs)
        assertEquals(1f,model.stageProgress)
    }
}
