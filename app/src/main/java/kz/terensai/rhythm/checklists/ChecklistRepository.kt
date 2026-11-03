package kz.terensai.rhythm.checklists

import androidx.room.withTransaction
import kz.terensai.rhythm.data.room.ChecklistRunRow
import kz.terensai.rhythm.data.room.ChecklistTemplateRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
            ChecklistCatalog.defaults().forEach{candidate->
                val saved=dao.template(candidate.id)
                if(saved==null){
                    dao.seed(ChecklistTemplateRow(candidate.id,candidate.type,candidate.version,
                        ChecklistJson.encode(candidate),now()))
                }else if(candidate.type=="DUTY" && saved.version in 1..2 &&
                    saved.json==ChecklistJson.encode(
                        if(saved.version==1) ChecklistCatalog.dutyV1()
                        else ChecklistCatalog.dutyV2()
                    )){
                    // Upgrade the untouched built-in template only. Previously started
                    // runs keep their original snapshot; user-edited templates are retained.
                    dao.putTemplate(ChecklistTemplateRow(candidate.id,candidate.type,
                        candidate.version,ChecklistJson.encode(candidate),now()))
                }
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
    suspend fun activeForStaff(templateId:String,staffId:String):ChecklistRunRow?=
        withContext(Dispatchers.IO){dao.activeForStaff(templateId,staffId)}
    suspend fun latestRun(templateId:String):ChecklistRunRow?=withContext(Dispatchers.IO){
        dao.latest(templateId)
    }
    suspend fun allRuns():List<ChecklistRunRow> = withContext(Dispatchers.IO){
        dao.allRuns()
    }
    suspend fun run(id:String):ChecklistRunRow?=withContext(Dispatchers.IO){
        dao.run(id)
    }
    suspend fun newRun(templateId:String,staffId:String?=null):ChecklistRunRow=
        withContext(Dispatchers.IO){
        db.withTransaction {
            val template=dao.template(templateId)?:error("Шаблон не найден")
            val model=ChecklistJson.decode(template.json)
            val staff=if(model.type=="DUTY"){
                require(!staffId.isNullOrBlank()){"Выбери профиль дежурного"}
                db.staff().member(staffId)?.takeUnless{it.archived}
                    ?:error("Профиль дежурного не найден")
            }else null
            val current=now().coerceAtLeast(1L)
            val date=if(staff!=null) SimpleDateFormat("yyyy-MM-dd",Locale.ROOT)
                .apply{timeZone=TimeZone.getTimeZone("Asia/Almaty")}
                .format(Date(current)) else null
            val run=ChecklistRunRow(UUID.randomUUID().toString(),templateId,
                template.version,template.json,"[]","{}","IN_PROGRESS",
                current,current,null,staff?.id,staff?.displayName,date)
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
    /** Atomic bulk update, scoped strictly to the snapshot block. */
    suspend fun allNormal(runId:String,blockId:String):ChecklistRunRow=withContext(Dispatchers.IO){
        db.withTransaction {
            val run=dao.run(runId)?:error("Проверка не найдена")
            check(run.status=="IN_PROGRESS"){"Завершённую проверку нельзя изменять"}
            val snapshot=ChecklistJson.decode(run.snapshotJson)
            val block=snapshot.blocks.firstOrNull{it.id==blockId}
                ?:error("Блок отсутствует в сохранённом шаблоне")
            val checked=ChecklistJson.decodeAnswers(run.checkedJson).toMutableSet()
            checked.addAll(block.items.map{it.id})
            val notes=ChecklistJson.decodeNotes(run.notesJson).toMutableMap()
            if(snapshot.type=="DUTY" && blockId.startsWith("duty-suite-")){
                val key="temperature:"+blockId
                if(notes[key].isNullOrBlank()){
                    notes[key]="200"
                    notes["temperature-proposed:"+blockId]="yes"
                }
            }
            val updated=run.copy(checkedJson=ChecklistJson.encodeAnswers(checked),
                notesJson=ChecklistJson.encodeNotes(notes),updatedAtMs=now())
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
            if(key=="arrivalTime")require(value.isBlank()||
                Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]").matches(value)){
                "Время прибытия запиши в формате ЧЧ:ММ"
            }
            if(key.startsWith("temperature:") && value.isNotBlank())require(
                (value.toIntOrNull()?:-1) in 0..400){"Укажи температуру числом от 0 до 400"}
            check(key in setOf("summary","important","heat","remarks","pm1") ||
                (snapshot.type=="DUTY" && key=="arrivalTime") ||
                snapshot.blocks.any{it.id==key} ||
                (snapshot.type=="DUTY" && key.startsWith("temperature:") &&
                    snapshot.blocks.any{key=="temperature:"+it.id &&
                        it.id.startsWith("duty-suite-")})){"Недопустимое поле заметки"}
            val notes=ChecklistJson.decodeNotes(run.notesJson).toMutableMap()
            notes[key]=value
            if(key.startsWith("temperature:")){
                notes.remove("temperature-proposed:"+key.removePrefix("temperature:"))
            }
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
                check(decision=="DONE" || snapshot.type in setOf("RECEPTION","DUTY")){"Решение недоступно для этого списка"}
                if(decision in setOf("READY","DONE"))check(checked.size==snapshot.totalItems) {
                    "Для статуса «Готово» отметь все пункты"
                }
                if(snapshot.type=="DUTY" && decision=="DONE"){
                    val notes=ChecklistJson.decodeNotes(run.notesJson)
                    check(snapshot.blocks.filter{it.id.startsWith("duty-suite-")}.all{b->
                        notes["temperature-proposed:"+b.id]!="yes" &&
                            !notes["temperature:"+b.id].isNullOrBlank()
                    }){"Подтверди измеренную температуру каждой парной перед статусом «Готово»"}
                }
                if(snapshot.type=="DUTY")check(decision in setOf("DONE","NOTES")){
                    "Дежурство: выбери завершение или сохранение с замечаниями"
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
