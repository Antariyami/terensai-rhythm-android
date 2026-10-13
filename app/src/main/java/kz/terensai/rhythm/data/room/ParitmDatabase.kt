package kz.terensai.rhythm.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** No destructive migration: future schema upgrades must provide explicit Migration objects. */
@Database(
    entities = [ProgramRow::class, SessionRow::class, EventRow::class, StaffRow::class, AssignmentRow::class, WorkRow::class, WorkEventRow::class, WorkCorrectionRow::class],
    version = 6,
    exportSchema = true
)
abstract class ParitmDatabase : RoomDatabase() {
    abstract fun records(): ParitmDao
    abstract fun staff(): StaffDao
    abstract fun work(): WorkDao

    companion object {
        /**
         * Additive migration. Room 1 journal and immutable snapshots stay in place.
         * Never enable fallbackToDestructiveMigration for session data.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sessions ADD COLUMN originClientId TEXT NOT NULL DEFAULT 'legacy-local'")
                db.execSQL("ALTER TABLE sessions ADD COLUMN localRevision INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sessions ADD COLUMN modifiedAtMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS staff_members (
                    id TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL,
                    specialties TEXT NOT NULL, isMyProfile INTEGER NOT NULL,
                    archived INTEGER NOT NULL, originClientId TEXT NOT NULL,
                    updatedAtMs INTEGER NOT NULL)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_staff_members_displayName ON staff_members(displayName)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS session_assignments (
                    id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL,
                    staffId TEXT NOT NULL, role TEXT NOT NULL, startedAtMs INTEGER NOT NULL,
                    endedAtMs INTEGER, originClientId TEXT NOT NULL,updatedAtMs INTEGER NOT NULL,
                    FOREIGN KEY(sessionId) REFERENCES sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(staffId) REFERENCES staff_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_assignments_sessionId ON session_assignments(sessionId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_session_assignments_staffId ON session_assignments(staffId)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3,4) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS work_entries (
                    id TEXT NOT NULL PRIMARY KEY, sessionId TEXT,staffId TEXT NOT NULL,
                    role TEXT NOT NULL,procedureType TEXT NOT NULL,
                    plannedMs INTEGER NOT NULL,createdAtMs INTEGER NOT NULL,
                    originClientId TEXT NOT NULL,revision INTEGER NOT NULL,
                    FOREIGN KEY(staffId) REFERENCES staff_members(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_entries_staffId ON work_entries(staffId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_entries_sessionId ON work_entries(sessionId)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS work_events (
                    id TEXT NOT NULL PRIMARY KEY,workId TEXT NOT NULL,
                    kind TEXT NOT NULL,atMs INTEGER NOT NULL,
                    FOREIGN KEY(workId) REFERENCES work_entries(id) ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_events_workId ON work_events(workId)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4,5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_entries ADD COLUMN pm1StaffId TEXT")
                db.execSQL("ALTER TABLE work_entries ADD COLUMN pm1NameSnapshot TEXT")
                db.execSQL("ALTER TABLE work_entries ADD COLUMN suiteNameSnapshot TEXT")
                db.execSQL("ALTER TABLE work_entries ADD COLUMN programmeNameSnapshot TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5,6) {
            override fun migrate(db:SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS work_corrections (
                    id TEXT NOT NULL PRIMARY KEY,
                    workId TEXT NOT NULL,
                    createdAtMs INTEGER NOT NULL,
                    priorMinutes INTEGER NOT NULL,
                    correctedMinutes INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    FOREIGN KEY(workId) REFERENCES work_entries(id)
                    ON UPDATE NO ACTION ON DELETE CASCADE)""")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_corrections_workId ON work_corrections(workId)")
            }
        }

        @Volatile private var instance: ParitmDatabase? = null

        fun get(context: Context): ParitmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, ParitmDatabase::class.java,
                    "paritm-local.db"
                ).addMigrations(MIGRATION_1_2,MIGRATION_2_3,MIGRATION_3_4,MIGRATION_4_5,MIGRATION_5_6).build().also { instance = it }
            }
    }
}
