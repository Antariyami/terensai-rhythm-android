package kz.terensai.rhythm.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * User-initiated offline full SQLite backup. Never exported automatically.
 * Contains personal reports, staff and sessions: share only with trusted storage.
 */
object ParitmBackupService {
    private const val DB="paritm-local.db"
    private const val STAGED="paritm-restore-staged.sqlite"
    private const val MARKER="paritm-restore-pending"
    private const val MAX_BYTES=100L*1024L*1024L

    private fun validate(file:File):Int{
        require(file.isFile&&file.length() in 1..MAX_BYTES){"Некорректный размер резервной копии"}
        val sql=SQLiteDatabase.openDatabase(file.absolutePath,null,
            SQLiteDatabase.OPEN_READONLY)
        return try{
            val integrity=sql.rawQuery("PRAGMA quick_check",null).use{c->
                if(c.moveToFirst())c.getString(0) else ""
            }
            require(integrity=="ok"){"Целостность базы нарушена"}
            val version=sql.rawQuery("PRAGMA user_version",null).use{c->
                if(c.moveToFirst())c.getInt(0) else 0
            }
            require(version in 8..11){"Неподдерживаемая версия базы ($version)"}
            val names=sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table'",null)
                .use{c->buildSet {while(c.moveToNext())add(c.getString(0))}}
            require(names.containsAll(setOf("sessions","session_events","checklist_runs",
                "work_entries","work_events","staff_members"))){
                "Это не полная база ПАРИТМ"
            }
            version
        } finally {sql.close()}
    }
    private fun sha256(file:File):String {
        val digest=MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use{stream->
            val bytes=ByteArray(8192)
            while(true){val n=stream.read(bytes);if(n<0)break;digest.update(bytes,0,n)}
        }
        return digest.digest().joinToString(""){"%02x".format(it)}
    }
    suspend fun export(context:Context,destination:Uri):String=withContext(Dispatchers.IO){
        val file=File(context.cacheDir,"paritm-export-${System.nanoTime()}.sqlite")
        try{
            val db=ParitmDatabase.get(context)
            db.openHelper.writableDatabase.execSQL(
                "VACUUM INTO '${file.absolutePath.replace("'","''")}'"
            )
            val version=validate(file)
            val digest=sha256(file)
            context.contentResolver.openOutputStream(destination,"w")?.use{out->
                file.inputStream().use{it.copyTo(out)}
            }?:error("Не удалось открыть выбранный файл")
            "Копия сохранена: версия Room $version, ${file.length()} байт, SHA-256 ${digest.take(12)}…"
        }finally{file.delete()}
    }

    /** Only stages a validated copy; live Room remains completely untouched. */
    suspend fun stageImport(context:Context,source:Uri):String=withContext(Dispatchers.IO){
        val temp=File(context.filesDir,"paritm-restore-import.tmp")
        val staging=File(context.filesDir,STAGED)
        val marker=File(context.filesDir,MARKER)
        try{
            context.contentResolver.openInputStream(source)?.use{input->
                temp.outputStream().use{out->
                    val bytes=ByteArray(8192);var count=0L
                    while(true){
                        val n=input.read(bytes);if(n<0)break
                        count+=n
                        require(count<=MAX_BYTES){"Копия превышает 100 МБ"}
                        out.write(bytes,0,n)
                    }
                }
            }?:error("Не удалось прочитать копию")
            val version=validate(temp)
            val hash=sha256(temp)
            Files.move(temp.toPath(),staging.toPath(),StandardCopyOption.REPLACE_EXISTING)
            // Validation alone does NOT arm restoration. Explicit user consent required.
            "Копия проверена: Room $version, SHA-256 ${hash.take(12)}…. После подтверждения приложение закроется; при следующем запуске восстановится база. Старая база будет сохранена отдельно для отката."
        }finally{temp.delete()}
    }

    fun armStagedRestore(context:Context) {
        val staging=File(context.filesDir,STAGED)
        validate(staging)
        File(context.filesDir,MARKER).writeText("PENDING",Charsets.UTF_8)
    }
    fun cancelStagedRestore(context:Context) {
        File(context.filesDir,STAGED).delete()
        File(context.filesDir,MARKER).delete()
    }

    /**
     * Called before Room singleton is ever opened in a FRESH process.
     * Old DB plus WAL/SHM are preserved as an internal rollback copy.
     */
    fun applyPendingAtColdStart(context:Context){
        val marker=File(context.filesDir,MARKER)
        if(!marker.exists())return
        synchronized(this){
            if(!marker.exists())return
            val staged=File(context.filesDir,STAGED)
            val original=context.getDatabasePath(DB)
            val previous=File(context.filesDir,"paritm-rollback-${System.currentTimeMillis()}")
            val suffixes=listOf("","-wal","-shm")
            try{
                validate(staged)
                previous.mkdirs()
                for(suffix in suffixes){
                    val old=File(original.absolutePath+suffix)
                    if(old.exists())Files.copy(old.toPath(),File(previous,DB+suffix).toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
                }
                original.parentFile?.mkdirs()
                val newFile=File(original.parentFile,"$DB.replacing")
                Files.copy(staged.toPath(),newFile.toPath(),StandardCopyOption.REPLACE_EXISTING)
                Files.move(newFile.toPath(),original.toPath(),
                    StandardCopyOption.REPLACE_EXISTING)
                File(original.absolutePath+"-wal").delete()
                File(original.absolutePath+"-shm").delete()
                marker.delete();staged.delete()
            }catch(e:Exception){
                // Roll back all original files. Do not attempt destructive migration.
                for(suffix in suffixes){
                    val old=File(previous,DB+suffix)
                    val target=File(original.absolutePath+suffix)
                    if(old.exists())Files.copy(old.toPath(),target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING)
                }
                marker.delete()
                throw IllegalStateException("Не удалось безопасно восстановить базу. Старые данные сохранены.",e)
            }
        }
    }
}
