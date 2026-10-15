package kz.terensai.rhythm.domain

/** Closest earlier finished stage eligible for correction before session finish. */
object StageReturnRules {
    fun target(progress:SessionProgress):StageProgress? {
        val all=progress.stages
        val current=all.indexOfFirst{it.status==StageStatus.RUNNING||it.status==StageStatus.PAUSED}
            .takeIf{it>=0}
            ?: all.indexOfFirst{it.status==StageStatus.NOT_STARTED}.takeIf{it>=0}
            ?: all.size
        return all.take(current).lastOrNull{
            it.status==StageStatus.COMPLETED||it.status==StageStatus.SKIPPED
        }
    }
}
