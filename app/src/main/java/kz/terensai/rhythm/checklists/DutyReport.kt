package kz.terensai.rhythm.checklists

import kz.terensai.rhythm.data.room.ChecklistRunRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Text prepared locally. The user decides whether and where it is shared. */
object DutyReport {
    fun render(run:ChecklistRunRow,summaryOverride:String?=null):String {
        val template=ChecklistJson.decode(run.snapshotJson)
        require(template.type=="DUTY"){"Отчёт дежурного доступен только для DUTY"}
        val checked=ChecklistJson.decodeAnswers(run.checkedJson)
        val notes=ChecklistJson.decodeNotes(run.notesJson)
        val date=run.dutyDate?:"Дата не указана"
        val time=SimpleDateFormat("HH:mm",Locale("ru","RU")).apply{
            timeZone=TimeZone.getTimeZone("Asia/Almaty")
        }.format(Date(run.startedAtMs))
        val summary=summaryOverride?:notes["summary"].orEmpty()
        val arrival=notes["arrivalTime"]?.takeIf{it.isNotBlank()}?:"не указано"
        val total=template.totalItems
        val completed=template.blocks.sumOf{b->b.items.count{it.id in checked}}
        return buildString {
            appendLine("ПАРИТМ · Чек-лист дежурного")
            appendLine("Дата: $date · Начало проверки: $time")
            appendLine("Дежурный: ${run.staffNameSnapshot?:"Не указан"}")
            appendLine("Фактическое время прибытия (со слов мастера): $arrival")
            appendLine("Проверено: $completed из $total")
            appendLine("Статус: "+when(run.status){
                "DONE"->"завершено"
                "NOTES"->"завершено с замечаниями"
                else->"в процессе"
            })
            template.blocks.forEach { block ->
                appendLine()
                appendLine(block.title)
                block.items.forEach { item ->
                    appendLine((if(item.id in checked)"☑ " else "☐ ")+item.title)
                }
                notes["temperature:"+block.id]?.takeIf{it.isNotBlank()}?.let{
                    appendLine("Температура: $it °C"+if(notes["temperature-proposed:"+block.id]=="yes")
                        " (шаблонное значение, не подтверждено измерением)" else " (внесено мастером)")
                }
                notes[block.id]?.takeIf{it.isNotBlank()}?.let{
                    appendLine("Замечания: $it")
                }
            }
            if(summary.isNotBlank()){
                appendLine()
                appendLine("Итог / кому передано: $summary")
            }
            appendLine()
            append("Отчёт составлен по отметкам мастера; не является подтверждением технического допуска.")
        }
    }

    /** One-line-per-suite status; copy/share never mutates the Run. */
    fun renderShort(run:ChecklistRunRow):String {
        val template=ChecklistJson.decode(run.snapshotJson)
        require(template.type=="DUTY")
        val checked=ChecklistJson.decodeAnswers(run.checkedJson)
        val notes=ChecklistJson.decodeNotes(run.notesJson)
        return buildString {
            appendLine("ПАРИТМ · Краткий отчёт дежурного")
            appendLine("Дата: ${run.dutyDate?:"не указана"}")
            appendLine("Мастер: ${run.staffNameSnapshot?:"не указан"}")
            appendLine("Прибыл: ${notes["arrivalTime"]?.takeIf{it.isNotBlank()}?:"не указано"}")
            template.blocks.filter{it.id.startsWith("duty-suite-")}.forEach{b->
                val suiteChecked=b.items.all{it.id in checked} &&
                    notes["temperature-proposed:"+b.id]!="yes" &&
                    !notes["temperature:"+b.id].isNullOrBlank()
                val inventory=b.items.firstOrNull{it.title.contains("Инвентарь размещён")}
                    ?.let{it.id in checked}?:false
                val temp=notes["temperature:"+b.id]?.takeIf{it.isNotBlank()}
                val proposed=notes["temperature-proposed:"+b.id]=="yes"
                appendLine("${b.title}: ${if(suiteChecked)"☑" else "☐"} парная подготовлена; "+
                    "t° ${temp?: "не указана"}${if(proposed)" (не подтверждена)" else ""}; "+
                    "${if(inventory)"☑" else "☐"} инвентарь на местах")
            }
            notes["summary"]?.takeIf{it.isNotBlank()}?.let{appendLine("Замечания: $it")}
            append("Статус отражает отметки мастера, не заменяет технический допуск.")
        }
    }
}
