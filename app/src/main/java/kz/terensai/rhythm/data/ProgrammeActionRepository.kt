package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.ProgrammeActionRow
import kz.terensai.rhythm.data.room.EventRow
import kz.terensai.rhythm.data.room.ProgramSnapshotCodec
import kz.terensai.rhythm.domain.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class ProgrammeAction(
    val id:String,
    val kind:String,
    val description:String,
    val startedAtMs:Long,
    val finishedAtMs:Long?,
    val effectiveDurationMs:Long,
    val active:Boolean,
    val revisionCount:Int
)
data class ProgrammeActionState(
    val items:List<ProgrammeAction>,
    val extraHours:Int,
    val extensionMs:Long
)

/** Independent event stream, never a stage or a counted personal procedure. */
class ProgrammeActionRepository(
    private val db:ParitmDatabase,
    private val clock:()->Long={System.currentTimeMillis()}
){
    private val dao=db.programmeActions()
    companion object{
        val waitingKinds=setOf("GUEST_WAIT","TECH_WAIT","OTHER_WAIT")
        const val HOUR_MS=3_600_000L
    }
    private fun project(rows:List<ProgrammeActionRow>,now:Long):ProgrammeActionState {
        val events=rows.groupBy{it.entityId}
        val actions=events.mapNotNull{(id,history)->
            val begin=history.firstOrNull{it.action=="START"||it.action=="ADD_HOUR"} ?:return@mapNotNull null
            val end=history.firstOrNull{it.action=="STOP"}
            val lastCorrection=history.lastOrNull{it.action=="CORRECT"}
            val duration=when{
                begin.action=="ADD_HOUR"->HOUR_MS
                lastCorrection!=null->lastCorrection.durationOverrideMs?:0L
                end!=null->(end.atMs-begin.atMs).coerceAtLeast(0L)
                else->(now-begin.atMs).coerceAtLeast(0L)
            }
            ProgrammeAction(id,begin.kind,
                history.lastOrNull{it.note.isNotBlank()}?.note ?: begin.note,
                begin.atMs,end?.atMs,duration,
                begin.action!="ADD_HOUR" && end==null,
                history.size)
        }.sortedWith(compareBy<ProgrammeAction>{it.startedAtMs}.thenBy{it.id})
        val hours=actions.count{it.kind=="EXTRA_HOUR"}
        return ProgrammeActionState(actions,hours,hours*HOUR_MS.toLong())
    }
    suspend fun view(sessionId:String):ProgrammeActionState=withContext(Dispatchers.IO){
        project(dao.forSession(sessionId),clock())
    }

    suspend fun begin(
        sessionId:String,kind:String,comment:String="",
        pauseStageId:String?=null,
        requestId:String=UUID.randomUUID().toString()
    ):ProgrammeActionState=withContext(Dispatchers.IO){
        require(kind in waitingKinds && requestId.isNotBlank())
        require(comment.length<=300)
        db.withTransaction{
            val session=db.records().session(sessionId)?:error("Программа не найдена")
            check(session.finishedAtMs==null && session.legacyJson==null){"Программа уже завершена"}
            val current=dao.forSession(sessionId)
            if(current.any{it.id==requestId})return@withTransaction project(current,clock())
            check(project(current,clock()).items.none{it.active}){
                "Сначала заверши предыдущую задержку"
            }
            val stageEvents=db.records().events(sessionId)
            val at=maxOf(clock(),session.startedAtMs,
                current.maxOfOrNull{it.recordedAtMs}?:0L,
                stageEvents.maxOfOrNull{it.atMs}?:0L)
            if(pauseStageId!=null){
                val plan=ProgramSnapshotCodec.decode(session.snapshotJson)
                val stages=TimelineEngine(Clock{at}).project(
                    SessionPlan(session.id,plan,session.startedAtMs),
                    stageEvents.map{SessionEvent(it.id,it.stageId,EventKind.valueOf(it.kind),it.atMs)}
                ).stages
                val stage=stages.firstOrNull{it.plan.id==pauseStageId}
                    ?:error("Этап для паузы не найден")
                check(stage.status==StageStatus.RUNNING){"Этап уже не идёт — обнови экран"}
                check(db.records().appendEvent(EventRow(UUID.randomUUID().toString(),
                    sessionId,pauseStageId,EventKind.PAUSE.name,at))!=-1L)
            }
            check(dao.append(ProgrammeActionRow(requestId,requestId,sessionId,
                kind,"START",at,at,note=comment.trim()))!=-1L)
            check(db.records().touchSession(sessionId,at)==1)
            project(dao.forSession(sessionId),at)
        }
    }
    suspend fun finish(
        sessionId:String,entityId:String,
        requestId:String=UUID.randomUUID().toString()
    ):ProgrammeActionState=withContext(Dispatchers.IO){
        db.withTransaction{
            val row=db.records().session(sessionId)?:error("Программа не найдена")
            check(row.finishedAtMs==null){"Программа завершена"}
            val old=dao.forSession(sessionId)
            if(old.any{it.id==requestId})return@withTransaction project(old,clock())
            val item=project(old,clock()).items.firstOrNull{it.id==entityId}
                ?:error("Событие не найдено")
            check(item.active){"Событие уже остановлено"}
            val stamp=maxOf(clock(),item.startedAtMs)
            check(dao.append(ProgrammeActionRow(requestId,entityId,sessionId,
                item.kind,"STOP",stamp,stamp))!=-1L)
            check(db.records().touchSession(sessionId,stamp)==1)
            project(dao.forSession(sessionId),stamp)
        }
    }
    suspend fun correctMinutes(
        sessionId:String,entityId:String,minutes:Int,reason:String,
        requestId:String=UUID.randomUUID().toString()
    ):ProgrammeActionState=withContext(Dispatchers.IO){
        require(minutes in 0..1440)
        require(reason.trim().length in 5..300){"Укажи причину изменения (5–300 символов)"}
        db.withTransaction{
            val old=dao.forSession(sessionId)
            if(old.any{it.id==requestId})return@withTransaction project(old,clock())
            val item=project(old,clock()).items.firstOrNull{it.id==entityId}
                ?:error("Событие не найдено")
            check(!item.active && item.kind in waitingKinds){"Сначала заверши задержку"}
            val session=db.records().session(sessionId)?:error("Программа не найдена")
            check(session.finishedAtMs==null){"Закрытую программу не меняем без отдельного аудита"}
            val at=maxOf(clock(),old.maxOfOrNull{it.recordedAtMs}?:0L)
            check(dao.append(ProgrammeActionRow(requestId,entityId,sessionId,
                item.kind,"CORRECT",at,at,minutes*60_000L,reason.trim()))!=-1L)
            check(db.records().touchSession(sessionId,at)==1)
            project(dao.forSession(sessionId),at)
        }
    }
    suspend fun addHour(
        sessionId:String,requestId:String=UUID.randomUUID().toString()
    ):ProgrammeActionState=withContext(Dispatchers.IO){
        db.withTransaction{
            val session=db.records().session(sessionId)?:error("Программа не найдена")
            check(session.finishedAtMs==null){"Программа уже завершена"}
            val old=dao.forSession(sessionId)
            if(old.any{it.id==requestId})return@withTransaction project(old,clock())
            check(project(old,clock()).extraHours<12){"Слишком много продлений"}
            val at=maxOf(clock(),session.startedAtMs)
            check(dao.append(ProgrammeActionRow(requestId,requestId,sessionId,
                "EXTRA_HOUR","ADD_HOUR",at,at,HOUR_MS.toLong(),"+1 час"))!=-1L)
            check(db.records().touchSession(sessionId,at)==1)
            project(dao.forSession(sessionId),at)
        }
    }
}
