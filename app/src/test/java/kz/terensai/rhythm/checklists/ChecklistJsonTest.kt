package kz.terensai.rhythm.checklists

import org.junit.Assert.*
import org.junit.Test

class ChecklistJsonTest {
    @Test fun templateExportImportRoundTripRetainsBlocksItemsAndAmounts() {
        val original=ChecklistCatalog.bag()
        val parsed=ChecklistJson.decode(ChecklistJson.encode(original))
        assertEquals(original,parsed)
        assertEquals(14,parsed.totalItems)
    }
    @Test fun answersRoundTripStableAndUnique() {
        val encoded=ChecklistJson.encodeAnswers(setOf("a","b"))
        assertEquals(setOf("a","b"),ChecklistJson.decodeAnswers(encoded))
    }
    @Test fun duplicateIdentifiersAndInvalidQuantityAreRejected() {
        val original=ChecklistCatalog.bag()
        val items=original.blocks[0].items
        try{
            original.copy(blocks=original.blocks.toMutableList().also{
                it[0]=it[0].copy(items=items+items[0])
            }).validated()
            fail("Duplicate identifier must fail")
        }catch(_:IllegalArgumentException){}
        try {
            original.copy(blocks=original.blocks.toMutableList().also{
                it[0]=it[0].copy(items=items.toMutableList().also{rows->
                    rows[0]=rows[0].copy(quantity=-2)
                })
            }).validated()
            fail("Negative amount must fail")
        }catch(_:IllegalArgumentException){}
    }
    @Test fun invalidVersionAndOversizedFilesCannotBeImported() {
        val json=ChecklistJson.encode(ChecklistCatalog.bag())
        try{ChecklistJson.decode(json.replace("\"schemaVersion\":1", "\"schemaVersion\":99"))
            fail("New incompatible schema must fail")
        }catch(_:IllegalArgumentException){}
        try{ChecklistJson.decode(" ".repeat(ChecklistJson.MAX_BYTES+1))
            fail("Oversized input must fail")
        }catch(_:IllegalArgumentException){}
    }
}
