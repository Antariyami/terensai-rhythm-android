package kz.terensai.rhythm.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** No destructive migration: future schema upgrades must provide explicit Migration objects. */
@Database(
    entities = [ProgramRow::class, SessionRow::class, EventRow::class, StaffRow::class, AssignmentRow::class],
    version = 3,
    exportSchema = true
)
abstract class ParitmDatabase : RoomDatabase() {
    abstract fun records(): ParitmDao
    abstract fun staff(): StaffDao

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

        @Volatile private var instance: ParitmDatabase? = null

        fun get(context: Context): ParitmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, ParitmDatabase::class.java,
                    "paritm-local.db"
                ).addMigrations(MIGRATION_1_2,MIGRATION_2_3).build().also { instance = it }
            }
    }
}
