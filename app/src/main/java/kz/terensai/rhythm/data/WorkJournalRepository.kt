package kz.terensai.rhythm.data

import androidx.room.withTransaction
import kz.terensai.rhythm.data.room.WorkCorrectionRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class JournalLine(
    val work: WorkView,
    val corrections: List<WorkCorrectionRow>
) {
    /** Measured time is always retained; adjustments affect only the report. */
    val reportedMinutes: Long
        get() = corrections.lastOrNull()?.correctedMinutes
            ?: ((work.elapsedMs + 30000L) / 60000L)
    val isAdjusted:Boolean get() = corrections.isNotEmpty()
}

class WorkJournalRepository(
    private val db:ParitmDatabase,
    private val origin:String,
    private val clock:()->Long={System.currentTimeMillis()}
){
    private val repo=WorkRepository(db,origin,clock)
    suspend fun all(staffId:String):List<JournalLine> = withContext(Dispatchers.IO) {
        repo.all(staffId).map {work ->
            JournalLine(work,db.work().corrections(work.row.id))
        }
    }

    /** Correction never mutates or replaces the original START/PAUSE/RESUME/COMPLETE chain. */
    suspend fun correctCompleted(workId:String,masterId:String,minutes:Long,reason:String):JournalLine =
        withContext(Dispatchers.IO){
            require(minutes in 0L..1440L){"Укажи минуты от 0 до 1440"}
            val cleanReason=reason.trim().take(300)
            require(cleanReason.length >= 5){"Укажи причину исправления (не менее 5 символов)"}
            db.withTransaction {
                val row=db.work().entry(workId) ?: error("Запись не найдена")
                require(row.staffId==masterId){"Можно исправлять только собственную запись"}
                val work=repo.get(workId) ?: error("Запись не найдена")
                check(work.state=="COMPLETED"){"Исправлять факт можно только после завершения"}
                val before=db.work().corrections(workId)
                val original=(work.elapsedMs + 30000L)/60000L
                val current=before.lastOrNull()?.correctedMinutes ?: original
                check(current!=minutes){"Новое значение совпадает с текущим"}
                val stamp=clock().coerceAtLeast(before.lastOrNull()?.createdAtMs?:row.createdAtMs)
                db.work().addCorrection(WorkCorrectionRow(
                    UUID.randomUUID().toString(),workId,stamp,current,minutes,cleanReason
                ))
                check(db.work().touch(workId)==1)
                JournalLine(repo.get(workId)!!,db.work().corrections(workId))
            }
        }
}

object WorkJournalLabels {
    fun procedure(key:String)=when(key){
        "INDIVIDUAL_STEAM"->"Индивидуальное парение"
        "COLLECTIVE_STEAM"->"Коллективное парение"
        "MASSAGE"->"Массаж"
        "PEELING"->"Пилинг"
        "WASHING"->"Помывка веником"
        else->"Другая процедура"
    }
    fun state(state:String)=when(state) {
        "READY"->"Ожидает"
        "RUNNING"->"Идёт"
        "PAUSED"->"Пауза"
        else->"Завершена"
    }
}
