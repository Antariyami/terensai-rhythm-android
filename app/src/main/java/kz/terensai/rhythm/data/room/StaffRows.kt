package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Person is independent from any role; role exists only in the session assignment. */
@Entity(tableName = "staff_members", indices = [Index(value=["displayName"])])
data class StaffRow(
    @PrimaryKey val id: String,
    val displayName: String,
    val specialties: String = "",
    val isMyProfile: Boolean = false,
    val archived: Boolean = false,
    val originClientId: String = "legacy-local",
    val updatedAtMs: Long = 0L
)

@Entity(
    tableName="session_assignments",
    foreignKeys=[
        ForeignKey(entity=SessionRow::class,parentColumns=["id"],childColumns=["sessionId"],onDelete=ForeignKey.CASCADE),
        ForeignKey(entity=StaffRow::class,parentColumns=["id"],childColumns=["staffId"],onDelete=ForeignKey.RESTRICT)
    ],
    indices=[Index("sessionId"),Index("staffId")]
)
data class AssignmentRow(
    @PrimaryKey val id: String,
    val sessionId: String,
    val staffId: String,
    val role: String,
    val startedAtMs: Long,
    val endedAtMs: Long? = null,
    val originClientId: String = "legacy-local",
    val updatedAtMs: Long = 0L
)
