package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.terensai.rhythm.data.room.EventRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.ProgramSnapshotCodec
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.data.room.SessionRow
import kz.terensai.rhythm.data.room.StageFactCorrectionRow
import kz.terensai.rhythm.data.room.SessionClosureRow
import kz.terensai.rhythm.domain.EffectiveStageFact
import kz.terensai.rhythm.domain.StageFactRules
import kz.terensai.rhythm.domain.Clock
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.SessionEvent
import kz.terensai.rhythm.domain.SessionPlan
import kz.terensai.rhythm.domain.SessionProgress
import kz.terensai.rhythm.domain.StageReturnRules
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.domain.SessionTransitionRules
import kz.terensai.rhythm.domain.TimelineEngine
import java.util.UUID

data class SavedSession(
    val id: String,
    val programmeTitle: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val progress: SessionProgress,
    val eventCount: Int,
    val metadata: SessionMetadata = SessionMetadata(),
    val originClientId: String = "legacy-local",
    val localRevision: Long = 0L,
    val correctedStageIds:Set<String> = emptySet(),
    val closureOutcome:String? = null,
    val closureReason:String? = null
)

/**
 * Single source of truth for B2.1 session time. Transactions serialize start,
 * transitions and finish. The legacy timer continues to use SharedPreferences
 * until the deliberate migration in B2.2.
 */
class SessionRepository(
    private val database: ParitmDatabase,
    private val clock: Clock = Clock { System.currentTimeMillis() },
    private val originClientId: String = "local-qa"
) {
    private val dao = database.records()

    companion object { const val MAX_ACTIVE_SESSIONS = 5 }

    /**
     * [requestId] is stable for a single user's Start tap, so retries restore that
     * SAME session, but two deliberate Start actions create separate programmes.
     * Room transactions serialize count/check/insert to enforce the five-session cap.
     */
    suspend fun start(
        plan: ProgramPlan,
        metadata: SessionMetadata = SessionMetadata(),
        requestId: String = UUID.randomUUID().toString()
    ): SavedSession = withContext(Dispatchers.IO) {
        require(requestId.isNotBlank())
        database.withTransaction {
            val existing = dao.session(requestId)
            if (existing != null) {
                check(existing.legacyJson == null) { "This session ID belongs to a legacy record" }
                return@withTransaction read(existing)
            }
            check(dao.activeSessionCount() < MAX_ACTIVE_SESSIONS) {
                "Достигнут предел — 5 одновременно активных программ"
            }
            val now = clock.epochMillis().coerceAtLeast(1L)
            val row = SessionRow(
                id = requestId,
                templateId = plan.id,
                templateTitle = plan.title,
                startedAtMs = now,
                plannedDurationMs = plan.plannedDurationMs,
                snapshotJson = ProgramSnapshotCodec.encode(plan.snapshot(), metadata),
                originClientId = originClientId,
                localRevision = 0,
                modifiedAtMs = now
            )
            dao.startSession(row)
            read(row)
        }
    }

    /** Historical (including completed) B2 sessions for the programme-stage journal. */
    suspend fun allSessions():List<SavedSession> = withContext(Dispatchers.IO) {
        dao.sessions().filter{it.legacyJson==null}.map{read(it)}
    }

    suspend fun activeSessions(): List<SavedSession> = withContext(Dispatchers.IO) {
        dao.activeSessions().map { read(it) }
    }

    suspend fun active(): SavedSession? = withContext(Dispatchers.IO) {
        dao.activeSession()?.let { read(it) }
    }

    suspend fun readById(id: String): SavedSession? = withContext(Dispatchers.IO) {
        dao.session(id)?.takeUnless { it.legacyJson != null }?.let { read(it) }
    }

    suspend fun perform(
        sessionId: String,
        stageId: String,
        command: EventKind,
        commandId: String = UUID.randomUUID().toString()
    ): SavedSession = withContext(Dispatchers.IO) {
        require(commandId.isNotBlank())
        require(command != EventKind.REOPEN) { "Для возврата используй reopenPrevious" }
        database.withTransaction {
            val row = dao.session(sessionId) ?: error("Session not found")
            check(row.finishedAtMs == null && row.legacyJson == null) { "Session is already closed" }
            val events = dao.events(sessionId)
            if (events.any { it.id == commandId }) return@withTransaction read(row)
            val state = read(row, events)
            val stage = state.progress.stages.firstOrNull { it.plan.id == stageId }
                ?: error("Unknown stage")
            check(SessionTransitionRules.allowed(stage.status, command)) {
                "Command $command is not allowed for stage status"
            }
            val now = maxOf(
                clock.epochMillis(), row.startedAtMs,
                (events.maxOfOrNull { it.atMs } ?: 0L) + 1L
            )
            val inserted = dao.appendEvent(
                EventRow(commandId, sessionId, stageId, command.name, now)
            )
            check(inserted != -1L) { "An event ID is already in use" }
            check(dao.touchSession(sessionId, now) == 1)
            read(dao.session(sessionId)!!)
        }
    }

    /**
     * Append-only correction of an accidental stage COMPLETE/SKIP.
     * A stage already attributed in S13 is temporarily blocked to prevent
     * WorkEntry/report divergence; a future change will resync it with audit.
     */
    suspend fun reopenPrevious(
        sessionId: String,
        expectedStageId: String,
        commandId: String = UUID.randomUUID().toString()
    ): SavedSession = withContext(Dispatchers.IO) {
        require(commandId.isNotBlank() && expectedStageId.isNotBlank())
        database.withTransaction {
            val row = dao.session(sessionId) ?: error("Программа не найдена")
            check(row.finishedAtMs == null && row.legacyJson == null) {
                "Программа уже завершена"
            }
            val events = dao.events(sessionId)
            if (events.any { it.id == commandId }) return@withTransaction read(row, events)
            val state = read(row, events)
            val previous = StageReturnRules.target(state.progress)
                ?: error("Нет завершённого предыдущего этапа")
            check(previous.plan.id == expectedStageId) {
                "Этап изменился. Обнови экран перед возвратом."
            }
            check(SessionTransitionRules.allowed(previous.status, EventKind.REOPEN))
            check(database.work().attributedStageCount(sessionId, expectedStageId) == 0) {
                "Этап уже учтён в личном журнале. Возврат заблокирован для защиты отчёта."
            }
            val at = maxOf(clock.epochMillis(), row.startedAtMs,
                (events.maxOfOrNull { it.atMs } ?: 0L) + 1L)
            state.progress.stages.filter {
                it.plan.id != expectedStageId &&
                    it.plan.track == previous.plan.track &&
                    it.status == StageStatus.RUNNING
            }.forEach { other ->
                check(dao.appendEvent(EventRow(
                    UUID.randomUUID().toString(), sessionId, other.plan.id,
                    EventKind.PAUSE.name, at
                )) != -1L)
            }
            check(dao.appendEvent(EventRow(
                commandId, sessionId, expectedStageId, EventKind.REOPEN.name, at
            )) != -1L)
            check(dao.touchSession(sessionId, at) == 1)
            read(dao.session(sessionId)!!)
        }
    }


    suspend fun corrections(sessionId:String):List<StageFactCorrectionRow> =
        withContext(Dispatchers.IO){dao.stageCorrections(sessionId)}

    /** Append-only correction of stage reality; original timer events are untouched. */
    suspend fun correctStageFact(sessionId:String,stageId:String,
        effectiveStartMs:Long,effectiveEndMs:Long?,reason:String,
        actor:String="Мастер",requestId:String=UUID.randomUUID().toString()
    ):SavedSession=withContext(Dispatchers.IO){
        require(requestId.isNotBlank() && reason.trim().length in 3..500)
        database.withTransaction{
            val row=dao.session(sessionId)?:error("Программа не найдена")
            check(row.legacyJson==null){"Нельзя редактировать старую архивную запись"}
            val duplicate=dao.stageCorrection(requestId)
            if(duplicate!=null){
                check(duplicate.sessionId==sessionId&&duplicate.stageId==stageId){
                    "Идентификатор исправления уже использован"
                }
                return@withTransaction read(row)
            }
            val now=maxOf(clock.epochMillis(),row.startedAtMs)
            require(effectiveStartMs in 1..now){"Начало позже текущего времени или некорректно"}
            require(effectiveEndMs==null||(effectiveEndMs>effectiveStartMs &&
                effectiveEndMs<=now)){"Проверь дату и время окончания"}
            if(row.finishedAtMs!=null){
                check(effectiveEndMs!=null&&effectiveEndMs<=row.finishedAtMs){
                    "В завершённой программе факт не должен выходить за время закрытия"
                }
            }
            val state=read(row)
            val stage=state.progress.stages.firstOrNull{it.plan.id==stageId}
                ?:error("Этап не найден")
            check(stage.status!=StageStatus.SKIPPED){
                "Пропущенный этап сначала нужно восстановить отдельным действием"
            }
            check(effectiveEndMs!=null||stage.status in
                setOf(StageStatus.RUNNING,StageStatus.PAUSED)){
                "Для неактивного этапа укажи и начало, и конец"
            }
            // Previously attributed personal work must not be silently desynced.
            check(database.work().attributedStageCount(sessionId,stageId)==0){
                "Этап уже перенесён в личный журнал. Его время нужно исправлять вместе с WorkEntry."
            }
            val candidateEnd=effectiveEndMs?:now
            for(other in state.progress.stages){
                if(other.plan.id==stageId||other.plan.track!=stage.plan.track)continue
                val os=other.actualStartedAtMs?:continue
                val oe=other.actualFinishedAtMs?:now
                check(candidateEnd<=os||effectiveStartMs>=oe){
                    "Время пересекается с этапом «${other.plan.title}» на одной дорожке"
                }
            }
            val before=dao.events(sessionId)
            val at=maxOf(now,(before.maxOfOrNull{it.atMs}?:now)+1L)
            when(stage.status){
                StageStatus.NOT_STARTED->{
                    check(dao.appendEvent(kz.terensai.rhythm.data.room.EventRow(
                        UUID.randomUUID().toString(),sessionId,stageId,"START",at))!=-1L)
                    check(dao.appendEvent(kz.terensai.rhythm.data.room.EventRow(
                        UUID.randomUUID().toString(),sessionId,stageId,"COMPLETE",at+1))!=-1L)
                }
                StageStatus.RUNNING,StageStatus.PAUSED->if(effectiveEndMs!=null){
                    check(row.finishedAtMs==null)
                    check(dao.appendEvent(kz.terensai.rhythm.data.room.EventRow(
                        UUID.randomUUID().toString(),sessionId,stageId,"COMPLETE",at))!=-1L)
                }
                else->Unit
            }
            check(dao.addStageCorrection(StageFactCorrectionRow(
                requestId,sessionId,stageId,effectiveStartMs,effectiveEndMs,
                stage.actualStartedAtMs,stage.actualFinishedAtMs,
                now,actor.take(80),reason.trim()
            ))!=-1L)
            check(dao.touchSession(sessionId,now)==1)
            read(dao.session(sessionId)!!)
        }
    }

    /** Explicit protected close; never deletes stage events or WorkEntries. */
    suspend fun finishProtected(sessionId:String,outcome:String,
        reason:String=""):SavedSession=withContext(Dispatchers.IO){
        require(outcome in setOf("SAVED","INTERRUPTED"))
        require(outcome!="INTERRUPTED"||reason.trim().length>=3){
            "Укажи причину прерывания программы"
        }
        database.withTransaction{
            val row=dao.session(sessionId)?:error("Программа не найдена")
            check(row.legacyJson==null){"Архивную запись закрыть нельзя"}
            if(row.finishedAtMs!=null)return@withTransaction read(row)
            val at=maxOf(clock.epochMillis(),row.startedAtMs,
                (dao.events(sessionId).maxOfOrNull{it.atMs}?:0L)+1L)
            val activeWorks=database.work().forSession(sessionId).filter{work->
                val view=WorkRepository(database,originClientId).projectForJournal(
                    work,database.work().events(work.id),at)
                view.state in setOf("RUNNING","PAUSED")
            }
            check(activeWorks.isEmpty()){
                "Перед закрытием заверши или отметь текущие SPA-процедуры: ${activeWorks.size}"
            }
            val progress=read(row).progress
            progress.stages.filter{it.status!=StageStatus.COMPLETED&&
                it.status!=StageStatus.SKIPPED}.forEach{stage->
                check(dao.appendEvent(kz.terensai.rhythm.data.room.EventRow(
                    UUID.randomUUID().toString(),sessionId,stage.plan.id,
                    EventKind.SKIP.name,at))!=-1L)
            }
            check(dao.addClosure(SessionClosureRow(sessionId,outcome,reason.trim(),at))!=-1L)
            check(dao.finishSession(sessionId,at)==1)
            check(dao.touchSession(sessionId,at)==1)
            read(dao.session(sessionId)!!)
        }
    }

    suspend fun finish(sessionId: String): SavedSession = withContext(Dispatchers.IO) {
        database.withTransaction {
            val row = dao.session(sessionId) ?: error("Session not found")
            check(row.legacyJson == null) { "Cannot finish legacy archive record" }
            if (row.finishedAtMs == null) {
                val projected = read(row)
                check(projected.progress.stages.all {
                    it.status == kz.terensai.rhythm.domain.StageStatus.COMPLETED ||
                        it.status == kz.terensai.rhythm.domain.StageStatus.SKIPPED
                }) { "Finish or skip all stages before ending the session" }
                val latest = dao.events(sessionId).maxOfOrNull { it.atMs } ?: row.startedAtMs
                val ended = maxOf(clock.epochMillis(), latest, row.startedAtMs)
                check(dao.finishSession(sessionId, ended) == 1)
                check(dao.touchSession(sessionId, ended) == 1)
            }
            read(dao.session(sessionId)!!)
        }
    }

    private fun read(row: SessionRow, rows: List<EventRow> = dao.events(row.id)): SavedSession {
        val plan = ProgramSnapshotCodec.decode(row.snapshotJson)
        val events = rows.map { item ->
            SessionEvent(item.id, item.stageId, EventKind.valueOf(item.kind), item.atMs)
        }
        val now = maxOf(clock.epochMillis(), events.maxOfOrNull { it.atMs } ?: row.startedAtMs)
        val effectiveClock = Clock { row.finishedAtMs ?: now }
        val baseProgress = TimelineEngine(effectiveClock).project(
            SessionPlan(row.id, plan, row.startedAtMs), events
        )
        val facts=dao.stageCorrections(row.id)
        val progress=StageFactRules.overlay(baseProgress,row.startedAtMs,
            effectiveClock.epochMillis(),facts.map{
                EffectiveStageFact(it.stageId,it.effectiveStartedAtMs,it.effectiveFinishedAtMs)
            })
        val closure=dao.closure(row.id)
        return SavedSession(
            row.id, row.templateTitle, row.startedAtMs,
            row.finishedAtMs, progress, events.size, ProgramSnapshotCodec.metadata(row.snapshotJson),
            row.originClientId, row.localRevision,
            facts.map{it.stageId}.toSet(),closure?.outcome,closure?.reason
        )
    }
}
