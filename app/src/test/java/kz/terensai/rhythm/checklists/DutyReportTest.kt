package kz.terensai.rhythm.checklists

import kz.terensai.rhythm.data.room.ChecklistRunRow
import org.junit.Assert.*
import org.junit.Test

class DutyReportTest {
    @Test fun partialDutyReportPreservesFiveSuitesAndNamedMaster(){
        val template=ChecklistCatalog.duty()
        val checked=setOf(template.blocks[0].items[0].id,template.blocks[1].items[1].id)
        val record=ChecklistRunRow(
            id="duty-1",templateId=template.id,templateVersion=template.version,
            snapshotJson=ChecklistJson.encode(template),
            checkedJson=ChecklistJson.encodeAnswers(checked),
            notesJson=ChecklistJson.encodeNotes(mapOf(
                "duty-suite-1" to "Проверка передана ответственному",
                "temperature:duty-suite-1" to "84",
                "summary" to "До передачи проверено частично"
            )),
            status="IN_PROGRESS",startedAtMs=1_800_000_000_000L,
            updatedAtMs=1_800_000_000_000L,
            staffIdSnapshot="staff-1",staffNameSnapshot="Тестовый мастер",
            dutyDate="2026-11-02"
        )
        val report=DutyReport.render(record)
        assertTrue(report.contains("Тестовый мастер"))
        assertTrue(report.contains("2026-11-02"))
        assertTrue(report.contains("Проверено: 2 из 49"))
        assertTrue(report.contains("☑ Прибыл к 10:00"))
        assertTrue(report.contains("☐ Передал информацию о готовности сьютов"))
        assertTrue(report.contains("Проверка передана ответственному"))
        assertTrue(report.contains("Температура при проверке: 84 °C"))
        assertTrue(report.contains("До передачи проверено частично"))
        val afterEdit=ChecklistEditing.addItem(template,0)
        assertEquals(50,afterEdit.totalItems)
        assertEquals(49,ChecklistJson.decode(record.snapshotJson).totalItems)
        assertEquals(report,DutyReport.render(record))
    }
    @Test fun editorStillRoundTripsDutySchemaAndItemOrder(){
        val model=ChecklistCatalog.duty()
        val changed=ChecklistEditing.moveBlock(model,0,1)
        val fromJson=ChecklistJson.decode(ChecklistJson.encode(changed))
        assertEquals(changed,fromJson)
        assertEquals(model.blocks[0].id,fromJson.blocks[1].id)
    }
}
