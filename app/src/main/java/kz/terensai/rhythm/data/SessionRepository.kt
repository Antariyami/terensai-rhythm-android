package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kz.terensai.rhythm.data.room.EventRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.ProgramSnapshotCodec
import kz.terensai.rhythm.data.room.SessionRow
import kz.terensai.rhythm.domain.Clock
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.SessionEvent
import kz.terensai.rhythm.domain.SessionPlan
import kz.terensai.rhythm.domain.SessionProgress
import kz.terensai.rhythm.domain.SessionTransitionRules
import kz.terensai.rhythm.domain.TimelineEngine
import java.util.UUID

data class SavedSession(
    val id: String,
    val programmeTitle: String,
    val startedAtMs: Long,
    val finishedAtMs: Long?,
    val progress: SessionProgress,
    val eventCount: Int
)

/**
 * Single source of truth for B2.1 session time. Transactions serialize start,
 * transitions and finish. The legacy timer continues to use SharedPreferences
 * until the deliberate migration in B2.2.
 */
class SessionRepository(
    private val database: ParitmDatabase,
    private val clock: Clock = Clock { System.currentTimeMillis() }
) {
    private val dao = database.records()

    suspend fun start(plan: ProgramPlan): SavedSession = withContext(Dispatchers.IO) {
        database.withTransaction {
            val existing = dao.activeSession()
            if (existing != null) return@withTransaction read(existing)
            val now = clock.epochMillis().coerceAtLeast(1L)
            val row = SessionRow(
                id = UUID.randomUUID().toString(),
                templateId = plan.id,
                templateTitle = plan.title,
                startedAtMs = now,
                plannedDurationMs = plan.plannedDurationMs,
                snapshotJson = ProgramSnapshotCodec.encode(plan.snapshot())
            )
            dao.startSession(row)
            read(row)
        }
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
            read(row)
        }
    }

    suspend fun finish(sessionId: String): SavedSession = withContext(Dispatchers.IO) {
        database.withTransaction {
            val row = dao.session(sessionId) ?: error("Session not found")
            check(row.legacyJson == null) { "Cannot finish legacy archive record" }
            if (row.finishedAtMs == null) {
                val latest = dao.events(sessionId).maxOfOrNull { it.atMs } ?: row.startedAtMs
                val ended = maxOf(clock.epochMillis(), latest, row.startedAtMs)
                check(dao.finishSession(sessionId, ended) == 1)
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
            row.finishedAtMs, progress, events.size
        )
    }
}
