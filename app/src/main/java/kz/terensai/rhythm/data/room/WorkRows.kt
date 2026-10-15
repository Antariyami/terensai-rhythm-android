package kz.terensai.rhythm.data.room

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName="work_entries",foreignKeys=[
    ForeignKey(entity=StaffRow::class,parentColumns=["id"],childColumns=["staffId"],onDelete=ForeignKey.RESTRICT)
],indices=[Index("staffId"),Index("sessionId"),
    Index(value=["sessionId","staffId","sourceStageId"],unique=true)])
data class WorkRow(
    @PrimaryKey val id:String,
    /** Nullable for standalone participation in another PM1's programme. */
    val sessionId:String?,
    val staffId:String,
    val role:String,
    val procedureType:String,
    val plannedMs:Long,
    val createdAtMs:Long,
    val originClientId:String,
    val revision:Long=0L,
    val pm1StaffId:String?=null,
    val pm1NameSnapshot:String?=null,
    val suiteNameSnapshot:String?=null,
    val programmeNameSnapshot:String?=null,
    /** Non-null only for a stage explicitly attributed to this performer. */
    val sourceStageId:String?=null,
    /** Optional exact SPA/menu service, with the original generic journal category. */
    val serviceLabelSnapshot:String?=null
)

@Entity(tableName="work_events",foreignKeys=[
    ForeignKey(entity=WorkRow::class,parentColumns=["id"],childColumns=["workId"],onDelete=ForeignKey.CASCADE)
],indices=[Index("workId")])
data class WorkEventRow(
    @PrimaryKey val id:String,
    val workId:String,
    val kind:String,
    val atMs:Long
)
