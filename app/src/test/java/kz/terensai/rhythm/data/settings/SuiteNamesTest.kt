package kz.terensai.rhythm.data.settings

import org.junit.Assert.*
import org.junit.Test

class SuiteNamesTest {
    @Test fun noPresetEmployerAndNamesAreOptional() {
        assertTrue(SuiteNames.add(emptyList(),"  ").isEmpty())
        assertEquals(listOf("Оркендеу"),SuiteNames.add(emptyList(),"  Оркендеу  "))
    }
    @Test fun duplicatesAreCaseInsensitive() {
        val a=SuiteNames.add(emptyList(),"Сьют 4")
        val b=SuiteNames.add(a,"сьют 4")
        assertEquals(1,b.size)
    }
    @Test fun renamePreservesListOrderAndNoDuplicate() {
        val original=listOf("Амандык","Оркендеу","Коркемдик")
        assertEquals(listOf("Амандык","Оркендеу 2","Коркемдик"),
            SuiteNames.rename(original,"Оркендеу","Оркендеу 2"))
        assertEquals(original,SuiteNames.rename(original,"Оркендеу","Амандык"))
        assertEquals(original,SuiteNames.rename(original,"Не существует","Новый"))
        assertEquals(listOf("Амандык","Коркемдик"),SuiteNames.remove(original,"Оркендеу"))
    }
}
