package kz.terensai.rhythm.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.domain.Clock
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.StagePlan
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.domain.Track
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSessionRepositoryTest {
    private lateinit var db: ParitmDatabase
    private class TestClock(var time: Long) : Clock { override fun epochMillis() = time }
    private val clock = TestClock(1_000_000L)
    private val plan = ProgramPlan("qa","test",600_000L,
        listOf(StagePlan("s","test stage",Track.GUEST,0,300_000L)))

    @Before fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            ParitmDatabase::class.java
        ).build()
    }

    @After fun tearDown() { db.close() }

    @Test fun retryOfOneRequestIsIdempotentButDeliberateNewRequestIsIndependent() = runBlocking {
        val repo = SessionRepository(db, clock)
        val a = repo.start(plan, requestId="request-A")
        val same = repo.start(plan, requestId="request-A")
        val b = repo.start(plan, requestId="request-B")
        assertEquals(a.id,same.id)
        assertNotEquals(a.id,b.id)
        assertEquals(2,db.records().activeSessionCount())
        assertEquals(2,repo.activeSessions().size)
    }

    @Test fun fiveIndependentSessionsKeepTheirOwnStagesAndCanRestoreById() = runBlocking {
        val repo = SessionRepository(db,clock)
        val ids = (1..5).map { i ->
            repo.start(plan,requestId="request-$i").id
        }
        assertEquals(5,repo.activeSessions().size)
        try {
            repo.start(plan,requestId="request-sixth")
            fail("Must enforce maximum of 5 programmes")
        } catch (_: IllegalStateException) { }
        repo.perform(ids[0],"s",EventKind.START,"first-start")
        clock.time += 17_000L
        repo.perform(ids[0],"s",EventKind.PAUSE,"first-pause")
        repo.perform(ids[1],"s",EventKind.START,"second-start")
        clock.time += 8_000L
        val restored=SessionRepository(db,clock)
        assertEquals(ids[0],restored.readById(ids[0])!!.id)
        assertEquals(StageStatus.PAUSED,restored.readById(ids[0])!!.progress.stages.single().status)
        assertEquals(17_000L,restored.readById(ids[0])!!.progress.stages.single().elapsedMs)
        assertEquals(StageStatus.RUNNING,restored.readById(ids[1])!!.progress.stages.single().status)
        assertEquals(StageStatus.NOT_STARTED,restored.readById(ids[2])!!.progress.stages.single().status)
        assertEquals(5,restored.activeSessions().size)
        repo.perform(ids[0],"s",EventKind.COMPLETE,"first-complete")
        repo.finish(ids[0])
        assertEquals(4,restored.activeSessions().size)
        val next=repo.start(plan,requestId="request-reuse-slot")
        assertNotNull(next)
        assertEquals(5,restored.activeSessions().size)
    }

    @Test fun pauseAndResumeSurviveNewRepositoryInstance() = runBlocking {
        val repo = SessionRepository(db,clock)
        val s=repo.start(plan)
        repo.perform(s.id,"s",EventKind.START, "go")
        clock.time += 15_000
        repo.perform(s.id,"s",EventKind.PAUSE, "pause")
        clock.time += 120_000
        val recovered=SessionRepository(db,clock).active()!!
        assertEquals(s.id,recovered.id)
        assertEquals(StageStatus.PAUSED,recovered.progress.stages.single().status)
        assertEquals(15_000L,recovered.progress.stages.single().elapsedMs)
        assertEquals(135_000L,recovered.progress.elapsedWallClockMs)
        val duplicate=repo.perform(s.id,"s",EventKind.PAUSE,"pause")
        assertEquals(2,duplicate.eventCount)
        repo.perform(s.id,"s",EventKind.RESUME,"resume")
        clock.time += 5_000
        val after=repo.active()!!
        assertEquals(20_000L,after.progress.stages.single().elapsedMs)
    }

    @Test fun closedSessionIsNotReturnedAsActiveAndKeepsItsEvents() = runBlocking {
        val repo=SessionRepository(db,clock)
        val s=repo.start(plan)
        repo.perform(s.id,"s",EventKind.START,"start")
        clock.time += 12_000
        repo.perform(s.id,"s",EventKind.COMPLETE,"complete")
        repo.finish(s.id)
        assertNull(repo.active())
        assertEquals(2,repo.readById(s.id)!!.eventCount)
    }
}
