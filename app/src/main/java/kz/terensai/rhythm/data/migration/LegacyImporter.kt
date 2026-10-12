package kz.terensai.rhythm.data.migration

import android.content.Context
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionRow
import kz.terensai.rhythm.domain.LegacySession
import org.json.JSONObject

/**
 * One-way, non-destructive bridge prepared for the future 0.2 screen switch.
 * IMPORTANT: Don't invoke on every v0.1 screen launch: that UI still writes SharedPreferences
 * and importing early would archive an unfinished snapshot.
 * Call once after switching the active UI to Room-backed repositories.
 */
object LegacyImporter {
    fun loadLegacy(context: Context): LegacySession {
        val p = context.getSharedPreferences("rhythm_state_v1", Context.MODE_PRIVATE)
        return LegacySession(
            startedAtMs = p.getLong("program", 0),
            stageStartedAtMs = p.getLong("stageStart", 0),
            accumulatedStageMs = p.getLong("stageElapsed", 0),
            stageIndex = p.getInt("stage", 0),
            paused = p.getBoolean("paused", false),
            rawJournal = p.getString("history", "") ?: ""
        )
    }

    /** Must be called on a background thread (Room forbids main-thread access). */
    fun importIfPresent(context: Context, database: ParitmDatabase): Boolean {
        val legacy = loadLegacy(context)
        if (!legacy.hasData()) return false
        val payload = JSONObject()
            .put("source", "0.1")
            .put("program", legacy.startedAtMs)
            .put("stageStart", legacy.stageStartedAtMs)
            .put("stageElapsed", legacy.accumulatedStageMs)
            .put("stage", legacy.stageIndex)
            .put("paused", legacy.paused)
            .put("history", legacy.rawJournal)
            .toString()
        // Insert IGNORE makes retry safe; the old preferences are never erased here.
        val record = SessionRow(
            id = legacy.stableId(),
            templateId = "legacy-tanysu",
            templateTitle = "Танысу · импорт из 0.1",
            startedAtMs = legacy.startedAtMs.coerceAtLeast(0),
            plannedDurationMs = 180L * 60 * 1000,
            snapshotJson = """{"source":"legacy-v01","timing":"UNVERIFIED","stages":[]}""",
            legacyJson = payload
        )
        return database.records().importSessionIfMissing(record) != -1L
    }
}
