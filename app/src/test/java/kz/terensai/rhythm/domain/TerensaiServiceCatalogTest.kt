package kz.terensai.rhythm.domain

import org.junit.Assert.*
import org.junit.Test

class TerensaiServiceCatalogTest {
    @Test fun originalScreenshotServiceDurationsAndPrices() {
        val all=TerensaiServiceCatalog.entries
        assertEquals(all.map{it.id}.toSet().size,all.size)
        assertEquals(15,all.size)
        assertEquals(45,TerensaiServiceCatalog.byId("individual_steam")!!.suggestedMinutes)
        assertEquals(15,TerensaiServiceCatalog.byId("collective_express")!!.suggestedMinutes)
        assertEquals(40,TerensaiServiceCatalog.byId("peeling")!!.suggestedMinutes)
        assertEquals(60,TerensaiServiceCatalog.byId("massage_aksoran")!!.suggestedMinutes)
        assertEquals(25_000,TerensaiServiceCatalog.byId("washing_broom")!!.retailKzt)
    }
    @Test fun hourPriceReferenceBySuiteDoesNotBecomePayroll() {
        assertEquals(50_000,TerensaiServiceCatalog.extraHourPriceKzt("1 Көркемдік"))
        assertEquals(50_000,TerensaiServiceCatalog.extraHourPriceKzt("2 Өркендеу"))
        assertEquals(50_000,TerensaiServiceCatalog.extraHourPriceKzt("5 Сәттілік"))
        assertEquals(100_000,TerensaiServiceCatalog.extraHourPriceKzt("3 Байлық"))
        assertEquals(100_000,TerensaiServiceCatalog.extraHourPriceKzt("4 Амандық"))
        assertNull(TerensaiServiceCatalog.extraHourPriceKzt(null))
    }
}
