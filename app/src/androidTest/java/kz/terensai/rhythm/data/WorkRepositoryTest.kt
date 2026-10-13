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
class WorkRepositoryTest {
    @Test fun pauseResumeCompleteAndRestoreAreIndependentOfProgrammeTimer()= runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var clock=10000L
        try {
            val staff=StaffRepository(db,"qa"){clock}
            val me=staff.create("Тестовый мастер",asMine=true)
            val repo=WorkRepository(db,"qa"){clock}
            val created=repo.create(me.id,null,"PM","INDIVIDUAL_STEAM",45)
            assertEquals("READY",created.state)
            repo.command(created.row.id,"START")
            clock+=120000L
            assertEquals(120000L,repo.get(created.row.id)!!.elapsedMs)
            repo.command(created.row.id,"PAUSE")
            clock+=300000L
            assertEquals(120000L,repo.get(created.row.id)!!.elapsedMs)
            repo.command(created.row.id,"RESUME")
            clock+=60000L
            repo.command(created.row.id,"COMPLETE")
            val recovered=WorkRepository(db,"qa"){clock}.get(created.row.id)!!
            assertEquals("COMPLETED",recovered.state)
            assertEquals(180000L,recovered.elapsedMs)
            assertEquals(listOf("START","PAUSE","RESUME","COMPLETE"),recovered.events.map {it.kind})
            try{
                repo.command(created.row.id,"START")
                fail("A completed work entry must remain complete")
            } catch(_:IllegalStateException){}
        } finally{db.close()}
    }
}
