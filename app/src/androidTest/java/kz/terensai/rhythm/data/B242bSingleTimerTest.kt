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
class B242bSingleTimerTest {
    @Test fun newPersonalTimerPausesOldOneAndSurvivesRepositoryReopen()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        var tick=100000L
        try{
            val staff=StaffRepository(db,"qa"){tick}
            staff.ensureStarterMasters()
            val andrey=staff.mine()!!
            val leader=staff.all().first{it.displayName=="Олжас"}
            assertEquals("Андрей",andrey.displayName)
            staff.ensureStarterMasters()
            assertEquals(2,staff.all().size)
            val repo=WorkRepository(db,"qa"){tick}
            val a=repo.create(andrey.id,null,"PM","INDIVIDUAL_STEAM",45,
                externalPm1Id=leader.id,externalSuite="4 · Амандық")
            val b=repo.create(andrey.id,null,"SPA","MASSAGE",30,
                externalPm1Id=leader.id,externalSuite="5 · Сәттілік")
            repo.command(a.row.id,"START")
            tick+=60000L
            repo.command(b.row.id,"START")
            val after=WorkRepository(db,"qa"){tick}.all(andrey.id)
            assertEquals(1,after.count{it.state=="RUNNING"})
            assertEquals("PAUSED",after.first{it.row.id==a.row.id}.state)
            assertEquals(60000L,after.first{it.row.id==a.row.id}.elapsedMs)
            tick+=30000L
            repo.command(a.row.id,"RESUME")
            val switched=WorkRepository(db,"qa"){tick}.all(andrey.id)
            assertEquals(1,switched.count{it.state=="RUNNING"})
            assertEquals("PAUSED",switched.first{it.row.id==b.row.id}.state)
            assertEquals(30000L,switched.first{it.row.id==b.row.id}.elapsedMs)
            assertEquals(listOf("START","PAUSE","RESUME"),repo.get(a.row.id)!!.events.map{it.kind})
            repo.command(a.row.id,"COMPLETE")
            val reopened=WorkRepository(db,"qa"){tick}.all(andrey.id)
            assertEquals(0,reopened.count{it.state=="RUNNING"})
            assertEquals("COMPLETED",reopened.first{it.row.id==a.row.id}.state)
            assertEquals("PAUSED",reopened.first{it.row.id==b.row.id}.state)
        }finally{db.close()}
    }

    @Test fun userChosenProfileIsNotOverwrittenBySeed()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(context,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try{
            val staff=StaffRepository(db,"qa")
            val custom=staff.create("Другой мастер",asMine=true)
            staff.ensureStarterMasters()
            assertEquals(custom.id,staff.mine()!!.id)
            assertEquals(3,staff.all().size)
        }finally{db.close()}
    }
}
