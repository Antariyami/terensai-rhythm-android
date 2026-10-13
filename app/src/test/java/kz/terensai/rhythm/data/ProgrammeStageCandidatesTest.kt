package kz.terensai.rhythm.data

import kz.terensai.rhythm.domain.*
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ProgrammeStageCandidatesTest {
    private val zone=ZoneId.of("Asia/Almaty")
    private val today=LocalDate.of(2026,10,14)
    private val ts=today.atTime(14,10).atZone(zone).toInstant().toEpochMilli()
    private val plan=StagePlan("steam1","Коллективное парение №1",Track.GUEST,0,720000L)
    private val rest=StagePlan("rest","Отдых гостей",Track.GUEST,720000L,600000L)
    private fun progress(status:StageStatus,started:Long?):SessionProgress {
        return SessionProgress(0L,0L,ts+180000L,listOf(
            StageProgress(plan,status,started,if(status==StageStatus.COMPLETED)ts+600000 else null,600000L,null),
            StageProgress(rest,StageStatus.COMPLETED,ts,ts+600000,600000L,null)
        ))
    }
    @Test fun onlyActualCompletedSteamStagesAppearOnTheirStartDay(){
        val s=SavedSession("session","Танысу",ts,null,progress(StageStatus.COMPLETED,ts),4,
            kz.terensai.rhythm.data.room.SessionMetadata(suite="2 · Өркендеу"))
        val stages=ProgrammeStageCandidates.of(listOf(s),today,zone)
        assertEquals(2,stages.size)
        assertEquals("COLLECTIVE_STEAM",stages.single{it.stageId=="steam1"}.type)
        assertNull(stages.single{it.stageId=="rest"}.type)
        assertEquals(0,ProgrammeStageCandidates.of(listOf(s),today.plusDays(1),zone).size)
    }
    @Test fun pausedSkippedAndUnstartedStagesNotClaimedCompleted(){
        val paused=SavedSession("s","Tanysu",ts,null,progress(StageStatus.PAUSED,ts),2)
        val doneButNotStarted=SavedSession("q","Tanysu",ts,null,
            progress(StageStatus.COMPLETED,null),2)
        val history=ProgrammeStageCandidates.of(listOf(paused,doneButNotStarted),today,zone)
        assertTrue(history.none{it.stageId=="steam1"})
        assertEquals(2,history.count{it.stageId=="rest"})
        assertNull(ProgrammeStageCandidates.classify("Отдых гостей"))
        assertEquals("INDIVIDUAL_STEAM",ProgrammeStageCandidates.classify("Индивидуальное парение"))
    }
}
