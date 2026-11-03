package kz.terensai.rhythm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.domain.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P28StageCorrectionTest {
    private class MutableClock(var ms:Long):Clock{override fun epochMillis()=ms}
    @Test fun correctionAndProtectedFinishSurviveRestartWithoutOriginalEventMutation()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        val time=MutableClock(1_800_000_000_000L)
        val plan=ProgramPlan("grand-qa","GRAND",7_200_000L,listOf(
            StagePlan("welcome","Встреча",Track.GUEST,0,1_200_000L),
            StagePlan("collective","Коллективное парение",Track.GUEST,1_200_000L,1_800_000L)))
        try{
            val repo=SessionRepository(db,time)
            val id=repo.start(plan,requestId="qa-grand").id
            repo.perform(id,"welcome",EventKind.START,"started")
            time.ms+=900_000L
            repo.perform(id,"welcome",EventKind.COMPLETE,"completed")
            val original=db.records().events(id).toList()
            val changed=repo.correctStageFact(id,"welcome",
                time.ms-960_000L,time.ms-60_000L,"Фактически начал раньше",
                "Тестовый мастер",requestId="correction-1")
            assertEquals(900_000L,changed.progress.stages[0].elapsedMs)
            assertEquals(original,db.records().events(id))
            assertEquals(1,repo.corrections(id).size)
            val repeated=repo.correctStageFact(id,"welcome",
                time.ms-960_000L,time.ms-60_000L,"Фактически начал раньше",
                "Тестовый мастер",requestId="correction-1")
            assertEquals(1,repo.corrections(id).size)
            assertEquals(1,db.records().events(id).count{it.kind=="COMPLETE"})
            assertEquals(900_000L,repeated.progress.stages[0].elapsedMs)
            val closed=repo.finishProtected(id,"INTERRUPTED","Гости сменили программу")
            assertEquals("INTERRUPTED",closed.closureOutcome)
            assertEquals(1,closed.progress.stages.count{it.status==StageStatus.SKIPPED})
            val restarted=SessionRepository(db,time).readById(id)!!
            assertEquals("INTERRUPTED",restarted.closureOutcome)
            assertEquals(1,restarted.correctedStageIds.size)
            assertEquals(900_000L,restarted.progress.stages[0].elapsedMs)
            assertTrue(SessionRepository(db,time).activeSessions().isEmpty())
        }finally{db.close()}
    }
}
