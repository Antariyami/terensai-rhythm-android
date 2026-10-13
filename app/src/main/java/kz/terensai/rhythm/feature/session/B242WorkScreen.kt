package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.data.WorkView
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.ui.theme.ParitmColors as C

@Composable
fun B242WorkScreen(sessions:List<SavedSession>,work:WorkView?,pending:Boolean,notice:String?,
    onBack:()->Unit,onCreate:(String?,String,Int)->Unit,onAction:(String)->Unit,onNew:()->Unit){
    var sessionId by remember {mutableStateOf<String?>(null)}
    var role by remember {mutableStateOf("PM")}
    var procedure by remember {mutableStateOf("INDIVIDUAL_STEAM")}
    var minutes by remember {mutableStateOf("45")}
    var expanded by remember {mutableStateOf(false)}
    Column(Modifier.fillMaxSize().background(C.canvas).verticalScroll(rememberScrollState())
        .padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick=onBack){Text("‹ Моя смена",color=C.amber)}
        Text("ПАРИТМ · МОЁ УЧАСТИЕ",color=C.bronze,fontSize=12.sp)
        Text("Личная процедура",color=C.white,fontSize=27.sp)
        if(notice!=null)Text(notice,color=C.amber)
        if(work==null){
            Text("Программа",color=C.cream)
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
                    Text(sessions.firstOrNull{it.id==sessionId}?.let{it.programmeTitle+" · "+(it.metadata.suite?:"Без сьюта")}
                        ?: "Участие без локальной программы",color=C.white)
                }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                    DropdownMenuItem(text={Text("Без локальной программы")},onClick={sessionId=null;expanded=false})
                    sessions.forEach {session->
                        DropdownMenuItem(text={Text(session.programmeTitle+" · "+(session.metadata.suite?:"Без сьюта"))},
                            onClick={sessionId=session.id;expanded=false})
                    }
                }
            }
            Text("Моя роль",color=C.cream)
            Row {
                listOf("PM","SPA","PM1").forEach { item->
                    FilterChip(selected=role==item,onClick={role=item},
                        label={Text(item)},modifier=Modifier.padding(end=8.dp))
                }
            }
            Text("Вид процедуры",color=C.cream)
            listOf("INDIVIDUAL_STEAM" to "Индивидуальное парение",
                "COLLECTIVE_STEAM" to "Коллективное парение",
                "MASSAGE" to "Массаж","PEELING" to "Пилинг",
                "WASHING" to "Помывка веником","OTHER" to "Другая работа").forEach {(key,label)->
                Row {
                    RadioButton(selected=procedure==key,onClick={procedure=key})
                    Text(label,color=C.white,modifier=Modifier.padding(top=13.dp))
                }
            }
            Text("Плановая продолжительность, минуты",color=C.cream)
            Row {
                OutlinedButton(onClick={minutes=((minutes.toIntOrNull()?:45)-5).coerceAtLeast(1).toString()}){Text("−5")}
                OutlinedTextField(value=minutes,onValueChange={minutes=it.filter(Char::isDigit).take(3)},
                    modifier=Modifier.weight(1f),singleLine=true)
                OutlinedButton(onClick={minutes=((minutes.toIntOrNull()?:45)+5).coerceAtMost(240).toString()}){Text("+5")}
            }
            Button(enabled=!pending && (minutes.toIntOrNull()?:0) in 1..240,
                onClick={onCreate(sessionId,role,minutes.toInt())},modifier=Modifier.fillMaxWidth()){
                Text("Создать процедуру")
            }
        }else{
            Text("Моя роль: ${work.row.role}",color=C.cream)
            Text("План: ${work.row.plannedMs/60000} мин",color=C.amber)
            Text("Факт: ${formatElapsed(work.elapsedMs)}",fontSize=37.sp,color=C.fact)
            Text("Состояние: "+when(work.state){"READY"->"ожидает запуска";"RUNNING"->"идёт";"PAUSED"->"пауза";else->"завершена"},
                color=C.white)
            val next=when(work.state){"READY"->"START";"RUNNING"->"PAUSE";"PAUSED"->"RESUME";else->null}
            if(next!=null) Button(enabled=!pending,onClick={onAction(next)},
                modifier=Modifier.fillMaxWidth().heightIn(min=56.dp)){
                Text(when(next){"START"->"Начать процедуру";"PAUSE"->"Пауза";else->"Продолжить"})
            }
            if(work.state=="RUNNING"||work.state=="PAUSED"){
                OutlinedButton(enabled=!pending,onClick={onAction("COMPLETE")},
                    modifier=Modifier.fillMaxWidth()){Text("Завершить мою процедуру")}
            }
            if(work.state=="COMPLETED"){
                OutlinedButton(onClick=onNew,modifier=Modifier.fillMaxWidth()){
                    Text("+ Ещё одна процедура")
                }
            }
            Text("Общая программа не останавливается при завершении участия.",
                color=C.secondary)
        }
        Spacer(Modifier.height(32.dp))
    }
}
