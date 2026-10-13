package kz.terensai.rhythm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class B243JournalAuditTest {
    @Test fun completedWorkIsCorrectableButMeasuredEventsAreImmutable()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var tick=1000000L
        try {
            val staff=StaffRepository(db,"qa"){tick}
            val me=staff.create("Андрей",asMine=true)
            val pm1=staff.create("Олжас")
            val work=WorkRepository(db,"qa"){tick}
            val created=work.create(me.id,null,"PM","INDIVIDUAL_STEAM",45,
                externalPm1Id=pm1.id,externalSuite="4 · Байлық")
            work.command(created.row.id,"START")
            tick+=60000L
            work.command(created.row.id,"COMPLETE")
            val eventsBefore=db.work().events(created.row.id)
            assertEquals(60000L,work.get(created.row.id)!!.elapsedMs)
            val journal=WorkJournalRepository(db,"qa"){tick}
            val first=journal.correctCompleted(created.row.id,me.id,2L,"Ошибочно поздно остановлен таймер")
            assertEquals(2L,first.reportedMinutes)
            assertTrue(first.isAdjusted)
            tick+=1000L
            val second=journal.correctCompleted(created.row.id,me.id,3L,"Уточнение фактического времени")
            assertEquals(3L,second.reportedMinutes)
            assertEquals(2,second.corrections.size)
            assertEquals(1L,second.corrections[0].priorMinutes)
            assertEquals(2L,second.corrections[1].priorMinutes)
            assertEquals(eventsBefore,db.work().events(created.row.id))
            val reopened=WorkJournalRepository(db,"qa"){tick}.all(me.id).single()
            assertEquals(3L,reopened.reportedMinutes)
            assertEquals("COMPLETED",reopened.work.state)
            assertEquals(60000L,reopened.work.elapsedMs)
            assertEquals(3L,WorkJournalReport.totalMinutes(listOf(reopened)))
            try{
                journal.correctCompleted(created.row.id,me.id,4L,"")
                fail("Correction without a reason must fail")
            }catch(_:IllegalArgumentException){}
        }finally{db.close()}
    }
}
