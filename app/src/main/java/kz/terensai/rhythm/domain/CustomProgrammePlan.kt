package kz.terensai.rhythm.domain

/**
 * A user-defined route is a snapshot, not a TERENSAI standard template.
 * Stage names and times come only from the person creating this programme.
 */
data class CustomStageInput(val title:String,val minutes:Int?,val track:Track=Track.GUEST)

data class CustomProgrammeDraft(
    val title:String,
    val stages:List<CustomStageInput>
)

object CustomProgrammePlan {
    const val MAX_STAGES=12
    const val MAX_TOTAL_MINUTES=720
    fun validate(draft:CustomProgrammeDraft):String?{
        if(draft.title.trim().length !in 2..80) return "Название программы: 2–80 символов"
        if(draft.stages.isEmpty() || draft.stages.size>MAX_STAGES)
            return "Нужно от 1 до $MAX_STAGES этапов"
        if(draft.stages.any{it.title.trim().length !in 2..100})
            return "Название каждого этапа: 2–100 символов"
        if(draft.stages.any{it.minutes==null || it.minutes !in 1..240})
            return "Длительность каждого этапа: 1–240 минут"
        val total=draft.stages.sumOf{it.minutes?:0}
        if(total>MAX_TOTAL_MINUTES) return "Общая программа не может превышать 12 часов"
        return null
    }
    fun toPlan(draft:CustomProgrammeDraft):ProgramPlan{
        require(validate(draft)==null){validate(draft)?: "Некорректный маршрут"}
        var offset=0L
        val stages=draft.stages.mapIndexed{index,item->
            val duration=(item.minutes?:error("Minutes not set")).toLong()*60000L
            val stage=StagePlan(
                "custom-stage-${index+1}",
                item.title.trim(),
                item.track,
                offset,
                duration
            )
            offset+=duration
            stage
        }
        return ProgramPlan("custom-v1",draft.title.trim(),offset,stages)
    }
}
