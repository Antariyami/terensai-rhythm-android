package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kz.terensai.rhythm.data.room.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class WorkView(val row:WorkRow,val state:String,val elapsedMs:Long,val events:List<WorkEventRow>)

class WorkRepository(private val db:ParitmDatabase,private val origin:String,
    private val now:()->Long={System.currentTimeMillis()}){
    private val dao=db.work()
    private fun projection(row:WorkRow, events:List<WorkEventRow>,at:Long):WorkView {
        var state="READY";var since:Long?=null;var elapsed=0L
        for(e in events){
            when(e.kind){
                "START" -> {if(state=="READY"){since=e.atMs;state="RUNNING"}}
                "RESUME" -> {if(state=="PAUSED"){since=e.atMs;state="RUNNING"}}
                "PAUSE","COMPLETE" -> {
                    if(state=="RUNNING"){
                        elapsed+=(e.atMs-(since?:e.atMs)).coerceAtLeast(0L)
                        since=null
                        state=if(e.kind=="PAUSE")"PAUSED" else "COMPLETED"
                    }else if(e.kind=="COMPLETE"&&state=="PAUSED")state="COMPLETED"
                }
            }
        }
        if(state=="RUNNING")elapsed+=(at-(since?:at)).coerceAtLeast(0L)
        return WorkView(row,state,elapsed,events)
    }
    suspend fun create(masterId:String,sessionId:String?,role:String="PM",
        procedure:String="INDIVIDUAL_STEAM",plannedMinutes:Int=45):WorkView=withContext(Dispatchers.IO){
        require(plannedMinutes in 1..240 && role in setOf("PM","SPA","PM1"))
        db.withTransaction {
            require(db.staff().member(masterId)?.archived==false){"Выбери действующий профиль мастера"}
            if(sessionId!=null)require(db.records().session(sessionId)!=null){"Программа не найдена"}
            val row=WorkRow(UUID.randomUUID().toString(),sessionId,masterId,role,procedure,
                plannedMinutes*60000L,now(),origin)
            dao.create(row)
            projection(row,emptyList(),now())
        }
    }
    suspend fun get(id:String):WorkView?=withContext(Dispatchers.IO){
        val row=dao.entry(id)?:return@withContext null
        projection(row,dao.events(id),now())
    }
    suspend fun all(masterId:String):List<WorkView> = withContext(Dispatchers.IO){
        dao.forMaster(masterId).map {projection(it,dao.events(it.id),now())}
    }
    suspend fun command(id:String,kind:String):WorkView=withContext(Dispatchers.IO){
        db.withTransaction{
            val row=dao.entry(id)?:error("Процедура не найдена")
            val previous=dao.events(id)
            val current=projection(row,previous,now())
            val allowed=when(current.state){
                "READY"->kind=="START"
                "RUNNING"->kind=="PAUSE"||kind=="COMPLETE"
                "PAUSED"->kind=="RESUME"||kind=="COMPLETE"
                else->false
            }
            check(allowed){"Действие недоступно в состоянии ${current.state}"}
            val at=now().coerceAtLeast(previous.lastOrNull()?.atMs?:row.createdAtMs)
            check(dao.append(WorkEventRow(UUID.randomUUID().toString(),id,kind,at))!=-1L)
            check(dao.touch(id)==1)
            projection(dao.entry(id)!!,dao.events(id),at)
        }
    }
}
