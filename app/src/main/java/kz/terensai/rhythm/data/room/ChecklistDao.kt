package kz.terensai.rhythm.data.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface ChecklistDao {
    @Query("SELECT * FROM checklist_templates ORDER BY type, updatedAtMs DESC")
    fun templates():List<ChecklistTemplateRow>
    @Query("SELECT * FROM checklist_templates WHERE id=:id LIMIT 1")
    fun template(id:String):ChecklistTemplateRow?
    @Insert(onConflict=OnConflictStrategy.IGNORE)
    fun seed(row:ChecklistTemplateRow):Long
    @Insert(onConflict=OnConflictStrategy.REPLACE)
    fun putTemplate(row:ChecklistTemplateRow)
    @Query("SELECT * FROM checklist_runs WHERE id=:id LIMIT 1")
    fun run(id:String):ChecklistRunRow?
    @Query("SELECT * FROM checklist_runs WHERE templateId=:id ORDER BY startedAtMs DESC,id DESC LIMIT 1")
    fun latest(id:String):ChecklistRunRow?
    @Query("SELECT * FROM checklist_runs WHERE templateId=:id AND status='IN_PROGRESS' ORDER BY startedAtMs DESC,id DESC LIMIT 1")
    fun active(id:String):ChecklistRunRow?
    @Insert(onConflict=OnConflictStrategy.ABORT)
    fun createRun(row:ChecklistRunRow)
    @Update fun updateRun(row:ChecklistRunRow):Int
    @Query("SELECT * FROM checklist_runs ORDER BY startedAtMs DESC")
    fun allRuns():List<ChecklistRunRow>
}
