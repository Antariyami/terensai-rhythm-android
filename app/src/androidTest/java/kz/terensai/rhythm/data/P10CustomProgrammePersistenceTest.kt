package kz.terensai.rhythm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.domain.Clock
import kz.terensai.rhythm.domain.CustomProgrammeDraft
import kz.terensai.rhythm.domain.CustomProgrammePlan
import kz.terensai.rhythm.domain.CustomStageInput
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.domain.Track
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P10CustomProgrammePersistenceTest {
    @Test fun customRouteEventsRemainAccessibleAfterRepositoryRecreation()=runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var time=1_800_000_000_000L
        try {
            val plan=CustomProgrammePlan.toPlan(CustomProgrammeDraft("Мой маршрут",listOf(
                CustomStageInput("Встреча гостей",10,Track.GUEST),
                CustomStageInput("Коллективное парение",12,Track.MASTER)
            )))
            val first=SessionRepository(db,Clock{time},"device1")
            val session=first.start(plan,SessionMetadata(suite="3 · Байлық",guestCount=4),
                requestId="custom-session-test")
            assertEquals("Мой маршрут",session.programmeTitle)
            assertEquals(2,session.progress.stages.size)
            first.perform(session.id,"custom-stage-1",EventKind.START)
            time+=30_000
            first.perform(session.id,"custom-stage-1",EventKind.PAUSE)
            val recovered=SessionRepository(db,Clock{time},"device1").readById(session.id)!!
            assertEquals(StageStatus.PAUSED,recovered.progress.stages.first().status)
            assertEquals("3 · Байлық",recovered.metadata.suite)
            assertEquals(2,recovered.eventCount)
            time+=60_000
            first.perform(session.id,"custom-stage-1",EventKind.RESUME)
            assertEquals(StageStatus.RUNNING,
                SessionRepository(db,Clock{time},"device1").readById(session.id)!!
                    .progress.stages.first().status)
        }finally{db.close()}
    }
}
