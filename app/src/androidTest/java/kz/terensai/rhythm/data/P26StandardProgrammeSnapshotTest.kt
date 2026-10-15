package kz.terensai.rhythm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P26StandardProgrammeSnapshotTest {
    @Test fun editableLightPlanIsSavedAsImmutableSessionSnapshotWithoutFakeWork()=runBlocking{
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            var now=1_800_000_000_000L
            val repo=SessionRepository(db,Clock{now},"qa-p26")
            val original=StandardRouteDraft("light",guests=6,simultaneousGuests=2,
                bookedExtraHours=1)
            val id=repo.start(StandardProgrammeRoute.toPlan(original),
                SessionMetadata("1 Көркемдік",6),"standard-six-guests").id
            val first=repo.readById(id)!!
            assertEquals(6,first.metadata.guestCount)
            assertEquals(300*60_000L,first.progress.plannedEndMs-first.startedAtMs)
            assertEquals(3,first.progress.stages.count{it.plan.id.startsWith("individual-")})
            assertEquals(0,db.work().forSession(id).size)
            val other=StandardRouteDraft("light",guests=2,
                minuteOverrides=mapOf("welcome" to 30))
            assertEquals(30,StandardProgrammeRoute.preview(other).steps[0].minutes)
            assertEquals(15,repo.readById(id)!!.progress.stages[0].plan.plannedDurationMs/60_000L)
            repo.perform(id,"welcome",EventKind.START)
            now+=15*60_000L
            repo.perform(id,"welcome",EventKind.COMPLETE)
            val fresh=SessionRepository(db,Clock{now},"qa-p26").readById(id)!!
            assertEquals(StageStatus.COMPLETED,fresh.progress.stages[0].status)
            assertEquals(0,db.work().forSession(id).size)
        }finally{db.close()}
    }
}
