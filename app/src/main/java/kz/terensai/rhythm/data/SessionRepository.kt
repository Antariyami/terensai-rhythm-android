package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.terensai.rhythm.data.room.EventRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.ProgramSnapshotCodec
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.data.room.SessionRow
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
    val localRevision: Long = 0L
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
        val progress = TimelineEngine(effectiveClock).project(
            SessionPlan(row.id, plan, row.startedAtMs), events
        )
        return SavedSession(
            row.id, row.templateTitle, row.startedAtMs,
            row.finishedAtMs, progress, events.size, ProgramSnapshotCodec.metadata(row.snapshotJson),
            row.originClientId, row.localRevision
        )
    }
}
