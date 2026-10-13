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
class ExternalParticipationTest {
    @Test fun externalWorkPersistsActualProcedureAndPm1Snapshot() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var now=1000L
        try {
            val staff=StaffRepository(db,"qa"){now}
            val me=staff.create("Исполнитель",asMine=true)
            val leader=staff.create("Ведущий PM1")
            val repo=WorkRepository(db,"qa"){now}
            val work=repo.create(me.id,null,"PM","MASSAGE",45,
                externalPm1Id=leader.id,externalSuite="1 · Көркемдік",
                externalProgramme="PREMIUM")
            assertEquals("MASSAGE",work.row.procedureType)
            assertEquals(leader.id,work.row.pm1StaffId)
            assertEquals("Ведущий PM1",work.row.pm1NameSnapshot)
            assertEquals("1 · Көркемдік",work.row.suiteNameSnapshot)
            assertEquals("PREMIUM",work.row.programmeNameSnapshot)
            repo.command(work.row.id,"START")
            now+=60000L
            val recovered=WorkRepository(db,"qa"){now}.get(work.row.id)!!
            assertEquals(60000L,recovered.elapsedMs)
            assertEquals("Ведущий PM1",recovered.row.pm1NameSnapshot)
        } finally { db.close() }
    }
    @Test fun externalWorkRequiresLeaderAndSuite() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val master=StaffRepository(db,"qa").create("Исполнитель")
            val repo=WorkRepository(db,"qa")
            try{
                repo.create(master.id,null,"PM","INDIVIDUAL_STEAM",45)
                fail("External work without PM1 must be rejected")
            }catch(_:IllegalArgumentException){}
            assertTrue(db.work().forMaster(master.id).isEmpty())
        }finally{db.close()}
    }
}
