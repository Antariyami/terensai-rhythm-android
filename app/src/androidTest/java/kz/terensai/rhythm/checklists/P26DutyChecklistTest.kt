package kz.terensai.rhythm.checklists

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.StaffRepository
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P26DutyChecklistTest {
    @Test fun untouchedDutyV1UpgradesWithoutRewritingOldHistory()=runBlocking{
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try{
            val staff=StaffRepository(db,"test"){1_800_000_000_000L}
            val master=staff.create("Мастер A",asMine=true)
            val old=ChecklistCatalog.dutyV1()
            db.checklists().putTemplate(kz.terensai.rhythm.data.room.ChecklistTemplateRow(
                old.id,old.type,old.version,ChecklistJson.encode(old),1_800_000_000_000L))
            val repo=ChecklistRepository(db){1_800_000_000_500L}
            val earlier=repo.newRun(old.id,master.id)
            repo.toggle(earlier.id,old.blocks.first().items.first().id)
            repo.ensureDefaults()
            val current=repo.template(old.id)!!
            assertEquals(3,current.version)
            assertEquals(54,current.totalItems)
            val historical=repo.run(earlier.id)!!
            assertEquals(1,historical.templateVersion)
            assertEquals(10,ChecklistJson.decode(historical.snapshotJson).totalItems)
            assertTrue(DutyReport.render(historical).contains("1 из 10"))
            val newRun=repo.newRun(old.id,master.id)
            assertEquals(54,ChecklistJson.decode(newRun.snapshotJson).totalItems)
            assertEquals("[]",newRun.checkedJson)
        }finally{db.close()}
    }

    @Test fun twoDutyRunsAndMasterSnapshotsSurviveRepositoryRestart()=runBlocking{
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var now=1_800_000_000_000L
        try{
            val staff=StaffRepository(db,"test"){now}
            val first=staff.create("Мастер дежурный",asMine=true)
            val second=staff.create("Напарник")
            val repo=ChecklistRepository(db){now}
            repo.ensureDefaults()
            assertEquals(3,repo.templates().size)
            val duty=ChecklistCatalog.duty()
            val firstRun=repo.newRun(duty.id,first.id)
            assertEquals("Мастер дежурный",firstRun.staffNameSnapshot)
            assertNotNull(firstRun.dutyDate)
            assertEquals(firstRun.id,repo.activeForStaff(duty.id,first.id)?.id)
            assertNull(repo.activeForStaff(duty.id,second.id))
            val item=duty.blocks[0].items.first().id
            repo.toggle(firstRun.id,item)
            repo.saveNote(firstRun.id,"duty-suite-2","Передано Юрию")
            val partial=repo.complete(firstRun.id,"NOTES","Не все проверки завершены")
            assertEquals("NOTES",partial.status)
            val result=DutyReport.render(partial)
            assertTrue(result.contains("Проверено: 1 из 54"))
            assertTrue(result.contains("Передано Юрию"))
            now+=86_400_000L
            val newRun=repo.newRun(duty.id,first.id)
            val otherRun=repo.newRun(duty.id,second.id)
            assertEquals("[]",newRun.checkedJson)
            assertNotEquals(firstRun.id,newRun.id)
            assertEquals(firstRun.staffIdSnapshot,newRun.staffIdSnapshot)
            assertEquals("Напарник",otherRun.staffNameSnapshot)
            val restarted=ChecklistRepository(db){now+1000L}
            assertEquals(firstRun.id,restarted.run(firstRun.id)?.id)
            assertEquals(result,DutyReport.render(restarted.run(firstRun.id)!!))
            assertEquals(newRun.id,restarted.activeForStaff(duty.id,first.id)?.id)
            assertEquals(otherRun.id,restarted.activeForStaff(duty.id,second.id)?.id)
            assertEquals(3,restarted.allRuns().count{it.templateId==duty.id})
        }finally{db.close()}
    }
}
