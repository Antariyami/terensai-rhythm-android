package kz.terensai.rhythm.data

import kz.terensai.rhythm.data.room.WorkEventRow
import kz.terensai.rhythm.data.room.WorkRow
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TelegramPeriodReportsTest {
    private val zone=ZoneId.of("Asia/Almaty")
    private val today=LocalDate.of(2026,10,14)
    private fun work(id:String,day:LocalDate,kind:String="INDIVIDUAL_STEAM"):JournalLine{
        val t=day.atTime(9,45).atZone(zone).toInstant().toEpochMilli()
        val row=WorkRow(id,"session-$id","andrey","PM",kind,30*60000L,t,"qa",
            pm1StaffId="olzhas",pm1NameSnapshot="Олжас",
            suiteNameSnapshot="3 · Байлық",programmeNameSnapshot="Танысу")
        val events=listOf(WorkEventRow("s-$id",id,"START",t),
            WorkEventRow("c-$id",id,"COMPLETE",t+60000L))
        return JournalLine(WorkView(row,"COMPLETED",60000L,events),emptyList())
    }
    @Test fun selectTodayMonthSixMonthsOrAllSeparately(){
        val data=listOf(
            work("today",today),
            work("oct",LocalDate.of(2026,10,1)),
            work("sep",LocalDate.of(2026,9,30)),
            work("may",LocalDate.of(2026,5,1)),
            work("apr",LocalDate.of(2026,4,30)),
            work("old",LocalDate.of(2025,12,1))
        )
        assertEquals(1,TelegramPeriodReports.generate(TelegramReportPeriod.TODAY,"Андрей",data,today,zone).completedSteamings)
        assertEquals(2,TelegramPeriodReports.generate(TelegramReportPeriod.MONTH,"Андрей",data,today,zone).completedSteamings)
        assertEquals(4,TelegramPeriodReports.generate(TelegramReportPeriod.HALF_YEAR,"Андрей",data,today,zone).completedSteamings)
        assertEquals(6,TelegramPeriodReports.generate(TelegramReportPeriod.ALL,"Андрей",data,today,zone).completedSteamings)
        assertEquals(LocalDate.of(2026,5,1),TelegramPeriodReports.startDate(TelegramReportPeriod.HALF_YEAR,today))
    }
    @Test fun htmlEscapesUserTextAndIsOffline(){
        val export=TelegramExport("Архив <не выдумывать>","Андрей & Олжас <script>alert('x')</script>",1,1)
        val html=TelegramPeriodReports.html(export)
        assertTrue(html.startsWith("<!DOCTYPE html>"))
        assertTrue(html.contains("charset=\"UTF-8\""))
        assertFalse(html.contains("<script>"))
        assertTrue(html.contains("&lt;script&gt;"))
        assertTrue(html.contains("&amp;"))
        assertFalse(html.contains("http://"))
    }
    @Test fun todayTelegramTextRemainsCompact(){
        val text=TelegramPeriodReports.generate(TelegramReportPeriod.TODAY,
            "Андрей",listOf(work("today",today)),today,zone).text
        assertTrue(text.startsWith("14.10.26\nСьют 3 — БАЙЛЫҚ"))
        assertTrue(text.contains("Коллективка — 0"))
        assertTrue(text.contains("Индивидуальное парение — 1"))
    }
}
