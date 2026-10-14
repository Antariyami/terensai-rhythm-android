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
import kz.terensai.rhythm.domain.CustomProgrammeDraft
import kz.terensai.rhythm.domain.CustomProgrammePlan
import kz.terensai.rhythm.domain.CustomStageInput
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.Track
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton

/**
 * P1.0: create a genuinely executable custom route, without presenting it as an
 * approved TERENSAI template. The existing SessionRepository stores its frozen
 * stage plan and events; changes to an active route are a separate milestone.
 */
@Composable
fun P10CustomProgrammeScreen(
    staff:List<StaffRow>,
    selectedPm1:String?,
    notice:String?,
    pending:Boolean,
    onPm1Selected:(String?)->Unit,
    onBack:()->Unit,
    onCreate:(ProgramPlan,String?,Int?)->Unit
){
    var title by rememberSaveable { mutableStateOf("") }
    var names by rememberSaveable { mutableStateOf(listOf("")) }
    var minutes by rememberSaveable { mutableStateOf(listOf("")) }
    var tracks by rememberSaveable { mutableStateOf(listOf("GUEST")) }
    var suite by rememberSaveable { mutableStateOf("") }
    var guests by rememberSaveable { mutableStateOf("") }
    val draft=CustomProgrammeDraft(title,
        names.indices.map{i->CustomStageInput(
            names[i],minutes[i].toIntOrNull(),
            if(tracks[i]=="MASTER")Track.MASTER else Track.GUEST
        )}
    )
    val problem=CustomProgrammePlan.validate(draft)
    val guestCount=guests.toIntOrNull()
    val guestsValid=guests.isBlank() || guestCount in 1..30
    val total=draft.stages.sumOf{it.minutes?:0}

    Column(Modifier.fillMaxSize().background(C.canvas)
        .verticalScroll(rememberScrollState())
        .padding(horizontal=18.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)){
        TextButton(onClick=onBack){Text("‹ Моя смена",color=C.amber)}
        Text("ПАРИТМ · СВОЙ МАРШРУТ",color=C.bronze,fontSize=12.sp)
        Text("Своя программа",color=C.white,fontSize=28.sp,fontWeight=FontWeight.Bold)
        Text("Каждый этап и его длительность вводишь сам. Это личный маршрут, а не утверждённый стандарт TERENSAI.",
            color=C.secondary,fontSize=13.sp)
        OutlinedTextField(
            value=title,onValueChange={title=it.take(80)},
            modifier=Modifier.fillMaxWidth(),singleLine=true,
            label={Text("Название программы")}
        )
        SuitePicker(selected=suite,onSelected={suite=it})
        if(staff.isNotEmpty())B241Pm1Picker(staff,selectedPm1,onPm1Selected)
        OutlinedTextField(
            value=guests,onValueChange={guests=it.filter(Char::isDigit).take(2)},
            modifier=Modifier.fillMaxWidth(),singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
            isError=!guestsValid,label={Text("Гостей (необязательно, 1–30)")}
        )
        Text("Этапы · ${names.size}/${CustomProgrammePlan.MAX_STAGES}",
            color=C.cream,fontSize=19.sp,fontWeight=FontWeight.SemiBold)
        names.indices.forEach { index->
            Column(Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(16.dp))
                .border(1.dp,C.border,RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(Modifier.fillMaxWidth()){
                    Text("Этап ${index+1}",Modifier.weight(1f),color=C.amber,
                        fontWeight=FontWeight.Bold)
                    if(names.size>1)TextButton(onClick={
                        names=names.filterIndexed{i,_->i!=index}
                        minutes=minutes.filterIndexed{i,_->i!=index}
                        tracks=tracks.filterIndexed{i,_->i!=index}
                    }){Text("Удалить",color=C.overrun)}
                }
                OutlinedTextField(value=names[index],onValueChange={value->
                    names=names.toMutableList().also{it[index]=value.take(100)}
                },modifier=Modifier.fillMaxWidth(),singleLine=true,
                    label={Text("Название этапа: например, парение")})
                OutlinedTextField(value=minutes[index],onValueChange={value->
                    minutes=minutes.toMutableList().also{it[index]=value.filter(Char::isDigit).take(3)}
                },modifier=Modifier.fillMaxWidth(),singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    label={Text("Минуты (1–240)")})
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={
                        tracks=tracks.toMutableList().also{it[index]="GUEST"}
                    },modifier=Modifier.weight(1f)){
                        Text("Гости",color=if(tracks[index]=="GUEST")C.fact else C.secondary)
                    }
                    OutlinedButton(onClick={
                        tracks=tracks.toMutableList().also{it[index]="MASTER"}
                    },modifier=Modifier.weight(1f)){
                        Text("Мастер",color=if(tracks[index]=="MASTER")C.fact else C.secondary)
                    }
                }
            }
        }
        OutlinedButton(
            onClick={
                names=names+"";minutes=minutes+"";tracks=tracks+"GUEST"
            },
            enabled=names.size<CustomProgrammePlan.MAX_STAGES,
            modifier=Modifier.fillMaxWidth().heightIn(min=50.dp)
        ){Text("+ Добавить этап",color=C.amber)}
        Text("Общий план: $total мин",color=C.cream,fontSize=18.sp)
        if(problem!=null)Text(problem,color=C.overrun,fontSize=12.sp)
        if(!guestsValid)Text("Проверь количество гостей",color=C.overrun,fontSize=12.sp)
        if(notice!=null)Text(notice,color=C.amber,fontSize=12.sp)
        ParitmPrimaryButton(
            label="Сохранить маршрут и начать →",
            onClick={onCreate(CustomProgrammePlan.toPlan(draft),
                suite.trim().takeIf{it.isNotEmpty()},guestCount)},
            enabled=problem==null && guestsValid && !pending,
            modifier=Modifier.fillMaxWidth()
        )
        Text("После запуска маршрут сохраняется в Room вместе со своими этапами. " +
            "Изменение порядка уже запущенных этапов появится позже.",
            color=C.secondary,fontSize=12.sp)
        Spacer(Modifier.height(20.dp))
    }
}
