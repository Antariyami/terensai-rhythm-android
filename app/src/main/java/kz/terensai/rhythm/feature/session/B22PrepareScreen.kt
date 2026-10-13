package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.R
import kz.terensai.rhythm.data.room.StaffRow
import kz.terensai.rhythm.feature.entry.ParitmAtmosphere
import kz.terensai.rhythm.domain.PilotTanysuPlan
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton
import kz.terensai.rhythm.ui.theme.ParitmColors as C

@Composable
fun B22PrepareScreen(
    notice: String?, pending: Boolean, onBack: () -> Unit,
    onStart: (List<Int?>, String?, Int?) -> Unit,
    staff: List<StaffRow> = emptyList(),
    selectedPm1: String? = null,
    onPm1Selected: (String?) -> Unit = {}
) {
    var minutes by rememberSaveable {
        mutableStateOf(PilotTanysuPlan.defaults.map { it.minutes.toString() })
    }
    var suite by rememberSaveable { mutableStateOf("") }
    var guests by rememberSaveable { mutableStateOf("") }
    val values = minutes.map { it.toIntOrNull() }
    val route = PilotTanysuPlan.evaluate(values)
    val guestNumber = if (guests.isBlank()) null else guests.toIntOrNull()
    val guestsValid = guests.isBlank() || (guestNumber != null && guestNumber in 1..30)
    val shape = RoundedCornerShape(18.dp)

    Box(Modifier.fillMaxSize().background(C.canvas)) {
    ParitmAtmosphere()
    Column(Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(horizontal=20.dp, vertical=14.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack) { Text("‹ Назад",color=C.amber) }
            Spacer(Modifier.weight(1f))
            Image(painterResource(R.drawable.paritm_approved_mark_hq),
                contentDescription="Логотип ПАРИТМ", modifier=Modifier.size(45.dp))
        }
        Text("ПОДГОТОВКА СЕССИИ",fontSize=12.sp,color=C.bronze,
            letterSpacing=2.sp,fontWeight=FontWeight.Bold)
        Text("Танысу",fontSize=34.sp,color=C.white,fontWeight=FontWeight.Bold)
        Text("3 часа · 3 коллективных парения",color=C.amber,fontSize=16.sp)
        Box(Modifier.fillMaxWidth().background(C.raised,shape)
            .border(1.dp,C.border,shape).padding(15.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Параметры",color=C.white,fontWeight=FontWeight.SemiBold)
                SuitePicker(selected=suite,onSelected={suite=it})
                if(staff.isNotEmpty()) B241Pm1Picker(staff,selectedPm1,onPm1Selected)
                Text("Вместимость сьюта — ориентир; фактическое количество гостей можно указать вручную.",color=C.secondary,fontSize=12.sp)
                OutlinedTextField(guests,{guests=it.filter(Char::isDigit).take(2)},
                    modifier=Modifier.fillMaxWidth(),singleLine=true,
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
                    isError=!guestsValid,label={Text("Гостей с детьми (1–30)")})
            }
        }
        Text("Личный пилотный маршрут",color=C.cream,
            fontWeight=FontWeight.SemiBold,fontSize=19.sp)
        Text("Минуты меняются кнопками −1/+1, −5/+5 или вручную. План остаётся 180 минут. Это личный пилот, не регламент TERENSAI.",
            color=C.secondary,fontSize=13.sp,lineHeight=18.sp)
        PilotTanysuPlan.defaults.forEachIndexed { index, stage ->
            Column(Modifier.fillMaxWidth().background(C.panel,shape)
                .border(1.dp,C.border,shape).padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Box(Modifier.size(35.dp).background(C.raised,RoundedCornerShape(10.dp)),
                        contentAlignment=Alignment.Center) {
                        Text((index+1).toString(),color=C.amber,fontWeight=FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(stage.label,Modifier.weight(1f),fontSize=14.sp,color=C.white,
                        lineHeight=18.sp)
                }
                MinuteStepper(values=minutes,index=index,onUpdate={minutes=it})
            }
        }
        Box(Modifier.fillMaxWidth().background(C.raised,shape).padding(14.dp)) {
            Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                Text("Свободное время и завершение: ${route.remainingMinutes} мин",
                    color=if(route.valid) C.fact else C.overrun,
                    fontWeight=FontWeight.SemiBold)
                Text(route.message,color=C.secondary,fontSize=12.sp)
                Text("Итог: 180 минут",color=C.amber)
            }
        }
        if (!guestsValid) Text("Проверь количество гостей",color=C.overrun)
        if (notice!=null) Text(notice,color=C.bronze,fontSize=13.sp)
        ParitmPrimaryButton("Начать программу →",
            onClick={onStart(values,suite.trim().takeIf{it.isNotEmpty()},guestNumber)},
            enabled=route.valid&&guestsValid&&!pending,
            modifier=Modifier.fillMaxWidth())
        Text("После запуска маршрут сохраняется как неизменяемая версия в Room.",
            color=C.muted,fontSize=12.sp)
        Spacer(Modifier.height(18.dp))
    }
    }
}
