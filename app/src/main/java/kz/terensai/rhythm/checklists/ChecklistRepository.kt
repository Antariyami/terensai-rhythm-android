package kz.terensai.rhythm.checklists

import androidx.room.withTransaction
import kz.terensai.rhythm.data.room.ChecklistRunRow
import kz.terensai.rhythm.data.room.ChecklistTemplateRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Templates are editable; each run is anchored to the full immutable version
 * that existed when it started. No migration of live session event/works data.
 */
class ChecklistRepository(
    private val db:ParitmDatabase,
    private val now:()->Long={System.currentTimeMillis()}
) {
    private val dao=db.checklists()

    suspend fun ensureDefaults():Unit=withContext(Dispatchers.IO){
        db.withTransaction {
            ChecklistCatalog.defaults().forEach{
                dao.seed(ChecklistTemplateRow(it.id,it.type,it.version,
                    ChecklistJson.encode(it),now()))
            }
        }
    }
    suspend fun templates():List<ChecklistTemplate> = withContext(Dispatchers.IO){
        dao.templates().map{ChecklistJson.decode(it.json)}
    }
    suspend fun template(id:String):ChecklistTemplate?=withContext(Dispatchers.IO){
        dao.template(id)?.let{ChecklistJson.decode(it.json)}
    }

    suspend fun saveTemplate(candidate:ChecklistTemplate):ChecklistTemplate=withContext(Dispatchers.IO){
        candidate.validated()
        db.withTransaction {
            val original=dao.template(candidate.id)
            val version=if(original==null) 1 else original.version+1
            val updated=candidate.copy(version=version)
            dao.putTemplate(ChecklistTemplateRow(updated.id,updated.type,
                version,ChecklistJson.encode(updated),now()))
            updated
        }
    }
    /** Import clones identity so that no existing template or old run is overwritten. */
    suspend fun importAsNew(json:String):ChecklistTemplate {
        val parsed=ChecklistJson.decode(json)
        val copy=parsed.copy(id=UUID.randomUUID().toString(),version=1,
            title=(parsed.title+" · импорт").take(120))
        return saveTemplate(copy)
    }
    suspend fun export(id:String):String=withContext(Dispatchers.IO){
        dao.template(id)?.json?:error("Шаблон не найден")
    }

    suspend fun activeRun(templateId:String):ChecklistRunRow?=withContext(Dispatchers.IO){
        dao.active(templateId)
    }
    suspend fun latestRun(templateId:String):ChecklistRunRow?=withContext(Dispatchers.IO){
        dao.latest(templateId)
    }
    suspend fun run(id:String):ChecklistRunRow?=withContext(Dispatchers.IO){
        dao.run(id)
    }
    suspend fun newRun(templateId:String):ChecklistRunRow=withContext(Dispatchers.IO){
        db.withTransaction {
            val template=dao.template(templateId)?:error("Шаблон не найден")
            val current=now().coerceAtLeast(1L)
            val run=ChecklistRunRow(UUID.randomUUID().toString(),templateId,
                template.version,template.json,"[]","{}","IN_PROGRESS",
                current,current,null)
            dao.createRun(run)
            run
        }
    }
    suspend fun toggle(runId:String,itemId:String):ChecklistRunRow=withContext(Dispatchers.IO){
        db.withTransaction {
            val run=dao.run(runId)?:error("Проверка не найдена")
            check(run.status=="IN_PROGRESS"){"Завершённую проверку нельзя изменить"}
            val snapshot=ChecklistJson.decode(run.snapshotJson)
            check(snapshot.blocks.any{block->block.items.any{it.id==itemId}}) {
                "Пункт не принадлежит этому шаблону"
            }
            val checked=ChecklistJson.decodeAnswers(run.checkedJson).toMutableSet()
            if(!checked.add(itemId))checked.remove(itemId)
            val updated=run.copy(checkedJson=ChecklistJson.encodeAnswers(checked),
                updatedAtMs=now())
            check(dao.updateRun(updated)==1)
            updated
        }
    }
    suspend fun saveNote(runId:String,key:String,value:String):ChecklistRunRow=withContext(Dispatchers.IO){
        require(key.length in 1..120 && value.length<=2000)
        db.withTransaction {
            val run=dao.run(runId)?:error("Проверка не найдена")
            check(run.status=="IN_PROGRESS"){"Проверка уже завершена"}
            val snapshot=ChecklistJson.decode(run.snapshotJson)
            check(key in setOf("summary","important","heat","remarks","pm1") ||
                snapshot.blocks.any{it.id==key}){"Недопустимое поле заметки"}
            val notes=ChecklistJson.decodeNotes(run.notesJson).toMutableMap()
            notes[key]=value
            val updated=run.copy(notesJson=ChecklistJson.encodeNotes(notes),updatedAtMs=now())
            check(dao.updateRun(updated)==1)
            updated
        }
    }
    /**
     * READY and DONE require all items; NOTES and CRITICAL allow explicit open
     * defects. Critical reception requires a meaningful written handover.
     */
    suspend fun complete(runId:String,decision:String,summary:String=""):ChecklistRunRow=
        withContext(Dispatchers.IO){
            require(decision in setOf("READY","NOTES","CRITICAL","DONE"))
            db.withTransaction {
                val run=dao.run(runId)?:error("Проверка не найдена")
                check(run.status=="IN_PROGRESS"){"Проверка уже завершена"}
                val snapshot=ChecklistJson.decode(run.snapshotJson)
                val checked=ChecklistJson.decodeAnswers(run.checkedJson)
                check(decision!="DONE" || snapshot.type!="RECEPTION"){"Приёмка требует итогового решения"}
                check(decision=="DONE" || snapshot.type=="RECEPTION"){"Решения PM1 только для приёмки"}
                if(decision in setOf("READY","DONE"))check(checked.size==snapshot.totalItems) {
                    "Для статуса «Готово» отметь все пункты"
                }
                if(decision=="CRITICAL")require(summary.trim().length>=5) {
                    "Запиши критические замечания и кому передано"
                }
                val notes=ChecklistJson.decodeNotes(run.notesJson).toMutableMap()
                if(summary.isNotBlank())notes["summary"]=summary.take(2000)
                val at=now()
                val updated=run.copy(
                    status=decision,notesJson=ChecklistJson.encodeNotes(notes),
                    completedAtMs=at,updatedAtMs=at
                )
                check(dao.updateRun(updated)==1)
                updated
            }
        }
}
