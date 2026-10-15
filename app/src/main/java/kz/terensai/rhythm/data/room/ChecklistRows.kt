package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="checklist_templates")
data class ChecklistTemplateRow(
    @PrimaryKey val id:String,
    val type:String,
    val version:Int,
    val json:String,
    val updatedAtMs:Long
)

@Entity(tableName="checklist_runs", indices=[Index("templateId")])
data class ChecklistRunRow(
    @PrimaryKey val id:String,
    val templateId:String,
    val templateVersion:Int,
    /** Immutable snapshot, kept even if the user edits/deletes the template. */
    val snapshotJson:String,
    val checkedJson:String,
    val notesJson:String,
    val status:String,
    val startedAtMs:Long,
    val updatedAtMs:Long,
    val completedAtMs:Long?=null
)
