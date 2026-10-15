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
class P21StageReturnPersistenceTest {
    private fun plan()=ProgramPlan("qa","QA",120_000L,listOf(
        StagePlan("first","Коллективное парение",Track.GUEST,0,60_000),
        StagePlan("second","Отдых",Track.GUEST,60_000,60_000)
    ))
    @Test fun stageReopenPausesNextAndRestoresTimeAfterRestart()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var now=1_800_000_000_000L
        try {
            val repo=SessionRepository(db,Clock{now},"qa")
            val id=repo.start(plan()).id
            repo.perform(id,"first",EventKind.START)
            now+=240_000L
            repo.perform(id,"first",EventKind.COMPLETE)
            now+=1_000L
            repo.perform(id,"second",EventKind.START)
            now+=20_000L
            val before=repo.readById(id)!!.progress.stages[0].elapsedMs
            val returned=repo.reopenPrevious(id,"first","return-once")
            assertEquals(StageStatus.RUNNING,returned.progress.stages[0].status)
            assertEquals(StageStatus.PAUSED,returned.progress.stages[1].status)
            val eventCount=returned.eventCount
            repo.reopenPrevious(id,"first","return-once")
            assertEquals(eventCount,repo.readById(id)!!.eventCount)
            now+=180_000L
            repo.perform(id,"first",EventKind.COMPLETE)
            val fresh=SessionRepository(db,Clock{now},"qa").readById(id)!!
            assertTrue(fresh.progress.stages[0].elapsedMs>=before+180_000L)
            assertEquals(1,db.records().events(id).count{it.kind=="REOPEN"})
        } finally { db.close() }
    }
    @Test fun skippedRunningStageReturnExcludesInactiveGapInJournal()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var now=1_800_000_000_000L
        try {
            val staff=StaffRepository(db,"qa"){now}
            val master=staff.create("Тестовый мастер",asMine=true)
            val repo=SessionRepository(db,Clock{now},"qa")
            val id=repo.start(plan()).id
            staff.appointPm1(id,master.id)
            repo.perform(id,"first",EventKind.START)
            now+=60_000L
            repo.perform(id,"first",EventKind.SKIP)
            now+=600_000L // The gap must not be counted as personal work.
            repo.reopenPrevious(id,"first")
            now+=60_000L
            repo.perform(id,"first",EventKind.COMPLETE)
            val personal=WorkRepository(db,"qa"){now}
                .attributeCompletedStage(master.id,id,"first")
            assertEquals("COMPLETED",personal.state)
            assertEquals(repo.readById(id)!!.progress.stages.first().elapsedMs,personal.elapsedMs)
            assertTrue(personal.elapsedMs in 119_998L..120_001L)
            assertEquals(1,db.work().forMaster(master.id).size)
        }finally{db.close()}
    }
    @Test fun stageAlreadyAttributedIsProtected()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var now=1_800_000_000_000L
        try {
            val staff=StaffRepository(db,"qa"){now}
            val master=staff.create("Тестовый мастер",asMine=true)
            val repo=SessionRepository(db,Clock{now},"qa")
            val id=repo.start(plan()).id
            staff.appointPm1(id,master.id)
            repo.perform(id,"first",EventKind.START)
            now+=60_000L
            repo.perform(id,"first",EventKind.COMPLETE)
            val work=WorkRepository(db,"qa"){now}
            assertEquals("COMPLETED",work.attributeCompletedStage(master.id,id,"first").state)
            val count=repo.readById(id)!!.eventCount
            try {repo.reopenPrevious(id,"first");fail("Expected guard")}
            catch (_:IllegalStateException){}
            assertEquals(count,repo.readById(id)!!.eventCount)
            assertEquals(1,db.work().forMaster(master.id).size)
        }finally {db.close()}
    }
}
