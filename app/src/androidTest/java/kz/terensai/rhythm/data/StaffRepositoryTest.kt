package kz.terensai.rhythm.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StaffRepositoryTest {
    @Test fun profileAssignmentAndHandoverAreDurable() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try {
            val repo=StaffRepository(db,"test-install"){10000L}
            val a=repo.create("Мастер А",asMine=true)
            val b=repo.create("Мастер Б")
            assertEquals(a.id,repo.mine()?.id)
            val row=SessionRow(
                id="session-test",templateId="t",templateTitle="Танысу",
                startedAtMs=1000L,plannedDurationMs=180000L,snapshotJson="{}")
            db.records().startSession(row)
            repo.appointPm1("session-test",a.id)
            assertEquals(a.id,repo.leader("session-test")?.id)
            repo.appointPm1("session-test",b.id)
            assertEquals(b.id,repo.leader("session-test")?.id)
            val history=repo.assignments("session-test")
            assertEquals(2,history.size)
            assertNotNull(history[0].endedAtMs)
            assertNull(history[1].endedAtMs)
            repo.appointPm1("session-test",b.id)
            assertEquals(2,repo.assignments("session-test").size)
        } finally {db.close()}
    }
}
