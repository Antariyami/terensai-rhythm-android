package kz.terensai.rhythm.domain

/** Separate effective recorded reality from immutable plan and original events. */
data class EffectiveStageFact(
    val stageId:String,
    val startMs:Long,
    val endMs:Long?
)
object StageFactRules {
    fun overlay(progress:SessionProgress,sessionStartedMs:Long,
        originalNowMs:Long,facts:List<EffectiveStageFact>):SessionProgress {
        val latest=facts.associateBy{it.stageId}
        if(latest.isEmpty())return progress
        val stages=progress.stages.map{stage->
            val fact=latest[stage.plan.id]?:return@map stage
            val originalStart=stage.actualStartedAtMs
            val originalEnd=stage.actualFinishedAtMs
            // Keep known PAUSE/RESUME time separate from the outer wall-clock
            // interval. If a stage had no original run, no pauses are invented.
            val originalWall=if(originalStart!=null)
                ((originalEnd?:originalNowMs)-originalStart).coerceAtLeast(0L)
                else 0L
            val paused=(originalWall-stage.elapsedMs).coerceAtLeast(0L)
            val outer=((fact.endMs?:originalNowMs)-fact.startMs).coerceAtLeast(0L)
            val elapsed=(outer-paused).coerceAtLeast(0L)
            val status=if(fact.endMs!=null)StageStatus.COMPLETED else stage.status
            stage.copy(status=status,actualStartedAtMs=fact.startMs,
                actualFinishedAtMs=fact.endMs,elapsedMs=elapsed,
                startDeltaMs=fact.startMs-(sessionStartedMs+stage.plan.startOffsetMs))
        }
        return progress.copy(stages=stages)
    }
}
