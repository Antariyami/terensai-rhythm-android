package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.UUID
import kz.terensai.rhythm.data.ProgrammeActionState
import kz.terensai.rhythm.data.ProgrammeAction
import kz.terensai.rhythm.data.WorkView
import kz.terensai.rhythm.data.room.StaffRow
import kz.terensai.rhythm.domain.TerensaiServiceCatalog
import kz.terensai.rhythm.ui.theme.ParitmColors as C

/** Session actions operate beside the immutable programme stage timeline. */
@Composable
fun P24LiveActions(
    suite:String?,
    masters:List<StaffRow>,
    works:List<WorkView>,
    actions:ProgrammeActionState,
    currentRunningStageId:String?,
    pending:Boolean,
    onCreateProcedure:(String,String,String,Int,String)->Unit,
    onWorkCommand:(String,String)->Unit,
    onBeginEvent:(String,String,String?,String)->Unit,
    onEndEvent:(String,String)->Unit,
    onCorrectEvent:(String,Int,String,String)->Unit,
    onExtraHour:(String)->Unit
) {
    var showAdd by rememberSaveable{mutableStateOf(false)}
    var showEvent by rememberSaveable{mutableStateOf(false)}
    var showExtraHour by rememberSaveable{mutableStateOf(false)}
    var editEventId by rememberSaveable{mutableStateOf<String?>(null)}
    var serviceId by rememberSaveable{mutableStateOf("massage_saryarka")}
    var selectedMaster by rememberSaveable{mutableStateOf<String?>(null)}
    var selectedRole by rememberSaveable{mutableStateOf("SPA")}
    var minutes by rememberSaveable{mutableStateOf("30")}
    var serviceDropdown by remember{mutableStateOf(false)}
    var masterDropdown by remember{mutableStateOf(false)}
    var eventKind by rememberSaveable{mutableStateOf("GUEST_WAIT")}
    var eventDropdown by remember{mutableStateOf(false)}
    var comment by rememberSaveable{mutableStateOf("")}
    var pauseCurrent by rememberSaveable{mutableStateOf(false)}
    var correctionMinutes by rememberSaveable{mutableStateOf("")}
    var correctionReason by rememberSaveable{mutableStateOf("")}
    val choices=TerensaiServiceCatalog.entries
    val currentService=TerensaiServiceCatalog.byId(serviceId)?:choices.first()
    val availableMasters=masters.filter{!it.archived}
    val chosen=availableMasters.firstOrNull{it.id==selectedMaster}
    val eventTypes=listOf(
        "GUEST_WAIT" to "Ожидание гостя",
        "TECH_WAIT" to "Техническая задержка",
        "OTHER_WAIT" to "Другая причина"
    )

    Column(Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(18.dp))
        .padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        Text("Живые действия программы",color=C.cream,fontSize=18.sp)
        Text("Дополнительные услуги, задержки и продления сохраняются отдельно от штатного маршрута.",
            color=C.secondary,fontSize=12.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.fillMaxWidth()){
            OutlinedButton(onClick={
                selectedMaster=masters.firstOrNull{it.isMyProfile && !it.archived}?.id
                    ?:availableMasters.firstOrNull()?.id
                minutes=currentService.suggestedMinutes.toString()
                showAdd=true
            },enabled=!pending,modifier=Modifier.weight(1f)){
                Text("+ Процедура",color=C.amber)
            }
            OutlinedButton(onClick={showEvent=true},enabled=!pending,
                modifier=Modifier.weight(1f)){
                Text("+ Событие",color=C.amber)
            }
        }
        if(actions.items.isNotEmpty()){
            Text("События на временной шкале",color=C.cream,fontSize=14.sp)
            actions.items.asReversed().take(10).forEach{item->
                Text("${eventTitle(item.kind)} · ${item.effectiveDurationMs/60_000L} мин"+
                    (if(item.active)" · идёт" else ""),
                    color=if(item.active)C.fact else C.secondary,fontSize=13.sp)
                if(item.description.isNotBlank() && item.kind!="EXTRA_HOUR")
                    Text(item.description.take(100),color=C.muted,fontSize=12.sp)
                if(item.active){
                    OutlinedButton(onClick={onEndEvent(item.id,UUID.randomUUID().toString())},
                        enabled=!pending,modifier=Modifier.fillMaxWidth()){
                        Text("Завершить задержку",color=C.amber)
                    }
                } else if(item.kind!="EXTRA_HOUR"){
                    TextButton(onClick={
                        editEventId=item.id
                        correctionMinutes=(item.effectiveDurationMs/60000L).toString()
                        correctionReason=""
                    },enabled=!pending){Text("Исправить длительность и причину",color=C.bronze)}
                }
            }
        }
        if(works.isNotEmpty()){
            Text("Дополнительные и личные процедуры",color=C.cream,fontSize=14.sp)
            works.take(12).forEach{work->
                val worker=masters.firstOrNull{it.id==work.row.staffId}?.displayName?:"Мастер"
                val title=work.row.serviceLabelSnapshot
                    ?:kz.terensai.rhythm.data.WorkJournalLabels.procedure(work.row.procedureType)
                Text("$title · $worker · ${work.elapsedMs/60000L} мин · ${workState(work.state)}",
                    color=if(work.state=="RUNNING")C.fact else C.secondary,fontSize=13.sp)
                val next=when(work.state){
                    "READY"->"START"
                    "RUNNING"->"PAUSE"
                    "PAUSED"->"RESUME"
                    else->null
                }
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    if(next!=null)TextButton(
                        onClick={onWorkCommand(work.row.id,next)},enabled=!pending){
                        Text(when(next){"START"->"Начать";"PAUSE"->"Пауза";else->"Продолжить"},
                            color=C.amber)
                    }
                    if(work.state=="RUNNING"||work.state=="PAUSED")
                        TextButton(onClick={onWorkCommand(work.row.id,"COMPLETE")},
                            enabled=!pending){Text("Завершить",color=C.amber)}
                }
            }
        }
        val hourPrice=TerensaiServiceCatalog.extraHourPriceKzt(suite)
        OutlinedButton(onClick={showExtraHour=true},enabled=!pending,
            modifier=Modifier.fillMaxWidth()){
            Text("+ Дополнительный час"+
                (hourPrice?.let{" · ${it/1000} 000 ₸ по прайсу"}?:""),color=C.bronze)
        }
        if(actions.extraHours>0)
            Text("Продлено: +${actions.extraHours} ч. К оплате и выручке автоматически не прибавляется.",
                color=C.secondary,fontSize=12.sp)
    }

    if(showAdd)AlertDialog(onDismissRequest={showAdd=false},containerColor=C.panel,
        title={Text("Добавить процедуру",color=C.white)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("Услуга TERENSAI",color=C.cream)
                Box{
                    OutlinedButton(onClick={serviceDropdown=true},modifier=Modifier.fillMaxWidth()){
                        Text(currentService.title,color=C.amber)
                    }
                    DropdownMenu(expanded=serviceDropdown,onDismissRequest={serviceDropdown=false}){
                        choices.forEach{item->
                            DropdownMenuItem(text={Text(item.title)},onClick={
                                serviceId=item.id
                                minutes=item.suggestedMinutes.toString()
                                selectedRole=if(item.workType=="MASSAGE" || item.workType=="PEELING" ||
                                    item.workType=="WASHING" || item.workType=="OTHER")"SPA" else "PM"
                                serviceDropdown=false
                            })
                        }
                    }
                }
                Text("Исполнитель (может быть другим специалистом)",color=C.cream)
                Box{
                    OutlinedButton(onClick={masterDropdown=true},modifier=Modifier.fillMaxWidth()){
                        Text(chosen?.displayName?:"Выбрать мастера",color=C.amber)
                    }
                    DropdownMenu(expanded=masterDropdown,onDismissRequest={masterDropdown=false}){
                        availableMasters.forEach{member->
                            DropdownMenuItem(text={Text(member.displayName)},onClick={
                                selectedMaster=member.id;masterDropdown=false
                            })
                        }
                    }
                }
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    listOf("PM","SPA","PM1").forEach{role->
                        FilterChip(selected=selectedRole==role,onClick={selectedRole=role},
                            label={Text(role)})
                    }
                }
                OutlinedTextField(value=minutes,
                    onValueChange={minutes=it.filter(Char::isDigit).take(3)},
                    label={Text("Плановая длительность, мин.")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                Text("Справочно: ${currentService.retailKzt} ₸. Фактическая услуга учтётся только после нажатия «Завершить».",
                    color=C.secondary,fontSize=12.sp)
            }
        },
        confirmButton={TextButton(enabled=!pending && chosen!=null &&
                (minutes.toIntOrNull()?:0) in 1..240,onClick={
            val id=selectedMaster?:return@TextButton
            showAdd=false
            onCreateProcedure(id,currentService.id,selectedRole,minutes.toInt(),
                UUID.randomUUID().toString())
        }){Text("Добавить",color=C.amber)}},
        dismissButton={TextButton(onClick={showAdd=false}){Text("Отмена",color=C.white)}}
    )
    if(showEvent)AlertDialog(onDismissRequest={showEvent=false},containerColor=C.panel,
        title={Text("Новое событие",color=C.white)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                Box{
                    OutlinedButton(onClick={eventDropdown=true}){
                        Text(eventTitle(eventKind),color=C.amber)
                    }
                    DropdownMenu(expanded=eventDropdown,onDismissRequest={eventDropdown=false}){
                        eventTypes.forEach{(type,name)->
                            DropdownMenuItem(text={Text(name)},onClick={
                                eventKind=type;eventDropdown=false
                            })
                        }
                    }
                }
                OutlinedTextField(value=comment,onValueChange={comment=it.take(300)},
                    modifier=Modifier.fillMaxWidth(),label={Text("Комментарий / причина")})
                if(currentRunningStageId!=null)Row(verticalAlignment=Alignment.CenterVertically){
                    Checkbox(checked=pauseCurrent,onCheckedChange={pauseCurrent=it})
                    Text("Поставить текущий этап на паузу",color=C.cream,fontSize=13.sp)
                }
                Text("Если этап оставить работающим, его таймер продолжится. Пауза не прерывает время всей программы.",
                    color=C.secondary,fontSize=12.sp)
            }
        },
        confirmButton={TextButton(enabled=!pending,onClick={
            showEvent=false
            onBeginEvent(eventKind,comment,
                if(pauseCurrent) currentRunningStageId else null,
                UUID.randomUUID().toString())
            comment="";pauseCurrent=false
        }){Text("Начать отсчёт",color=C.amber)}},
        dismissButton={TextButton(onClick={showEvent=false}){Text("Отмена",color=C.white)}}
    )
    if(showExtraHour)AlertDialog(onDismissRequest={showExtraHour=false},containerColor=C.panel,
        title={Text("Продлить программу на час?",color=C.white)},
        text={Text("Новая плановая граница будет на 60 минут позже. " +
            "Это не создаёт выполненную SPA-процедуру и не начисляет оплату.",
            color=C.secondary)},
        confirmButton={TextButton(enabled=!pending,onClick={
            showExtraHour=false;onExtraHour(UUID.randomUUID().toString())
        }){Text("+60 минут",color=C.amber)}},
        dismissButton={TextButton(onClick={showExtraHour=false}){Text("Отмена",color=C.white)}}
    )
    val correcting=actions.items.firstOrNull{it.id==editEventId}
    if(correcting!=null)AlertDialog(onDismissRequest={editEventId=null},containerColor=C.panel,
        title={Text("Исправить длительность события",color=C.white)},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text("${eventTitle(correcting.kind)}: ранее ${correcting.effectiveDurationMs/60000L} мин",
                color=C.cream)
            OutlinedTextField(value=correctionMinutes,
                onValueChange={correctionMinutes=it.filter(Char::isDigit).take(4)},
                label={Text("Фактические минуты")})
            OutlinedTextField(value=correctionReason,onValueChange={correctionReason=it.take(300)},
                label={Text("Причина исправления")})
        }},
        confirmButton={TextButton(enabled=!pending &&
            (correctionMinutes.toIntOrNull()?:-1) in 0..1440 &&
            correctionReason.trim().length>=5,onClick={
                val id=editEventId?:return@TextButton
                editEventId=null
                onCorrectEvent(id,correctionMinutes.toInt(),correctionReason,
                    UUID.randomUUID().toString())
        }){Text("Записать исправление",color=C.amber)}},
        dismissButton={TextButton(onClick={editEventId=null}){Text("Отмена",color=C.white)}}
    )
}
private fun eventTitle(kind:String)=when(kind){
    "GUEST_WAIT"->"Ожидание гостя"
    "TECH_WAIT"->"Техническая задержка"
    "OTHER_WAIT"->"Другая задержка"
    "EXTRA_HOUR"->"Дополнительный час"
    else->"Событие"
}
private fun workState(s:String)=when(s){
    "READY"->"ожидает"
    "RUNNING"->"идёт"
    "PAUSED"->"пауза"
    else->"завершено"
}
