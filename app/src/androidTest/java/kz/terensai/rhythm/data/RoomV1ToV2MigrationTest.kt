package kz.terensai.rhythm.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kz.terensai.rhythm.data.room.ParitmDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** An actual file opened as Room v2 after creating the original v1 SQL tables. */
@RunWith(AndroidJUnit4::class)
class RoomV1ToV2MigrationTest {
    @Test fun migrationKeepsLegacySessionAndEventWithNewDefaultColumns() {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="b23-migration-${UUID.randomUUID()}.db"
        val file=context.getDatabasePath(name)
        file.parentFile?.mkdirs()
        val old=SQLiteDatabase.openOrCreateDatabase(file,null)
        try {
            old.execSQL("""CREATE TABLE IF NOT EXISTS `programs` (
                `id` TEXT NOT NULL, `title` TEXT NOT NULL,
                `plannedDurationMs` INTEGER NOT NULL,
                `immutableStagesJson` TEXT NOT NULL,
                `updatedAtMs` INTEGER NOT NULL, PRIMARY KEY(`id`))""".trimIndent())
            old.execSQL("""CREATE TABLE IF NOT EXISTS `sessions` (
                `id` TEXT NOT NULL, `templateId` TEXT NOT NULL,
                `templateTitle` TEXT NOT NULL, `startedAtMs` INTEGER NOT NULL,
                `plannedDurationMs` INTEGER NOT NULL, `snapshotJson` TEXT NOT NULL,
                `legacyJson` TEXT, `finishedAtMs` INTEGER,
                PRIMARY KEY(`id`))""".trimIndent())
            old.execSQL("""CREATE TABLE IF NOT EXISTS `session_events` (
                `id` TEXT NOT NULL, `sessionId` TEXT NOT NULL,
                `stageId` TEXT NOT NULL, `kind` TEXT NOT NULL,
                `atMs` INTEGER NOT NULL, `referenceEventId` TEXT,
                PRIMARY KEY(`id`),
                FOREIGN KEY(`sessionId`) REFERENCES `sessions`(`id`)
                ON UPDATE NO ACTION ON DELETE CASCADE)""".trimIndent())
            old.execSQL("CREATE INDEX IF NOT EXISTS `index_session_events_sessionId` ON `session_events` (`sessionId`)")
            old.execSQL("""INSERT INTO sessions (
                id, templateId, templateTitle, startedAtMs, plannedDurationMs,
                snapshotJson, legacyJson, finishedAtMs
            ) VALUES ('old-session','t','Legacy',1000000,600000,
              '{"schemaVersion":1,"id":"t","title":"Legacy","plannedDurationMs":600000,"stages":[]}',
              NULL,NULL)""".trimIndent())
            old.execSQL("""INSERT INTO session_events (id,sessionId,stageId,kind,atMs,referenceEventId)
              VALUES ('old-event','old-session','one','START',1000001,NULL)""")
            old.version=1
        } finally { old.close() }
        val modern=Room.databaseBuilder(context,ParitmDatabase::class.java,name)
            .addMigrations(ParitmDatabase.MIGRATION_1_2)
            .allowMainThreadQueries() // only for this instrumentation test
            .build()
        try {
            val session=modern.records().session("old-session")!!
            assertEquals("legacy-local",session.originClientId)
            assertEquals(0L,session.localRevision)
            assertEquals(1,modern.records().events(session.id).size)
            assertEquals(1,modern.records().activeSessionCount())
        } finally {
            modern.close()
            context.deleteDatabase(name)
        }
    }
}
