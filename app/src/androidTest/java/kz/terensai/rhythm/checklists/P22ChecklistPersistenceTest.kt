package kz.terensai.rhythm.checklists

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class P22ChecklistPersistenceTest {
    @Test fun persistedRunKeepsCheckedStateAcrossRepositoryRestartAndNextShiftResets()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try{
            val repo=ChecklistRepository(db){1_800_000_000_000L}
            repo.ensureDefaults()
            assertEquals(2,repo.templates().size)
            val bag=ChecklistCatalog.bag()
            val first=repo.newRun(bag.id)
            val firstId=bag.blocks[0].items[0].id
            repo.toggle(first.id,firstId)
            val rebuilt=ChecklistRepository(db){1_800_000_000_500L}
            val saved=rebuilt.activeRun(bag.id)!!
            assertEquals(first.id,saved.id)
            assertTrue(firstId in ChecklistJson.decodeAnswers(saved.checkedJson))
            val second=rebuilt.newRun(bag.id)
            assertTrue(ChecklistJson.decodeAnswers(second.checkedJson).isEmpty())
            assertTrue(firstId in ChecklistJson.decodeAnswers(rebuilt.run(first.id)!!.checkedJson))
        }finally{db.close()}
    }
    @Test fun editedTemplateDoesNotRewriteOldRunAndImportClonesIdentity()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try{
            val repo=ChecklistRepository(db){1_800_000_000_000L}
            repo.ensureDefaults()
            val old=repo.newRun("pm1-reception")
            val source=repo.template("pm1-reception")!!
            val updated=repo.saveTemplate(ChecklistEditing.moveBlock(source,0,1))
            assertEquals(2,updated.version)
            assertEquals(source.blocks.first().id,
                ChecklistJson.decode(old.snapshotJson).blocks.first().id)
            assertEquals(source.blocks[1].id,updated.blocks.first().id)
            val clone=repo.importAsNew(repo.export(updated.id))
            assertNotEquals(source.id,clone.id)
            assertEquals(3,repo.templates().size)
            assertEquals(61,clone.totalItems)
        }finally{db.close()}
    }
    @Test fun receptionCriticalDecisionNeedsWrittenDefectAndRemainsAuditable()=runBlocking{
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val db=Room.inMemoryDatabaseBuilder(ctx,ParitmDatabase::class.java)
            .allowMainThreadQueries().build()
        try{
            val repo=ChecklistRepository(db){1_800_000_000_000L}
            repo.ensureDefaults()
            val run=repo.newRun("pm1-reception")
            try{repo.complete(run.id,"CRITICAL","bad");fail("Expected summary validation")}
            catch(_:IllegalArgumentException){}
            val checked=repo.toggle(run.id,"pm1-1-1")
            val ended=repo.complete(run.id,"CRITICAL","Парная не готова, передано PM1")
            assertEquals("CRITICAL",ended.status)
            assertEquals(checked.checkedJson,ended.checkedJson)
            assertEquals("Парная не готова, передано PM1",
                ChecklistJson.decodeNotes(ended.notesJson)["summary"])
            assertNotNull(ended.completedAtMs)
        }finally{db.close()}
    }
}
