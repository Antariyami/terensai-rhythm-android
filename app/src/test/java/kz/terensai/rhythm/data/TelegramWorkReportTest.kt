package kz.terensai.rhythm.data

import kz.terensai.rhythm.data.room.WorkEventRow
import kz.terensai.rhythm.data.room.WorkRow
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TelegramWorkReportTest {
    private val zone=ZoneId.of("Asia/Almaty")
    private val date=LocalDate.of(2026,10,14)
    private val start=date.atTime(12,30).atZone(zone).toInstant().toEpochMilli()

    private fun completed(
        id:String,type:String="INDIVIDUAL_STEAM",
        sessionId:String?="p1",suite:String?="2 · Өркендеу",
        pm1:String?="Олжас", offsetMs:Long=0,
        status:String="COMPLETED", programme:String?="PREMIUM"
    ):JournalLine{
        val t=start+offsetMs
        val row=WorkRow(id,sessionId,"andrey","PM",type,15*60000,t,"qa",
            pm1StaffId="olzhas",pm1NameSnapshot=pm1,
            suiteNameSnapshot=suite,programmeNameSnapshot=programme)
        val events=listOf(
            WorkEventRow("start-$id",id,"START",t),
            WorkEventRow("end-$id",id,if(status=="COMPLETED")"COMPLETE" else "PAUSE",t+45000)
        )
        return JournalLine(WorkView(row,status,45000,events),emptyList())
    }

    @Test fun reportsOneCollectiveTwoIndividualFromSameLocalProgramme(){
        val lines=listOf(
            completed("c",type="COLLECTIVE_STEAM"),
            completed("i1",offsetMs=60000),
            completed("i2",offsetMs=120000)
        )
        val s=TelegramWorkReport.message(date,"Андрей",lines,zone)
        assertEquals(1,Regex("Коллективка — 1").findAll(s).count())
        assertTrue(s.contains("Индивидуальное парение — 2"))
        assertTrue(s.contains("14.10.26\nСьют 2 — ӨРКЕНДЕУ\nВремя: 12:30\nПрограмма: PREMIUM"))
        assertTrue(s.contains("\nАндрей\n"))
        assertTrue(s.endsWith("PM1: Олжас"))
        assertFalse(s.contains("──────────"))
    }

    @Test fun differentLocalSessionsAreSeparateAndPausedIsExcluded(){
        val lines=listOf(
            completed("a",type="COLLECTIVE_STEAM",sessionId="p1"),
            completed("b",sessionId="p2",suite="4 · Байлық",pm1="Зарина",programme="GRAND",offsetMs=300000),
            completed("c",sessionId="p1",status="PAUSED",offsetMs=600000)
        )
        val s=TelegramWorkReport.message(date,"Андрей",lines,zone)
        assertEquals(2,Regex("Коллективка — ").findAll(s).count())
        assertTrue(s.contains("Сьют 4 — БАЙЛЫҚ"))
        assertTrue(s.contains("PM1: Зарина"))
        assertTrue(s.contains("Индивидуальное парение — 0"))
        assertFalse(s.contains("12:40"))
    }

    @Test fun neverMergeDistinctExternalVisitWithoutStableVisitId(){
        val lines=listOf(
            completed("ext-1",sessionId=null),
            completed("ext-2",sessionId=null,offsetMs=240000)
        )
        val s=TelegramWorkReport.message(date,"Андрей",lines,zone)
        assertEquals(2,Regex("PM1: Олжас").findAll(s).count())
        assertEquals(2,Regex("Индивидуальное парение — 1").findAll(s).count())
    }

    @Test fun missingContextIsExplicitAndDifferentDayDoesNotLeak(){
        val old=completed("old",sessionId=null,suite=null,pm1=null,programme=null)
        val s=TelegramWorkReport.message(date,"Андрей",listOf(old),zone)
        assertTrue(s.contains("Сьют: не указан"))
        assertTrue(s.contains("PM1: не указан"))
        assertTrue(s.contains("Программа: не указана"))
        assertEquals("За 15.10.26 завершённых парений нет.",
            TelegramWorkReport.message(date.plusDays(1),"Андрей",listOf(old),zone))
    }

    @Test fun otherProceduresAreNotWronglyCountedAsSteamings(){
        val massage=completed("m",type="MASSAGE")
        val s=TelegramWorkReport.message(date,"Андрей",listOf(massage),zone)
        assertEquals("За 14.10.26 завершённых парений нет.",s)
    }
}
