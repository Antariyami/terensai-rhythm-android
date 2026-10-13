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
class WorkRecoveryRegressionTest {
    @Test fun pausedAndRunningEntriesReturnAfterRecreatingRepository() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java).build()
        var at=1000L
        try {
            val staff=StaffRepository(db,"test"){at}
            val master=staff.create("Тестовый пармастер",asMine=true)
            val leader=staff.create("Ведущий PM1")
            val first=WorkRepository(db,"test"){at}.create(master.id,null,"PM","INDIVIDUAL_STEAM",45,
                externalPm1Id=leader.id,externalSuite="4 · Амандық")
            val repo=WorkRepository(db,"test"){at}
            repo.command(first.row.id,"START")
            at+=90000
            repo.command(first.row.id,"PAUSE")
            val second=repo.create(master.id,null,"PM","COLLECTIVE_STEAM",15,
                externalPm1Id=leader.id,externalSuite="1 · Көркемдік")
            val reopened=WorkRepository(db,"test"){at}.all(master.id)
            assertEquals(2,reopened.size)
            assertEquals("PAUSED",reopened.first{it.row.id==first.row.id}.state)
            assertEquals(90000L,reopened.first{it.row.id==first.row.id}.elapsedMs)
            assertEquals("READY",reopened.first{it.row.id==second.row.id}.state)
        }finally{db.close()}
    }
}
