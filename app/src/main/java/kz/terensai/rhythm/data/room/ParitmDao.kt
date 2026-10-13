package kz.terensai.rhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ParitmDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun addProgram(row: ProgramRow)
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun importSessionIfMissing(row: SessionRow): Long
    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun startSession(row: SessionRow)
    /** Event IDs are idempotency keys: double taps never append the same event twice. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun appendEvent(row: EventRow): Long

    @Query("SELECT * FROM programs ORDER BY title")
    fun programs(): List<ProgramRow>

    @Query("SELECT * FROM sessions WHERE id = :id LIMIT 1")
    fun session(id: String): SessionRow?

    @Query("SELECT * FROM sessions ORDER BY startedAtMs DESC")
    fun sessions(): List<SessionRow>

    /** Legacy imports are historical snapshots, never an active B2 session. */
    @Query("SELECT * FROM sessions WHERE finishedAtMs IS NULL AND legacyJson IS NULL ORDER BY startedAtMs DESC LIMIT 1")
    fun activeSession(): SessionRow?

    @Query("UPDATE sessions SET finishedAtMs = :atMs WHERE id = :id AND finishedAtMs IS NULL")
    fun finishSession(id: String, atMs: Long): Int

    @Query("SELECT COUNT(*) FROM sessions WHERE finishedAtMs IS NULL AND legacyJson IS NULL")
    fun activeSessionCount(): Int

    @Query("SELECT * FROM session_events WHERE sessionId = :id ORDER BY atMs, id")
    fun events(id: String): List<EventRow>

    @Query("SELECT COUNT(*) FROM sessions")
    fun sessionCount(): Int
}
