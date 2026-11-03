package kz.terensai.rhythm.checklists

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kz.terensai.rhythm.data.StaffRepository
import kz.terensai.rhythm.data.room.ParitmDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P28AllNormalTest {
    @Test fun markOneSuitePreservesOthersAndDoesNotConfirmProposed200()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try{
            val master=StaffRepository(db,"qa").create("Проверяющий",asMine=true)
            val repo=ChecklistRepository(db)
            repo.ensureDefaults()
            val duty=repo.newRun("duty-shift",master.id)
            assertEquals(54,ChecklistJson.decode(duty.snapshotJson).totalItems)
            val suite2=repo.allNormal(duty.id,"duty-suite-2")
            assertEquals(10,ChecklistJson.decodeAnswers(suite2.checkedJson).size)
            val notes=ChecklistJson.decodeNotes(suite2.notesJson)
            assertEquals("200",notes["temperature:duty-suite-2"])
            assertEquals("yes",notes["temperature-proposed:duty-suite-2"])
            assertFalse(notes.containsKey("temperature:duty-suite-1"))
            val duplicate=repo.allNormal(duty.id,"duty-suite-2")
            assertEquals(10,ChecklistJson.decodeAnswers(duplicate.checkedJson).size)
            assertTrue(DutyReport.renderShort(duplicate).contains("не подтверждена"))
            val confirmed=repo.saveNote(duty.id,"temperature:duty-suite-2","86")
            assertFalse(DutyReport.renderShort(confirmed).contains("86 (не подтверждена)"))
            assertTrue(DutyReport.renderShort(confirmed).contains("86"))
            repo.saveNote(duty.id,"arrivalTime","10:12")
            assertTrue(DutyReport.renderShort(repo.run(duty.id)!!).contains("Прибыл: 10:12"))
            val old=repo.run(duty.id)!!
            val completed=repo.complete(duty.id,"NOTES","Обход частично")
            try{repo.allNormal(duty.id,"duty-suite-1");fail("Completed cannot mutate")}
            catch(_:IllegalStateException){}
            assertEquals(old.checkedJson,completed.checkedJson)
        }finally{db.close()}
    }
}
