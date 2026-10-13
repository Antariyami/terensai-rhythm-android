package kz.terensai.rhythm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.*
import kz.terensai.rhythm.domain.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class B243aStageAttributionRegressionTest {
    @Test fun programmeStageBecomesPersonalReportOnlyAfterAttributionAndNeverDuplicates()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var tick=1_800_000_000_000L
        try {
            val staff=StaffRepository(db,"qa"){tick}
            val master=staff.create("Андрей",asMine=true)
            val sessionRepo=SessionRepository(db,Clock{tick},"qa")
            val session=sessionRepo.start(
                PilotTanysuPlan.toPlan(PilotTanysuPlan.defaults.map{it.minutes}),
                SessionMetadata(suite="2 · Өркендеу",guestCount=3)
            )
            staff.appointPm1(session.id,master.id)
            val initial=WorkJournalRepository(db,"qa"){tick}.all(master.id)
            assertTrue(initial.isEmpty())
            sessionRepo.perform(session.id,"steam1",EventKind.START)
            tick+=60000L
            sessionRepo.perform(session.id,"steam1",EventKind.COMPLETE)
            val storedStageEvents=db.records().events(session.id).filter{it.stageId=="steam1"}
            assertEquals(listOf("START","COMPLETE"),storedStageEvents.map{it.kind})
            val workRepo=WorkRepository(db,"qa"){tick}
            val recovered=workRepo.attributeCompletedStage(master.id,session.id,"steam1")
            assertEquals("COMPLETED",recovered.state)
            assertEquals("COLLECTIVE_STEAM",recovered.row.procedureType)
            assertEquals("2 · Өркендеу",recovered.row.suiteNameSnapshot)
            assertEquals("Андрей",recovered.row.pm1NameSnapshot)
            assertEquals(storedStageEvents.map{it.atMs},recovered.events.map{it.atMs})
            val repeat=workRepo.attributeCompletedStage(master.id,session.id,"steam1")
            assertEquals(recovered.row.id,repeat.row.id)
            assertEquals(1,db.work().forMaster(master.id).size)
            assertEquals(2,db.work().events(recovered.row.id).size)
            assertEquals(storedStageEvents,db.records().events(session.id).filter{it.stageId=="steam1"})
            val journal=WorkJournalRepository(db,"qa"){tick}.all(master.id)
            val date=Instant.ofEpochMilli(recovered.events.first().atMs)
                .atZone(ZoneId.systemDefault()).toLocalDate()
            val report=TelegramWorkReport.message(date,"Андрей",journal)
            assertTrue(report.contains("Коллективка — 1"))
            assertTrue(report.contains("Индивидуальное парение — 0"))
            try {
                workRepo.attributeCompletedStage(master.id,session.id,"rest1")
                fail("Rest stage must never become a steaming")
            }catch(_:IllegalStateException){} catch(_:IllegalArgumentException){}
        }finally{db.close()}
    }
}
