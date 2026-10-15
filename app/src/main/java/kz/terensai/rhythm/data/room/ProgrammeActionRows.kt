package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Append-only actions alongside the stage journal. They never change the
 * immutable ProgramPlan or create a fake StagePlan/WorkEntry.
 */
@Entity(tableName="programme_action_events",
    foreignKeys=[ForeignKey(entity=SessionRow::class,
        parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.CASCADE)],
    indices=[Index("sessionId"),Index("entityId")])
data class ProgrammeActionRow(
    @PrimaryKey val id:String,
    val entityId:String,
    val sessionId:String,
    /** GUEST_WAIT, TECH_WAIT, OTHER_WAIT or EXTRA_HOUR. */
    val kind:String,
    /** START, STOP, CORRECT or ADD_HOUR. */
    val action:String,
    /** Work-effective event instant; for correction this is record time. */
    val atMs:Long,
    val recordedAtMs:Long,
    /** Only CORRECT and ADD_HOUR carry an explicit duration. */
    val durationOverrideMs:Long?=null,
    val note:String=""
)
