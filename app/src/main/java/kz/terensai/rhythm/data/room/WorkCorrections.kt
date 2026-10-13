package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only correction of reported billable/actual minutes.
 * Never change original work_events: both measured time and corrections
 * remain auditable. The newest correction by timestamp / rowid wins.
 */
@Entity(
    tableName="work_corrections",
    foreignKeys=[ForeignKey(
        entity=WorkRow::class,parentColumns=["id"],childColumns=["workId"],
        onDelete=ForeignKey.CASCADE
    )],
    indices=[Index("workId")]
)
data class WorkCorrectionRow(
    @PrimaryKey val id:String,
    val workId:String,
    val createdAtMs:Long,
    val priorMinutes:Long,
    val correctedMinutes:Long,
    val reason:String
)
