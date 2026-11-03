package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Append-only corrections: never rewrite session_events or session snapshot. */
@Entity(tableName="stage_fact_corrections",
    foreignKeys=[ForeignKey(entity=SessionRow::class,
        parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("sessionId"),Index(value=["sessionId","stageId"])])
data class StageFactCorrectionRow(
    @PrimaryKey val id:String,
    val sessionId:String,
    val stageId:String,
    val effectiveStartedAtMs:Long,
    val effectiveFinishedAtMs:Long?,
    val priorStartedAtMs:Long?,
    val priorFinishedAtMs:Long?,
    val recordedAtMs:Long,
    val actor:String,
    val reason:String
)

/** A protected, auditable close: no deletion of stages or already performed work. */
@Entity(tableName="session_closures",
    foreignKeys=[ForeignKey(entity=SessionRow::class,
        parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.CASCADE)])
data class SessionClosureRow(
    @PrimaryKey val sessionId:String,
    val outcome:String,
    val reason:String,
    val recordedAtMs:Long
)
