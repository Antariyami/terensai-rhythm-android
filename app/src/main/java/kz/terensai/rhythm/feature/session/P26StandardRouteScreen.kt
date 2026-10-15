package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.data.room.StaffRow
import kz.terensai.rhythm.domain.StandardProgrammeRoute
import kz.terensai.rhythm.domain.StandardRouteDraft
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton

/**
 * Pre-launch editable, versioned route for LIGHT/GRAND/PREMIUM.
 * Active route is frozen in Room. Post-start extras are WorkEntries/annotations,
 * not modifications of prior stage snapshots.
 */
@Composable
fun P26StandardRouteScreen(
    programmeId:String,
    staff:List<StaffRow>,
    selectedPm1:String?,
    onPm1Selected:(String?)->Unit,
    pending:Boolean,
    notice:String?,
    onBack:()->Unit,
    onCreate:(ProgramPlan,String?,Int)->Unit
){
    var guests by rememberSaveable(programmeId){mutableStateOf("2")}
    var capacity by rememberSaveable(programmeId){mutableStateOf("2")}
    var extraHours by rememberSaveable(programmeId){mutableIntStateOf(0)}
    var suite by rememberSaveable(programmeId){mutableStateOf("")}
    var minutesText by remember(programmeId){
        mutableStateOf<Map<String,String>>(emptyMap())
    }
    val validationError=minutesText.values.firstOrNull{
        it.isNotBlank() && (it.toIntOrNull()?:0) !in 1..240
    }?.let{"Укажи длительности от 1 до 240 минут"}
    val edits=minutesText.mapNotNull{(id,text)->
        text.toIntOrNull()?.takeIf{it in 1..240}?.let{id to it}
    }.toMap()
    val draft=StandardRouteDraft(
        programmeId=programmeId,
        guests=guests.toIntOrNull()?:0,
        simultaneousGuests=capacity.toIntOrNull()?:0,
        bookedExtraHours=extraHours,
        minuteOverrides=edits
    )
    val preview=StandardProgrammeRoute.preview(draft)
    val canStart=preview.valid && validationError==null && !pending
    val shape=RoundedCornerShape(16.dp)

    Column(Modifier.fillMaxSize().background(C.canvas)
        .verticalScroll(rememberScrollState())
        .padding(horizontal=18.dp,vertical=15.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick=onBack){Text("‹ К программам",color=C.amber)}
        Text("ПАРИТМ · ТЕХНОЛОГИЧЕСКАЯ КАРТА",color=C.bronze,fontSize=12.sp)
        Text(programmeId.uppercase(),color=C.white,fontSize=29.sp,
            fontWeight=FontWeight.Bold)
        Text("Живой план с рабочими ориентирами. Минуты и количество одновременно обслуживаемых гостей меняются перед запуском.",
            color=C.secondary,fontSize=13.sp)
        SuitePicker(selected=suite,onSelected={suite=it})
        if(staff.isNotEmpty())B241Pm1Picker(staff,selectedPm1,onPm1Selected)

        OutlinedTextField(value=guests,onValueChange={guests=it.filter(Char::isDigit).take(2)},
            modifier=Modifier.fillMaxWidth(),
            label={Text("Фактическое количество гостей (1–30)")},singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        if(programmeId!="grand"){
            OutlinedTextField(value=capacity,
                onValueChange={capacity=it.filter(Char::isDigit).take(1)},
                modifier=Modifier.fillMaxWidth(),
                label={Text("Одновременно обслуживаемых гостей (1–8)")},singleLine=true,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            Text("Волн индивидуальных парений: ${preview.waves}. Одна волна — один плановый блок, не автоматически выполненное парение каждого гостя.",
                color=C.cream,fontSize=13.sp)
        } else {
            Text("Каждое коллективное парение — примерно 50 минут целиком, включая паузу 5–7 минут на аперитив.",
                color=C.cream,fontSize=13.sp)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={if(extraHours>0)extraHours--},
                enabled=extraHours>0){Text("−",color=C.amber)}
            Text("Согласованных дополнительных часов: $extraHours",
                modifier=Modifier.weight(1f),color=C.secondary,fontSize=13.sp)
            OutlinedButton(onClick={if(extraHours<3)extraHours++},
                enabled=extraHours<3){Text("+",color=C.amber)}
        }
        Text("Допчас до запуска — только после согласования с гостем. Оплата автоматически не начисляется.",
            color=C.muted,fontSize=12.sp)

        Text("Плановые этапы",color=C.white,fontSize=20.sp,
            fontWeight=FontWeight.SemiBold)
        if(preview.steps.isNotEmpty())preview.steps.forEachIndexed{index,stage->
            Column(Modifier.fillMaxWidth().background(C.panel,shape)
                .border(1.dp,C.border,shape).padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)){
                Text("${index+1}. ${stage.title}",color=C.cream,fontSize=14.sp,
                    fontWeight=FontWeight.SemiBold)
                if(stage.adjustable){
                    OutlinedTextField(
                        value=minutesText[stage.id]?:stage.minutes.toString(),
                        onValueChange={value->
                            minutesText=minutesText.toMutableMap().also{
                                it[stage.id]=value.filter(Char::isDigit).take(3)
                            }
                        },
                        modifier=Modifier.fillMaxWidth(),
                        singleLine=true,
                        label={Text("План, минут")},
                        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number)
                    )
                }else{
                    Text("${stage.minutes} мин · остаточное свободное время",
                        color=C.bronze,fontSize=13.sp)
                }
            }
        }
        val summary=if(preview.packageMinutes>0)
            "Тарифная длительность: ${preview.packageMinutes} мин · согласованный план: ${preview.bookedMinutes} мин"
            else "Маршрут не задан"
        Text(summary,color=C.amber,fontSize=14.sp)
        if(preview.problem!=null)
            Text(preview.problem,color=C.overrun,fontSize=14.sp)
        if(validationError!=null)
            Text(validationError,color=C.overrun,fontSize=13.sp)
        if(preview.valid){
            Text("Основные этапы: ${preview.fixedMinutes} мин. " +
                "Свободный отдых: ${preview.availableMinutes} мин.",
                color=C.fact,fontSize=14.sp)
        }
        if(programmeId=="premium")
            Text("SPA и массаж могут идти параллельно. После запуска добавляй их через «+ Процедура» и выбирай конкретного специалиста либо общую «SPA-процедуру».",
                color=C.secondary,fontSize=13.sp)
        Text("После завершения гостевой программы выполняется уборка. Она не включена в тарифное время; отдельный журнал послепрограммной уборки предусмотрим позднее.",
            color=C.secondary,fontSize=13.sp)
        if(notice!=null)Text(notice,color=C.amber,fontSize=13.sp)
        ParitmPrimaryButton(
            label="Сохранить план и начать программу →",
            onClick={onCreate(StandardProgrammeRoute.toPlan(draft),
                suite.trim().takeIf{it.isNotBlank()},draft.guests)},
            enabled=canStart,modifier=Modifier.fillMaxWidth()
        )
        Text("При запуске план фиксируется как snapshot в Room. Завершённые процедуры не создаются автоматически. Дополнительные процедуры и задержки — на экране действующей программы.",
            color=C.muted,fontSize=12.sp)
        Spacer(Modifier.height(30.dp))
    }
}
