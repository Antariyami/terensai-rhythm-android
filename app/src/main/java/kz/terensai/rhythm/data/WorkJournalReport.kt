package kz.terensai.rhythm.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

object WorkJournalReport {
    private val dateFormat=DateTimeFormatter.ofPattern("dd.MM.yyyy",Locale("ru","RU"))
    private val clockFormat=DateTimeFormatter.ofPattern("HH:mm",Locale("ru","RU"))
    fun dayOf(line:JournalLine,zone:ZoneId=ZoneId.systemDefault()):LocalDate {
        val timestamp=line.work.events.firstOrNull{it.kind=="START"}?.atMs
            ?:line.work.row.createdAtMs
        return Instant.ofEpochMilli(timestamp).atZone(zone).toLocalDate()
    }
    fun hhmm(timestamp:Long,zone:ZoneId=ZoneId.systemDefault()):String =
        Instant.ofEpochMilli(timestamp).atZone(zone).format(clockFormat)
    fun dayLabel(date:LocalDate):String=date.format(dateFormat)
    fun completed(lines:List<JournalLine>):List<JournalLine> =
        lines.filter{it.work.state=="COMPLETED"}
    fun totalMinutes(lines:List<JournalLine>):Long =
        completed(lines).sumOf{it.reportedMinutes}

    fun message(
        date:LocalDate,
        masterName:String,
        completedLines:List<JournalLine>,
        zone:ZoneId=ZoneId.systemDefault()
    ):String {
        val completed=completed(completedLines).sortedBy {
            it.work.events.firstOrNull{e->e.kind=="START"}?.atMs?:it.work.row.createdAtMs
        }
        val body=completed.mapIndexed{index,line->
            val row=line.work.row
            val start=line.work.events.firstOrNull{it.kind=="START"}?.atMs
            val end=line.work.events.lastOrNull{it.kind=="COMPLETE"}?.atMs
            listOfNotNull(
                "${index+1}. ${WorkJournalLabels.procedure(row.procedureType)}",
                "Программа: ${row.programmeNameSnapshot?:"не указана"}",
                "Сьют: ${row.suiteNameSnapshot?: "не указан (локальная программа)"}",
                "PM1: ${row.pm1NameSnapshot?: "не указан (локальная программа)"}",
                "Роль: ${row.role}",
                start?.let { "Начало: ${hhmm(it,zone)}" },
                end?.let { "Окончание: ${hhmm(it,zone)}" },
                "План: ${row.plannedMs/60000} мин · Факт: ${line.reportedMinutes} мин" +
                    if(line.isAdjusted) " (исправлено)" else ""
            ).joinToString("\n")
        }.joinToString("\n\n")
        val grouped=completed.groupingBy{WorkJournalLabels.procedure(it.work.row.procedureType)}
            .eachCount().entries.sortedBy{it.key}
            .joinToString("\n") { "${it.key}: ${it.value}" }
        return buildString {
            append("ПАРИТМ · Личный отчёт за ${dayLabel(date)}\n")
            append("Мастер: ${masterName}\n")
            append("Завершённых процедур: ${completed.size}\n")
            append("Суммарный факт по процедурам: ${totalMinutes(completed)} мин\n")
            if(grouped.isNotBlank())append("\n${grouped}\n")
            if(body.isNotBlank())append("\n${body}") else append("\nЗавершённых процедур за день нет.")
            append("\n\nУчёт времени процедур не равен продолжительности всей смены.")
        }
    }
}
