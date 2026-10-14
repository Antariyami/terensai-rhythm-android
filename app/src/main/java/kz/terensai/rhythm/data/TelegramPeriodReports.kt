package kz.terensai.rhythm.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Selecting a period always uses the current date, not S13's history picker. */
enum class TelegramReportPeriod(val label:String){
    TODAY("Сегодня"), MONTH("Этот месяц"), HALF_YEAR("Последние 6 месяцев"), ALL("Всё время")
}
data class TelegramExport(
    val title:String,
    val text:String,
    val completedSteamings:Int,
    val daysWithWork:Int
)
object TelegramPeriodReports {
    private val dateFmt=DateTimeFormatter.ofPattern("dd.MM.yyyy")
    fun startDate(period:TelegramReportPeriod,today:LocalDate):LocalDate?=when(period){
        TelegramReportPeriod.TODAY->today
        TelegramReportPeriod.MONTH->today.withDayOfMonth(1)
        TelegramReportPeriod.HALF_YEAR->today.withDayOfMonth(1).minusMonths(5)
        TelegramReportPeriod.ALL->null
    }
    private fun startTime(line:JournalLine):Long?=
        line.work.events.firstOrNull{it.kind=="START"}?.atMs
    fun generate(
        period:TelegramReportPeriod,
        masterName:String,
        lines:List<JournalLine>,
        today:LocalDate=LocalDate.now(),
        zone:ZoneId=ZoneId.systemDefault()
    ):TelegramExport {
        val earliest=startDate(period,today)
        val eligible=lines.filter {line->
            line.work.state=="COMPLETED" &&
            line.work.row.procedureType in setOf("INDIVIDUAL_STEAM","COLLECTIVE_STEAM") &&
            line.work.events.any{it.kind=="COMPLETE"} &&
            startTime(line)?.let{ms->
                val day=Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
                (earliest==null || day>=earliest) && day<=today
            }==true
        }
        val grouped=eligible.groupBy {line->
            Instant.ofEpochMilli(startTime(line)!!).atZone(zone).toLocalDate()
        }.toSortedMap()
        val title=when(period){
            TelegramReportPeriod.TODAY->"За ${today.format(dateFmt)}"
            TelegramReportPeriod.MONTH->"За ${startDate(period,today)!!.format(dateFmt)} — ${today.format(dateFmt)}"
            TelegramReportPeriod.HALF_YEAR->"За ${startDate(period,today)!!.format(dateFmt)} — ${today.format(dateFmt)}"
            TelegramReportPeriod.ALL->"Весь архив по ${today.format(dateFmt)}"
        }
        val texts=grouped.entries.map{(date,dayLines)->
            TelegramWorkReport.message(date,masterName,dayLines,zone)
        }
        val output=if(period==TelegramReportPeriod.TODAY)
            texts.firstOrNull()?:"За ${today.format(dateFmt)} завершённых парений нет."
        else buildString {
            append("ПАРИТМ · ${period.label}\n$title\n")
            append("Мастер: $masterName\n")
            append("Завершённых парений: ${eligible.size}\n")
            append("Дней с парениями: ${grouped.size}\n\n")
            append(if(texts.isEmpty())"За выбранный период завершённых парений нет."
                else texts.joinToString("\n\n════════════════\n\n"))
        }
        return TelegramExport(title,output,eligible.size,grouped.size)
    }
    fun html(export:TelegramExport):String {
        fun escape(raw:String)=raw.replace("&","&amp;").replace("<","&lt;")
            .replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;")
        return """<!DOCTYPE html>
<html lang="ru"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ПАРИТМ — ${escape(export.title)}</title>
<style>body{font-family:system-ui,Arial,sans-serif;background:#f7f5ef;color:#29261f;
margin:0 auto;max-width:760px;padding:28px;line-height:1.5}
h1{font-size:25px} .summary{margin:16px 0;padding:14px;background:#eee8d7;border-radius:10px}
pre{font-family:inherit;white-space:pre-wrap;overflow-wrap:anywhere;font-size:15px;line-height:1.65}
@media print{body{background:white;padding:0}.summary{background:#f1f1f1}}</style></head><body>
<h1>ПАРИТМ · Архив процедур</h1>
<div class="summary"><strong>${escape(export.title)}</strong><p>Парений: ${export.completedSteamings}.
Дней с работой: ${export.daysWithWork}.</p></div>
<pre>${escape(export.text)}</pre></body></html>"""
    }
}
