package kz.terensai.rhythm.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** No destructive migration: future schema upgrades must provide explicit Migration objects. */
@Database(
    entities = [ProgramRow::class, SessionRow::class, EventRow::class],
    version = 2,
    exportSchema = true
)
abstract class ParitmDatabase : RoomDatabase() {
    abstract fun records(): ParitmDao

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

        @Volatile private var instance: ParitmDatabase? = null

        fun get(context: Context): ParitmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, ParitmDatabase::class.java,
                    "paritm-local.db"
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
