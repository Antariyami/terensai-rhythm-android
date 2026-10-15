package kz.terensai.rhythm.domain

/**
 * Source: TERENSAI commercial SPA screenshots provided 15 October 2026.
 * Price is informational guest retail, never a master payroll rate.
 * A catalogue entry is NOT a completed WorkEntry.
 */
data class TerensaiService(
    val id:String,val title:String,val workType:String,
    val suggestedMinutes:Int,val retailKzt:Int
)

object TerensaiServiceCatalog {
    val entries=listOf(
        TerensaiService("massage_akzhar","Ветер Ақжар · голова и лицо","MASSAGE",30,20000),
        TerensaiService("massage_saryarka","Ось Сарыарқа · спина, шея и плечи","MASSAGE",30,20000),
        TerensaiService("massage_aulie","Руки Аулие · кисти, предплечья, плечи","MASSAGE",30,20000),
        TerensaiService("massage_pilgrim","Ступы пилигрима · ноги","MASSAGE",30,20000),
        TerensaiService("massage_naurzum","Источник Наурзум · лимфодренаж","MASSAGE",60,40000),
        TerensaiService("massage_aksoran","Мощь Аксоран · глубокий массаж","MASSAGE",60,40000),
        TerensaiService("spa_unspecified","SPA-процедура (без уточнения)","OTHER",30,0),
        TerensaiService("body_wash","Помывка тела (уточнить вид)","WASHING",40,0),
        TerensaiService("scrub","Скрабирование","OTHER",40,25000),
        TerensaiService("peeling","Пилинг","PEELING",40,25000),
        TerensaiService("washing_broom","Пенная помывка веником","WASHING",40,25000),
        TerensaiService("head_wash","Мойка головы","OTHER",20,15000),
        TerensaiService("individual_steam","Индивидуальное парение","INDIVIDUAL_STEAM",45,40000),
        TerensaiService("individual_express","Индивидуальное экспресс-парение","INDIVIDUAL_STEAM",20,25000),
        TerensaiService("collective_double","Коллективное в 2 захода","COLLECTIVE_STEAM",50,65000),
        TerensaiService("collective_express","Коллективное экспресс-парение","COLLECTIVE_STEAM",15,20000),
        TerensaiService("aqualevitation","Аквалевитация","OTHER",10,8000)
    )
    fun byId(id:String)=entries.firstOrNull{it.id==id}
    fun extraHourPriceKzt(suite:String?):Int? {
        val label=suite?.lowercase()?:return null
        return when {
            label.contains("көркем") || label.contains("коркем")->50000
            label.contains("өркен") || label.contains("оркен")->50000
            label.contains("сәтті") || label.contains("сатти")->50000
            label.contains("байл")->100000
            label.contains("аман")->100000
            else->null
        }
    }
}
