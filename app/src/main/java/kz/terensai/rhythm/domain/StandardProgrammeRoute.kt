package kz.terensai.rhythm.domain

/**
 * Editable starting schedules, NOT rigid TERENSAI technical norms.
 *
 * Owner clarification 15.10.2026: GRAND's 50-min collective includes
 * a short internal 5–7 minute aperitif break, not an extra 5–7 minutes.
 * The guest clock does not include post-visit housekeeping.
 */
data class StandardRouteDraft(
    val programmeId:String,
    val guests:Int=2,
    val simultaneousGuests:Int=2,
    val bookedExtraHours:Int=0,
    val minuteOverrides:Map<String,Int> = emptyMap()
)

data class StandardRouteStep(
    val id:String,
    val title:String,
    val minutes:Int,
    val track:Track=Track.GUEST,
    val adjustable:Boolean=true
)

data class StandardRoutePreview(
    val programmeId:String,
    val packageMinutes:Int,
    val bookedMinutes:Int,
    val guests:Int,
    val simultaneousGuests:Int,
    val waves:Int,
    val steps:List<StandardRouteStep>,
    val fixedMinutes:Int,
    val availableMinutes:Int,
    val overrunMinutes:Int,
    val problem:String?
) {
    val valid:Boolean get()=problem==null
}

/**
 * A guest booking clock, distinct from post-visit room cleaning and from
 * parallel, staff-owned SPA WorkEntries. Neither is automatically paid work.
 */
object StandardProgrammeRoute {
    const val DEFAULT_WELCOME_MINUTES=15
    const val DEFAULT_EXPLORATORY_MINUTES=15
    const val DEFAULT_REST_MINUTES=20
    const val DEFAULT_INDIVIDUAL_MINUTES=50
    const val DEFAULT_COLLECTIVE_MINUTES=50
    const val DEFAULT_GASTRONOMY_MINUTES=10
    const val DEFAULT_CLOSING_MINUTES=15

    fun preview(draft:StandardRouteDraft):StandardRoutePreview {
        val packageMinutes=when(draft.programmeId){
            "light","grand"->240
            "premium"->300
            else->0
        }
        val booked=packageMinutes+draft.bookedExtraHours*60
        fun invalid(text:String)=StandardRoutePreview(
            draft.programmeId,packageMinutes,booked,draft.guests,
            draft.simultaneousGuests,0,emptyList(),0,0,0,text
        )
        if(packageMinutes==0)return invalid("У этой карточки пока нет исполнимого маршрута")
        if(draft.guests !in 1..30)return invalid("Укажи число гостей от 1 до 30")
        if(draft.simultaneousGuests !in 1..8)
            return invalid("Одновременно можно выбрать от 1 до 8 гостей")
        if(draft.bookedExtraHours !in 0..3)
            return invalid("Дополнительных согласованных часов может быть от 0 до 3")
        if(draft.minuteOverrides.values.any{it !in 1..240})
            return invalid("Продолжительность каждого этапа должна быть от 1 до 240 минут")

        val waves=if(draft.programmeId=="grand")1
            else (draft.guests+draft.simultaneousGuests-1)/draft.simultaneousGuests
        val steps=mutableListOf<StandardRouteStep>()
        fun add(id:String,title:String,defaultMinutes:Int){
            steps+=StandardRouteStep(id,title,
                draft.minuteOverrides[id]?:defaultMinutes)
        }

        add("welcome","Встреча и подготовка гостей",DEFAULT_WELCOME_MINUTES)
        when(draft.programmeId) {
            "light"->{
                add("intro-express","Коллективное экспресс-парение · ознакомительный прогрев",
                    DEFAULT_EXPLORATORY_MINUTES)
                add("intro-rest","Отдых гостей · подготовка к индивидуальным заходам",
                    DEFAULT_REST_MINUTES)
            }
            "grand"->{
                add("grand-collective-1",
                    "Коллективное парение №1 · два захода с отдыхом 5–7 мин внутри",
                    DEFAULT_COLLECTIVE_MINUTES)
                add("grand-break","Уборка и отдых мастеров · отдых гостей",
                    DEFAULT_REST_MINUTES)
                add("grand-collective-2",
                    "Коллективное парение №2 · два захода с отдыхом 5–7 мин внутри",
                    DEFAULT_COLLECTIVE_MINUTES)
            }
            "premium"->{
                // Changeable planning estimate; not an exact PREMIUM norm.
                add("premium-collective",
                    "Полноценное коллективное парение · плановый ориентир",
                    DEFAULT_COLLECTIVE_MINUTES)
                add("premium-break","Отдых гостей · уборка и восстановление команды",
                    DEFAULT_REST_MINUTES)
            }
        }
        if(draft.programmeId!="grand"){
            for(wave in 1..waves){
                if(wave>1){
                    add("wave-rest-$wave",
                        "Уборка и отдых мастеров перед волной №$wave",
                        DEFAULT_REST_MINUTES)
                }
                val served=minOf(draft.simultaneousGuests,
                    draft.guests-(wave-1)*draft.simultaneousGuests)
                add("individual-$wave",
                    "Индивидуальное парение · волна №$wave ($served гост.)",
                    DEFAULT_INDIVIDUAL_MINUTES)
            }
        }
        add("gastro","Гастрономия / перекус",DEFAULT_GASTRONOMY_MINUTES)
        add("closing","Завершающий отдых и окончание программы",DEFAULT_CLOSING_MINUTES)

        val fixed=steps.sumOf{it.minutes}
        val remaining=booked-fixed
        val problem=if(remaining<0)
            "План превышает бронирование на ${-remaining} мин. Измени интервалы, " +
            "число параллельных гостей или явно согласуй дополнительный час."
            else null
        if(remaining>0){
            // Free guest time is visible, immediately BEFORE the closing step.
            steps.add(steps.lastIndex,StandardRouteStep("free-rest",
                "Свободный отдых гостей / SPA по выбору",
                remaining,adjustable=false))
        }
        return StandardRoutePreview(draft.programmeId,packageMinutes,
            booked,draft.guests,draft.simultaneousGuests,waves,
            steps,fixed,remaining.coerceAtLeast(0),(-remaining).coerceAtLeast(0),problem)
    }

    fun toPlan(draft:StandardRouteDraft):ProgramPlan {
        val result=preview(draft)
        require(result.valid){result.problem?:"Некорректная технологическая карта"}
        var offset=0L
        val stages=result.steps.map{step->
            val duration=step.minutes*60_000L
            StagePlan(step.id,step.title,step.track,offset,duration).also{
                offset+=duration
            }
        }
        require(offset==result.bookedMinutes*60_000L)
        val name=draft.programmeId.uppercase()+
            if(draft.bookedExtraHours==0)"" else " · согласовано +${draft.bookedExtraHours} ч"
        return ProgramPlan(
            id="terensai-${draft.programmeId}-route-v1",
            title=name,
            plannedDurationMs=offset,
            stages=stages
        )
    }
}
