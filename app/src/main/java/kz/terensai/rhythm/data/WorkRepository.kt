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
        procedure:String="INDIVIDUAL_STEAM",plannedMinutes:Int=45,
        externalPm1Id:String?=null,externalSuite:String?=null,
        externalProgramme:String?=null):WorkView=withContext(Dispatchers.IO){
        require(plannedMinutes in 1..240 && role in setOf("PM","SPA","PM1"))
        require(procedure in setOf("INDIVIDUAL_STEAM","COLLECTIVE_STEAM","MASSAGE","PEELING","WASHING","OTHER"))
        if(sessionId==null) {
            require(!externalPm1Id.isNullOrBlank()){"Выбери ведущего PM1"}
            require(!externalSuite.isNullOrBlank()){"Укажи сьют"}
        }
        db.withTransaction {
            require(db.staff().member(masterId)?.archived==false){"Выбери действующий профиль мастера"}
            if(sessionId!=null)require(db.records().session(sessionId)!=null){"Программа не найдена"}
            val leader = if(sessionId!=null) {
                db.staff().activeLeader(sessionId)?.let {db.staff().member(it.staffId)}
            }else externalPm1Id?.let { db.staff().member(it) }
            val localSession=sessionId?.let{db.records().session(it)}
            val localSuite=localSession?.let{
                runCatching {ProgramSnapshotCodec.metadata(it.snapshotJson).suite}.getOrNull()
            }
            if(sessionId==null) require(leader!=null && !leader.archived){"PM1 не найден в справочнике"}
            val row=WorkRow(UUID.randomUUID().toString(),sessionId,masterId,role,procedure,
                plannedMinutes*60000L,now(),origin,
                pm1StaffId=leader?.id,
                pm1NameSnapshot=leader?.displayName,
                suiteNameSnapshot=(localSuite ?: externalSuite)?.trim()?.take(48),
                programmeNameSnapshot=(localSession?.templateTitle ?: externalProgramme)?.trim()?.take(80))
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
    /**
     * Old QA versions permitted several RUNNING timers for one master. On first
     * open, keep the most recently started one and pause every other running work.
     * All corrections are new events; history is never silently discarded.
     */
    suspend fun reconcileLegacyRunning(masterId:String):Unit=withContext(Dispatchers.IO){
        db.withTransaction{
            val timestamp=now()
            val running=dao.forMaster(masterId).mapNotNull { row ->
                val events=dao.events(row.id)
                val view=projection(row,events,timestamp)
                if(view.state=="RUNNING") Pair(view,
                    events.lastOrNull{it.kind=="START" || it.kind=="RESUME"}?.atMs?:0L) else null
            }.sortedWith(compareByDescending<Pair<WorkView,Long>>{it.second}.thenByDescending{it.first.row.createdAtMs})
            running.drop(1).forEach{(view,_)->
                val last=view.events.lastOrNull()?.atMs?:view.row.createdAtMs
                val stamp=timestamp.coerceAtLeast(last)
                check(dao.append(WorkEventRow(UUID.randomUUID().toString(),view.row.id,"PAUSE",stamp))!=-1L)
                check(dao.touch(view.row.id)==1)
            }
        }
    }

    /** START/RESUME atomically pauses other RUNNING work belonging to this master. */
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
            var at=now().coerceAtLeast(previous.lastOrNull()?.atMs?:row.createdAtMs)
            if(kind=="START" || kind=="RESUME"){
                // One Room transaction covers every PAUSE and the requested START/RESUME.
                // This invariant survives Activity/process restart.
                val others=dao.forMaster(row.staffId).filter{it.id!=row.id}.mapNotNull { other ->
                    val events=dao.events(other.id)
                    val state=projection(other,events,at)
                    if(state.state=="RUNNING") Pair(other,events) else null
                }
                others.forEach {(other, events)->
                    at=at.coerceAtLeast(events.lastOrNull()?.atMs?:other.createdAtMs)
                }
                others.forEach {(other,_)->
                    check(dao.append(WorkEventRow(UUID.randomUUID().toString(),other.id,"PAUSE",at))!=-1L)
                    check(dao.touch(other.id)==1)
                }
            }
            check(dao.append(WorkEventRow(UUID.randomUUID().toString(),id,kind,at))!=-1L)
            check(dao.touch(id)==1)
            projection(dao.entry(id)!!,dao.events(id),at)
        }
    }
}
