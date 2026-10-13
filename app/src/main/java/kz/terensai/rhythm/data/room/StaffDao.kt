package kz.terensai.rhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StaffDao {
    @Query("SELECT * FROM staff_members WHERE archived=0 ORDER BY isMyProfile DESC, displayName COLLATE NOCASE")
    fun all(): List<StaffRow>
    @Query("SELECT * FROM staff_members ORDER BY displayName COLLATE NOCASE")
    fun allIncludingArchived():List<StaffRow>
    @Query("SELECT * FROM staff_members WHERE id=:id LIMIT 1")
    fun member(id:String): StaffRow?
    @Query("SELECT * FROM staff_members WHERE isMyProfile=1 AND archived=0 LIMIT 1")
    fun myProfile(): StaffRow?
    @Insert(onConflict=OnConflictStrategy.ABORT)
    fun add(row: StaffRow)
    @Query("UPDATE staff_members SET displayName=:name,specialties=:specialties,updatedAtMs=:now WHERE id=:id AND archived=0")
    fun update(id:String,name:String,specialties:String,now:Long):Int
    @Query("UPDATE staff_members SET archived=1,isMyProfile=0,updatedAtMs=:now WHERE id=:id")
    fun archive(id:String,now:Long):Int
    @Query("UPDATE staff_members SET isMyProfile=0 WHERE isMyProfile=1")
    fun clearMyProfile()
    @Query("UPDATE staff_members SET isMyProfile=1,updatedAtMs=:now WHERE id=:id AND archived=0")
    fun selectMyProfile(id:String,now:Long):Int

    @Insert(onConflict=OnConflictStrategy.ABORT)
    fun addAssignment(row:AssignmentRow)
    @Query("SELECT * FROM session_assignments WHERE sessionId=:sessionId ORDER BY startedAtMs")
    fun assignments(sessionId:String):List<AssignmentRow>
    @Query("SELECT * FROM session_assignments WHERE sessionId=:sessionId AND role='PM1' AND endedAtMs IS NULL LIMIT 1")
    fun activeLeader(sessionId:String):AssignmentRow?
    @Query("UPDATE session_assignments SET endedAtMs=:now,updatedAtMs=:now WHERE id=:id AND endedAtMs IS NULL")
    fun endAssignment(id:String,now:Long):Int
}
