package kz.terensai.rhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProgrammeActionDao {
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    fun append(row:ProgrammeActionRow):Long
    @Query("SELECT * FROM programme_action_events WHERE sessionId=:sessionId ORDER BY recordedAtMs,rowid")
    fun forSession(sessionId:String):List<ProgrammeActionRow>
    @Query("SELECT * FROM programme_action_events WHERE id=:id LIMIT 1")
    fun byId(id:String):ProgrammeActionRow?
}
