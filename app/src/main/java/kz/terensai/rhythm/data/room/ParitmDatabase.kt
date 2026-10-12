package kz.terensai.rhythm.data.room

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/** No destructive migration: future schema upgrades must provide explicit Migration objects. */
@Database(
    entities = [ProgramRow::class, SessionRow::class, EventRow::class],
    version = 1,
    exportSchema = true
)
abstract class ParitmDatabase : RoomDatabase() {
    abstract fun records(): ParitmDao

    companion object {
        @Volatile private var instance: ParitmDatabase? = null

        fun get(context: Context): ParitmDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext, ParitmDatabase::class.java,
                    "paritm-local.db"
                ).build().also { instance = it }
            }
    }
}
