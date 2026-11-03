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
                "summary" to "До передачи проверено частично",
                "arrivalTime" to "10:17",
                "temperature-proposed:duty-suite-2" to "yes",
                "temperature:duty-suite-2" to "200"
            )),
            status="IN_PROGRESS",startedAtMs=1_800_000_000_000L,
            updatedAtMs=1_800_000_000_000L,
            staffIdSnapshot="staff-1",staffNameSnapshot="Тестовый мастер",
            dutyDate="2026-11-02"
        )
        val report=DutyReport.render(record)
        assertTrue(report.contains("Тестовый мастер"))
        assertTrue(report.contains("2026-11-02"))
        assertTrue(report.contains("Проверено: 2 из 54"))
        assertTrue(report.contains("Прибытия (со слов мастера): 10:17") ||
            report.contains("время прибытия (со слов мастера): 10:17"))
        assertTrue(report.contains("☑ Прибыл к 10:00"))
        assertTrue(report.contains("☐ Передал информацию о готовности сьютов"))
        assertTrue(report.contains("Проверка передана ответственному"))
        assertTrue(report.contains("Температура: 84 °C"))
        assertTrue(report.contains("шаблонное значение, не подтверждено измерением"))
        val short=DutyReport.renderShort(record)
        assertTrue(short.contains("Прибыл: 10:17"))
        assertTrue(short.contains("Парная №1"))
        assertTrue(short.contains("не подтверждена"))
        assertTrue(report.contains("До передачи проверено частично"))
        val afterEdit=ChecklistEditing.addItem(template,0)
        assertEquals(55,afterEdit.totalItems)
        assertEquals(54,ChecklistJson.decode(record.snapshotJson).totalItems)
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
