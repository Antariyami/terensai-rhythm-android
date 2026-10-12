package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "programs")
data class ProgramRow(
    @PrimaryKey val id: String,
    val title: String,
    val plannedDurationMs: Long,
    val immutableStagesJson: String,
    val updatedAtMs: Long
)

@Entity(tableName = "sessions")
data class SessionRow(
    @PrimaryKey val id: String,
    val templateId: String,
    val templateTitle: String,
    val startedAtMs: Long,
    val plannedDurationMs: Long,
    /** The full template as it was when the session was created. Never rewrite retrospectively. */
    val snapshotJson: String,
    /** Raw v0.1 journal/state, retained without making up absent timing data. */
    val legacyJson: String? = null,
    val finishedAtMs: Long? = null
)

@Entity(
    tableName = "session_events",
    foreignKeys = [ForeignKey(
        entity = SessionRow::class, parentColumns = ["id"], childColumns = ["sessionId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("sessionId")]
)
data class EventRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val stageId: String,
    val kind: String,
    val atMs: Long,
    val referenceEventId: String? = null
)
