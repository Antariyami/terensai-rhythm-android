package kz.terensai.rhythm.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Telegram compact report. Do not infer visits from matching PM1, date or suite:
 * these attributes can be identical for two distinct bookings.
 * A local programme groups by its stable sessionId. Older external work lacks
 * visitId, so each external work is a separate report block until visit grouping
 * receives an explicit identity in a future schema version.
 */
object TelegramWorkReport {
    private val dateFmt=DateTimeFormatter.ofPattern("dd.MM.yy",Locale("ru","RU"))
    private val timeFmt=DateTimeFormatter.ofPattern("HH:mm",Locale("ru","RU"))
    private val suiteRegex=Regex("^\\s*(?:Сьют\\s*)?(\\d+)\\s*[·—–-]?\\s*(.*?)\\s*$",RegexOption.IGNORE_CASE)

    private data class ReportGroup(
        val id:String,
        val rows:List<JournalLine>,
        val startMs:Long
    )

    private fun start(line:JournalLine):Long? =
        line.work.events.firstOrNull{it.kind=="START"}?.atMs

    private fun eligible(line:JournalLine):Boolean =
        line.work.state=="COMPLETED" &&
            line.work.row.procedureType in setOf("COLLECTIVE_STEAM","INDIVIDUAL_STEAM") &&
            start(line)!=null &&
            line.work.events.any{it.kind=="COMPLETE"}

    private fun suite(value:String?):String {
        if(value.isNullOrBlank())return "Сьют: не указан"
        val raw=value.trim()
        val match=suiteRegex.matchEntire(raw)
        if(match!=null){
            val number=match.groupValues[1]
            val title=match.groupValues[2].trim()
            if(title.isNotEmpty())return "Сьют $number — ${title.uppercase(Locale("ru","RU"))}"
        }
        return "Сьют — ${raw.uppercase(Locale("ru","RU"))}"
    }

    fun message(
        date:LocalDate,
        masterName:String,
        lines:List<JournalLine>,
        zone:ZoneId=ZoneId.systemDefault()
    ):String {
        val eligible=lines.filter { eligible(it) &&
            Instant.ofEpochMilli(start(it)!!).atZone(zone).toLocalDate()==date
        }
        if(eligible.isEmpty())return "За ${date.format(dateFmt)} завершённых парений нет."
        // An external work without a visit id must never be silently mixed with
        // another external booking even if both have the same PM1 and suite.
        val grouped=eligible.groupBy { line ->
            val row=line.work.row
            if(row.sessionId!=null)
                "session:${row.sessionId}:${row.staffId}:${row.pm1StaffId?:row.pm1NameSnapshot.orEmpty()}"
            else "external-work:${row.id}"
        }
        return grouped.map {(key,records)->
            ReportGroup(key,records,records.mapNotNull(::start).minOrNull()!!)
        }.sortedWith(compareBy<ReportGroup>{it.startMs}.thenBy{it.id})
            .joinToString("\n\n──────────\n\n") { group ->
                val earliest=group.rows.minBy{start(it)!!}.work.row
                val collective=group.rows.count{it.work.row.procedureType=="COLLECTIVE_STEAM"}
                val individual=group.rows.count{it.work.row.procedureType=="INDIVIDUAL_STEAM"}
                listOf(
                    date.format(dateFmt),
                    suite(earliest.suiteNameSnapshot),
                    "Время: ${Instant.ofEpochMilli(group.startMs).atZone(zone).format(timeFmt)}",
                    "Программа: ${earliest.programmeNameSnapshot?.takeIf{it.isNotBlank()}?:"не указана"}",
                    "",
                    masterName.takeIf{it.isNotBlank()}?:"Мастер не указан",
                    "",
                    "Коллективка — $collective",
                    "Индивидуальное парение — $individual",
                    "",
                    "PM1: ${earliest.pm1NameSnapshot?.takeIf{it.isNotBlank()}?:"не указан"}"
                ).joinToString("\n")
            }
    }
}
