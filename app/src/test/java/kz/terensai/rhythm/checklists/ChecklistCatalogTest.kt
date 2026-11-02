package kz.terensai.rhythm.checklists

import org.junit.Assert.*
import org.junit.Test

class ChecklistCatalogTest {
    @Test fun receptionMatchesSevenPagesAndSixtyOneSourceItems() {
        val template=ChecklistCatalog.reception()
        assertEquals("RECEPTION",template.type)
        assertEquals(7,template.blocks.size)
        assertEquals(listOf(6,8,8,8,16,8,7),template.blocks.map{it.items.size})
        assertEquals(61,template.totalItems)
        assertTrue(template.blocks[0].items.first().title.contains("физическое состояние"))
        assertTrue(template.blocks[6].items.last().title.contains("Результат проверен"))
    }
    @Test fun bagHasFourGroupsFourteenObjectsAndFiveOriginalQuantities() {
        val bag=ChecklistCatalog.bag()
        assertEquals("BAG",bag.type)
        assertEquals(listOf(5,7,1,1),bag.blocks.map{it.items.size})
        assertEquals(14,bag.totalItems)
        assertEquals(2,bag.blocks.first().items.first().quantity)
        assertEquals("шт.",bag.blocks.first().items.first().unit)
        assertNull(bag.blocks[1].items.first().quantity)
    }
    @Test fun dutyHasFiveSeparateSuitesAndEditableNineChecksEach(){
        val duty=ChecklistCatalog.duty()
        assertEquals("DUTY",duty.type)
        assertEquals(listOf(2,9,9,9,9,9,2),duty.blocks.map{it.items.size})
        assertEquals(49,duty.totalItems)
        assertEquals(listOf("duty-suite-1","duty-suite-2","duty-suite-3",
            "duty-suite-4","duty-suite-5"),duty.blocks.drop(1).take(5).map{it.id})
        assertTrue(duty.blocks[1].items[0].title.contains("газовая"))
        assertTrue(duty.blocks[2].items[0].title.contains("дровяная"))
        assertTrue(duty.blocks[5].items[0].title.contains("дровяная"))
        assertTrue(duty.blocks.last().items.last().title.contains("готовности сьютов"))
    }
    @Test fun movingAndEditingBlocksKeepsIdsStable() {
        val original=ChecklistCatalog.reception()
        val swapped=ChecklistEditing.moveBlock(original,0,1)
        assertEquals(original.blocks[0].id,swapped.blocks[1].id)
        assertEquals(61,swapped.totalItems)
        val add=ChecklistEditing.addItem(swapped,0)
        assertEquals(62,add.totalItems)
        assertEquals(61,ChecklistEditing.removeItem(add,0,add.blocks[0].items.lastIndex).totalItems)
        assertEquals(61,original.totalItems)
    }
}
