package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kz.terensai.rhythm.data.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/** No accounts or access control in this local-only iteration. */
class StaffRepository(
    private val db:ParitmDatabase,
    private val origin:String="local-qa",
    private val clock:()->Long={System.currentTimeMillis()}
) {
    private val dao=db.staff()
    private fun clean(s:String)=s.trim().replace(Regex("\\s+")," ").take(80)
    suspend fun all():List<StaffRow> = withContext(Dispatchers.IO){dao.all()}
    suspend fun mine():StaffRow?=withContext(Dispatchers.IO){dao.myProfile()}
    suspend fun create(name:String,specialties:String="",asMine:Boolean=false):StaffRow =
        withContext(Dispatchers.IO){
            val trimmed=clean(name)
            require(trimmed.isNotBlank()) {"Укажи имя мастера"}
            db.withTransaction {
                val row=StaffRow(UUID.randomUUID().toString(),trimmed,clean(specialties),
                    isMyProfile=false,originClientId=origin,updatedAtMs=clock())
                dao.add(row)
                if(asMine) {dao.clearMyProfile();check(dao.selectMyProfile(row.id,clock())==1)}
                dao.member(row.id)!!
            }
        }
    suspend fun edit(id:String,name:String,specialties:String):Unit=withContext(Dispatchers.IO) {
        require(clean(name).isNotEmpty())
        check(dao.update(id,clean(name),clean(specialties),clock())==1)
    }
    suspend fun chooseMine(id:String):Unit=withContext(Dispatchers.IO) {
        db.withTransaction {
            require(dao.member(id)?.archived==false)
            dao.clearMyProfile()
            check(dao.selectMyProfile(id,clock())==1)
        }
    }
    suspend fun archive(id:String):Unit=withContext(Dispatchers.IO) {
        db.withTransaction {
            require(dao.member(id)!=null)
            check(dao.archive(id,clock())==1)
        }
    }
    suspend fun leader(sessionId:String):StaffRow?=withContext(Dispatchers.IO){
        dao.activeLeader(sessionId)?.let { dao.member(it.staffId) }
    }
    suspend fun assignments(sessionId:String):List<AssignmentRow> =
        withContext(Dispatchers.IO){dao.assignments(sessionId)}
    /** Atomic PM1 handover; historical assignment records are not erased. */
    suspend fun appointPm1(sessionId:String,staffId:String):AssignmentRow =
        withContext(Dispatchers.IO){
            db.withTransaction {
                require(db.records().session(sessionId)?.finishedAtMs==null) {"Сессия уже завершена"}
                require(dao.member(staffId)?.archived==false) {"Мастер недоступен"}
                val old=dao.activeLeader(sessionId)
                if(old?.staffId==staffId) return@withTransaction old
                val now=clock()
                if(old!=null) check(dao.endAssignment(old.id,now)==1)
                val row=AssignmentRow(UUID.randomUUID().toString(),sessionId,staffId,
                    "PM1",now,originClientId=origin,updatedAtMs=now)
                dao.addAssignment(row)
                check(db.records().touchSession(sessionId,now)==1)
                row
            }
        }
}
