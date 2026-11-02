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
        val total=template.totalItems
        val completed=template.blocks.sumOf{b->b.items.count{it.id in checked}}
        return buildString {
            appendLine("ПАРИТМ · Чек-лист дежурного")
            appendLine("Дата: $date · Начало проверки: $time")
            appendLine("Дежурный: ${run.staffNameSnapshot?:"Не указан"}")
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
                    appendLine("Температура при проверке: $it °C")
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
}
