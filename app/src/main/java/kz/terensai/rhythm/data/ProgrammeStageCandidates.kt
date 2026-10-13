package kz.terensai.rhythm.data

import kz.terensai.rhythm.domain.StageStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Stages are programme events, not proof of a particular master's participation. */
data class ReportableProgrammeStage(
    val sessionId:String,
    val stageId:String,
    val stageTitle:String,
    val programmeTitle:String,
    val suiteName:String?,
    val startedAtMs:Long,
    val finishedAtMs:Long?,
    val type:String
)

/** Only confirmed, actually started steamings may be attributed by the user. */
object ProgrammeStageCandidates {
    fun classify(title:String):String? {
        val t=title.lowercase()
        return when {
            "парен" !in t -> null
            "коллектив" in t -> "COLLECTIVE_STEAM"
            "индивидуал" in t -> "INDIVIDUAL_STEAM"
            else -> null
        }
    }
    fun of(
        sessions:List<SavedSession>,
        date:LocalDate,
        zone:ZoneId=ZoneId.systemDefault()
    ):List<ReportableProgrammeStage> = sessions.flatMap { session ->
        session.progress.stages.mapNotNull {stage ->
            val started=stage.actualStartedAtMs
            val type=classify(stage.plan.title)
            if(stage.status!=StageStatus.COMPLETED || started==null || type==null ||
                Instant.ofEpochMilli(started).atZone(zone).toLocalDate()!=date
            ) null
            else ReportableProgrammeStage(
                session.id,stage.plan.id,stage.plan.title,
                session.programmeTitle,session.metadata.suite,
                started,stage.actualFinishedAtMs,type
            )
        }
    }.sortedWith(compareBy<ReportableProgrammeStage>{it.startedAtMs}.thenBy{it.stageId})
}
