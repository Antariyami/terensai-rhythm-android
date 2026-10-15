package kz.terensai.rhythm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P24LiveActionsPersistenceTest {
    private fun plan()=ProgramPlan("qa","QA",180_000L,listOf(
        StagePlan("stage","Встреча гостей",Track.GUEST,0,90_000L),
        StagePlan("rest","Уборка и отдых",Track.MASTER,90_000L,90_000L)
    ))

    @Test fun waitingAndPausingStageAreAtomicAndHourOnlyExtendsPlan()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var time=1_800_000_000_000L
        try{
            val session=SessionRepository(db,Clock{time},"qa")
            val id=session.start(plan()).id
            session.perform(id,"stage",EventKind.START)
            time+=10_000L
            val repo=ProgrammeActionRepository(db){time}
            val begin=repo.begin(id,"GUEST_WAIT","Ожидаем гостя","stage","once-wait")
            assertEquals(1,begin.items.size)
            assertTrue(begin.items.first().active)
            assertEquals(StageStatus.PAUSED,
                session.readById(id)!!.progress.stages[0].status)
            val before=db.records().events(id).size
            repo.begin(id,"GUEST_WAIT","Ожидаем гостя","stage","once-wait")
            assertEquals(before,db.records().events(id).size)
            time+=120_000L
            repo.finish(id,"once-wait","once-stop")
            assertEquals(120_000L,repo.view(id).items[0].effectiveDurationMs)
            repo.correctMinutes(id,"once-wait",110,"Ожидание было короче","once-fix")
            val corrected=repo.view(id)
            assertEquals(110*60_000L,corrected.items.first().effectiveDurationMs)
            assertEquals(3,corrected.items.first().revisionCount)
            val extra=repo.addHour(id,"once-hour")
            assertEquals(1,extra.extraHours)
            assertEquals(3_600_000L,extra.extensionMs)
            repo.addHour(id,"once-hour")
            assertEquals(1,repo.view(id).extraHours)
            assertEquals(plan().plannedDurationMs,
                kz.terensai.rhythm.data.room.ProgramSnapshotCodec.decode(
                    db.records().session(id)!!.snapshotJson).plannedDurationMs)
            assertEquals(0,db.work().forSession(id).size)
        }finally{db.close()}
    }

    @Test fun extraWorkCanBeAssignedToDifferentMasterAndCountsOnlyWhenCompleted()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var time=1_800_000_000_000L
        try{
            val staff=StaffRepository(db,"qa"){time}
            val pm=staff.create("Ведущий",asMine=true)
            val spa=staff.create("SPA специалист")
            val session=SessionRepository(db,Clock{time},"qa").start(plan())
            staff.appointPm1(session.id,pm.id)
            val repo=WorkRepository(db,"qa"){time}
            val entry=repo.create(masterId=spa.id,sessionId=session.id,
                role="SPA",procedure="MASSAGE",plannedMinutes=30,
                serviceLabelSnapshot="Ветер Ақжар",requestId="idempotent-one")
            assertEquals("READY",entry.state)
            val duplicate=repo.create(masterId=spa.id,sessionId=session.id,
                role="SPA",procedure="MASSAGE",plannedMinutes=30,
                serviceLabelSnapshot="Ветер Ақжар",requestId="idempotent-one")
            assertEquals(entry.row.id,duplicate.row.id)
            assertEquals(1,repo.sessionWorks(session.id).size)
            repo.command(entry.row.id,"START")
            time+=1_800_000L
            val done=repo.command(entry.row.id,"COMPLETE")
            assertEquals("COMPLETED",done.state)
            assertEquals("Ветер Ақжар",done.row.serviceLabelSnapshot)
            assertEquals(1,repo.sessionWorks(session.id).count{it.state=="COMPLETED"})
            assertEquals(1,repo.all(spa.id).size)
            assertTrue(repo.all(pm.id).isEmpty())
        }finally{db.close()}
    }
}
