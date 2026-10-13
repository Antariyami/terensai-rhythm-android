package kz.terensai.rhythm.data

import kz.terensai.rhythm.data.room.WorkRow
import kz.terensai.rhythm.data.room.WorkEventRow
import kz.terensai.rhythm.data.room.WorkCorrectionRow
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WorkJournalReportTest {
    @Test fun dailyMessageCountsOnlyCompletedAndSeparatesMeasuredFromCorrected() {
        val day=LocalDate.of(2026,10,14)
        val zone=ZoneId.of("Asia/Almaty")
        val start=day.atTime(9,30).atZone(zone).toInstant().toEpochMilli()
        fun row(id:String,procedure:String)=WorkRow(
            id,null,"master","PM",procedure,45L*60000L,start,"qa")
        val finished=WorkView(row("work-1","INDIVIDUAL_STEAM"),"COMPLETED",600000L,
            listOf(WorkEventRow("e1","work-1","START",start),
                WorkEventRow("e2","work-1","COMPLETE",start+600000L)))
        val stillRunning=WorkView(row("work-2","MASSAGE"),"RUNNING",180000L,
            listOf(WorkEventRow("e3","work-2","START",start)))
        val corrected=JournalLine(finished,listOf(WorkCorrectionRow(
            "c1","work-1",start+700000L,10L,9L,"Уточнение по записи")))
        val pending=JournalLine(stillRunning,emptyList())
        assertEquals(day,WorkJournalReport.dayOf(corrected,zone))
        assertEquals(9L,WorkJournalReport.totalMinutes(listOf(corrected,pending)))
        val message=WorkJournalReport.message(day,"Андрей",listOf(corrected,pending),zone)
        assertTrue(message.contains("Завершённых процедур: 1"))
        assertTrue(message.contains("Суммарный факт по процедурам: 9 мин"))
        assertTrue(message.contains("Индивидуальное парение: 1"))
        assertFalse(message.contains("Массаж: 1"))
        assertTrue(message.contains("исправлено"))
    }
}
