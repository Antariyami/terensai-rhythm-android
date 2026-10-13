package kz.terensai.rhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WorkDao {
    @Insert(onConflict=OnConflictStrategy.ABORT)
    fun create(row:WorkRow)
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    fun append(row:WorkEventRow):Long
    @Query("SELECT * FROM work_entries WHERE id=:id LIMIT 1")
    fun entry(id:String):WorkRow?
    @Query("SELECT * FROM work_entries WHERE staffId=:staffId ORDER BY createdAtMs DESC")
    fun forMaster(staffId:String):List<WorkRow>
    @Query("SELECT * FROM work_events WHERE workId=:id ORDER BY atMs,rowid")
    fun events(id:String):List<WorkEventRow>
    @Insert(onConflict=OnConflictStrategy.ABORT)
    fun addCorrection(row:WorkCorrectionRow)
    @Query("SELECT * FROM work_corrections WHERE workId=:id ORDER BY createdAtMs,rowid")
    fun corrections(id:String):List<WorkCorrectionRow>
    @Query("UPDATE work_entries SET revision=revision+1 WHERE id=:id")
    fun touch(id:String):Int
}
