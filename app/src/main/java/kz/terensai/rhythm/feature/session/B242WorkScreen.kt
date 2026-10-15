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
import kz.terensai.rhythm.data.room.StaffRow
import kz.terensai.rhythm.ui.theme.ParitmColors as C

@Composable
fun B242WorkScreen(sessions:List<SavedSession>,work:WorkView?,pending:Boolean,notice:String?,
    onBack:()->Unit,onCreate:(String?,String,String,Int,String?,String?,String?)->Unit,
    onAction:(String)->Unit,onNew:()->Unit,onJournal:()->Unit = {},masters:List<StaffRow>,
    allWorks:List<WorkView> = emptyList(),onOpenWork:(String)->Unit = {}){
    var sessionId by remember {mutableStateOf<String?>(null)}
    var role by remember {mutableStateOf("PM")}
    var procedure by remember {mutableStateOf("INDIVIDUAL_STEAM")}
    var minutes by remember {mutableStateOf("45")}
    var expanded by remember {mutableStateOf(false)}
    var chosenPm1 by remember { mutableStateOf<String?>(null) }
    var pmExpanded by remember { mutableStateOf(false) }
    var suite by remember { mutableStateOf("") }
    var programme by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(C.canvas).verticalScroll(rememberScrollState())
        .padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick=onBack){Text("‹ Моя смена",color=C.amber)}
        Text("ПАРИТМ · МОЁ УЧАСТИЕ",color=C.bronze,fontSize=12.sp)
        Text("Личная процедура",color=C.white,fontSize=27.sp)
        OutlinedButton(onClick=onJournal,modifier=Modifier.fillMaxWidth()){
            Text("Открыть журнал и отчёты →",color=C.amber)
        }
        if(notice!=null)Text(notice,color=C.amber)
        val ongoing=allWorks.filter{it.state!="COMPLETED"}
            .sortedBy { when(it.state){"RUNNING"->0;"PAUSED"->1;else->2} }
        val finished=allWorks.filter{it.state=="COMPLETED"}
        Text("Незавершённые процедуры · ${ongoing.size}",color=C.cream)
        if(ongoing.isEmpty()) Text("Пока нет незавершённых процедур",color=C.secondary)
        ongoing.forEach { item ->
            val label=workLabel(item.row.procedureType,item.row.serviceLabelSnapshot).ifBlank{"Процедура"}
            /* val legacyLabel=when(item.row.procedureType){
                "INDIVIDUAL_STEAM"->"Индивидуальное парение"
                "COLLECTIVE_STEAM"->"Коллективное парение"
                "MASSAGE"->"Массаж"
                "PEELING"->"Пилинг"
                "WASHING"->"Помывка веником"
                else->"Другая работа"
            } */ 
            val state=when(item.state){"RUNNING"->"ИДЁТ";"PAUSED"->"ПАУЗА";else->"ОЖИДАЕТ"}
            OutlinedButton(onClick={onOpenWork(item.row.id)},
                modifier=Modifier.fillMaxWidth().heightIn(min=53.dp)){
                Column {
                    Text("$label · $state",color=if(item.state=="RUNNING")C.fact else C.amber)
                    Text("Факт ${formatElapsed(item.elapsedMs)} · ${item.row.suiteNameSnapshot?:"Локальная программа"}",
                        color=C.secondary,fontSize=11.sp)
                }
            }
        }
        var showFinished by remember { mutableStateOf(false) }
        TextButton(onClick={showFinished=!showFinished}) {
            Text((if(showFinished)"▾" else "▸")+" Завершённые · ${finished.size}",color=C.cream)
        }
        if(showFinished) {
            if(finished.isEmpty()) Text("Завершённых процедур пока нет",color=C.secondary)
            finished.forEach { item ->
                OutlinedButton(onClick={onOpenWork(item.row.id)},modifier=Modifier.fillMaxWidth()){
                    Text((when(item.row.procedureType){
                        "INDIVIDUAL_STEAM"->"Индивидуальное парение"
                        "COLLECTIVE_STEAM"->"Коллективное парение"
                        "MASSAGE"->"Массаж"
                        else->"Процедура"
                    })+" · завершена · ${formatElapsed(item.elapsedMs)}",color=C.secondary)
                }
            }
        }
        if(work!=null){
            TextButton(onClick=onNew) {Text("+ Новая процедура",color=C.amber)}
        }
        if(work==null){
            Text("Программа",color=C.cream)
            Box {
                OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){
                    Text(sessions.firstOrNull{it.id==sessionId}?.let{it.programmeTitle+" · "+(it.metadata.suite?:"Без сьюта")}
                        ?: "Участвовать в программе другого PM1",color=C.white)
                }
                DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                    DropdownMenuItem(text={Text("У другого PM1 · внешняя программа")},onClick={sessionId=null;expanded=false})
                    sessions.forEach {session->
                        DropdownMenuItem(text={Text(session.programmeTitle+" · "+(session.metadata.suite?:"Без сьюта"))},
                            onClick={sessionId=session.id;expanded=false})
                    }
                }
            }
            if(sessionId==null){
                Text("Ведущий программы · PM1",color=C.cream)
                Box {
                    OutlinedButton(onClick={pmExpanded=true},modifier=Modifier.fillMaxWidth()){
                        Text(masters.firstOrNull{it.id==chosenPm1}?.displayName ?: "Выбрать PM1 ▾",color=C.white)
                    }
                    DropdownMenu(expanded=pmExpanded,onDismissRequest={pmExpanded=false}){
                        masters.forEach { master ->
                            DropdownMenuItem(text={Text(master.displayName)},onClick={
                                chosenPm1=master.id;pmExpanded=false
                            })
                        }
                    }
                }
                SuitePicker(selected=suite,onSelected={suite=it})
                OutlinedTextField(value=programme,onValueChange={programme=it.take(80)},
                    modifier=Modifier.fillMaxWidth(),label={Text("Название программы (необязательно)")})
                Text("Дата и начало работы фиксируются при нажатии «Начать процедуру».",color=C.secondary)
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
            Button(enabled=!pending && (minutes.toIntOrNull()?:0) in 1..240 && (sessionId!=null || (chosenPm1!=null && suite.isNotBlank())),
                onClick={onCreate(sessionId,role,procedure,minutes.toInt(),chosenPm1,suite,programme)},modifier=Modifier.fillMaxWidth()){
                Text("Создать процедуру")
            }
        }else{
            Text("Моя роль: ${work.row.role}",color=C.cream)
            Text("Процедура: "+workLabel(work.row.procedureType,work.row.serviceLabelSnapshot),color=C.cream)
            /* Text("Процедура: "+when(work.row.procedureType){
                "INDIVIDUAL_STEAM"->"Индивидуальное парение"
                "COLLECTIVE_STEAM"->"Коллективное парение"
                "MASSAGE"->"Массаж"
                "PEELING"->"Пилинг"
                "WASHING"->"Помывка веником"
                else->"Другая работа"
            },color=C.cream) */
            if(work.row.pm1NameSnapshot!=null) Text("PM1: ${work.row.pm1NameSnapshot}",color=C.cream)
            if(work.row.suiteNameSnapshot!=null) Text("Сьют: ${work.row.suiteNameSnapshot}",color=C.cream)
            val startedAt=work.events.firstOrNull{it.kind=="START"}?.atMs
            if(startedAt!=null) {
                val stamp=java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",java.util.Locale("ru","RU"))
                    .format(java.util.Date(startedAt))
                Text("Начало процедуры: $stamp",color=C.secondary)
            }
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
            Text("При запуске другого личного таймера этот автоматически встанет на паузу. Общая программа не остановится.",
                color=C.secondary)
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun workLabel(type:String,service:String?):String =
    service?.takeIf{it.isNotBlank()} ?: kz.terensai.rhythm.data.WorkJournalLabels.procedure(type)
