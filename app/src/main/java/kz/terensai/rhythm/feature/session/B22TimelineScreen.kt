package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import kz.terensai.rhythm.data.room.StageFactCorrectionRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.R
import kz.terensai.rhythm.feature.entry.ParitmAtmosphere
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.StageReturnRules
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun B22TimelineScreen(
    saved:SavedSession,
    notice:String?,
    pending:Boolean,
    onBack:()->Unit,
    onCommand:(String,EventKind)->Unit,
    onReopen:(String)->Unit,
    onFinish:()->Unit,
    onFinishProtected:(String,String)->Unit={_,_->onFinish()},
    onCorrectStage:(String,Long,Long?,String)->Unit={_,_,_,_->},
    corrections:List<StageFactCorrectionRow> = emptyList(),
    activeWorkCount:Int=0,
    extraDurationMs:Long=0L,
    liveActions:@Composable ()->Unit={},
    backLabel:String="‹ К программам"
) {
    var focusVisible by rememberSaveable(saved.id){mutableStateOf(false)}
    if(focusVisible) {
        B31FocusScreen(
            saved=saved,notice=notice,pending=pending,
            onClose={focusVisible=false},onCommand=onCommand
        )
        return
    }
    val all=saved.progress.stages
    val current=all.firstOrNull{
        it.status==StageStatus.RUNNING || it.status==StageStatus.PAUSED
    } ?: all.firstOrNull { it.status==StageStatus.NOT_STARTED }
    val previous=StageReturnRules.target(saved.progress)
    val finished=all.all { it.status==StageStatus.COMPLETED ||
        it.status==StageStatus.SKIPPED }
    var confirmFinish by remember { mutableStateOf(false) }
    var finishMenuOpen by remember { mutableStateOf(false) }
    var finishMode by remember {mutableStateOf("SAVED")}
    var finishReason by remember {mutableStateOf("")}
    var stageToEdit by remember(saved.id){mutableStateOf<String?>(null)}
    var confirmSkip by remember { mutableStateOf(false) }
    var confirmReopen by remember { mutableStateOf(false) }
    val plannedDuration=saved.progress.plannedEndMs-saved.startedAtMs+extraDurationMs
    val remaining=(plannedDuration-saved.progress.elapsedWallClockMs).coerceAtLeast(0L)
    val clock=SimpleDateFormat("HH:mm",Locale("ru","RU"))
    val now = Date(saved.startedAtMs + saved.progress.elapsedWallClockMs)
    val plannedEnd = Date(saved.progress.plannedEndMs+extraDurationMs)
    val shape=RoundedCornerShape(18.dp)

    Box(Modifier.fillMaxSize().background(C.canvas)) {
    ParitmAtmosphere()
    Column(Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState()).padding(horizontal=17.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack){Text(backLabel,color=C.amber)}
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick={finishMenuOpen=true},enabled=!pending){
                    Text("⋮ Действия",color=C.amber)
                }
                DropdownMenu(expanded=finishMenuOpen,
                    onDismissRequest={finishMenuOpen=false}){
                    DropdownMenuItem(text={Text("Завершить всю программу…")},
                        onClick={finishMenuOpen=false;confirmFinish=true})
                }
            }
            Image(painterResource(R.drawable.paritm_approved_mark_hq),
                contentDescription="ПАРИТМ",modifier=Modifier.size(44.dp))
        }
        Text("ПАРИТМ  ·  ХОД ПРОГРАММЫ",color=C.bronze,
            fontSize=12.sp,letterSpacing=1.5.sp)
        Text(saved.programmeTitle,color=C.white,fontSize=26.sp,
            fontWeight=FontWeight.SemiBold)
        val meta=listOfNotNull(saved.metadata.suite?.takeIf{it.isNotBlank()},
            saved.metadata.guestCount?.let{"${it} гостей"})
        if(meta.isNotEmpty()) Text(meta.joinToString(" · "),color=C.secondary,fontSize=13.sp)
        Column(Modifier.fillMaxWidth().background(C.raised,shape).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(5.dp),
            horizontalAlignment=Alignment.CenterHorizontally) {
            Text("РЕАЛЬНОЕ ВРЕМЯ",color=C.muted,fontSize=12.sp)
            Text(clock.format(now),color=C.white,fontSize=32.sp,
                fontWeight=FontWeight.SemiBold)
            Text("Плановое окончание ${clock.format(plannedEnd)}",color=C.bronze,fontSize=13.sp)
        }
        Column(Modifier.fillMaxWidth().background(C.panel,shape).padding(15.dp),
            verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Text("Общий ход программы",color=C.secondary,fontSize=13.sp)
            Text(formatElapsed(saved.progress.elapsedWallClockMs)+" / "+
                formatElapsed(plannedDuration),
                color=C.white,fontSize=24.sp,fontWeight=FontWeight.Bold)
            Text(if(remaining>0)
                    "До планового завершения ${formatElapsed(remaining)}"
                 else "Плановое время завершилось",
                color=if(remaining>0) C.fact else C.overrun,fontSize=13.sp)
        }
        if(current!=null) {
            StageProgressCard(current)
            OutlinedButton(onClick={focusVisible=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=58.dp)) {
                Text("⛶ Полноэкранный таймер · ФОКУС",
                    color=C.amber,fontSize=16.sp,fontWeight=FontWeight.SemiBold)
            }
            val id=current.plan.id
            val status=current.status
            if(status==StageStatus.NOT_STARTED) {
                ParitmPrimaryButton("Начать этап",onClick={onCommand(id,EventKind.START)},
                    enabled=!pending,modifier=Modifier.fillMaxWidth())
            }
            if(status==StageStatus.RUNNING) {
                OutlinedButton(onClick={onCommand(id,EventKind.PAUSE)},
                    enabled=!pending,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                    Text("Ⅱ  Пауза этапа",color=C.white)
                }
            }
            if(status==StageStatus.PAUSED) {
                ParitmPrimaryButton("Продолжить этап",onClick={onCommand(id,EventKind.RESUME)},
                    enabled=!pending,modifier=Modifier.fillMaxWidth())
            }
            if(status==StageStatus.RUNNING || status==StageStatus.PAUSED) {
                ParitmPrimaryButton("Завершить этап →",
                    onClick={onCommand(id,EventKind.COMPLETE)},
                    enabled=!pending,modifier=Modifier.fillMaxWidth())
            }
            OutlinedButton(onClick={confirmSkip=true},enabled=!pending,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Пропустить этап",color=C.bronze)
            }
        }
        if(previous!=null) OutlinedButton(onClick={confirmReopen=true},
            enabled=!pending,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Text("← Предыдущий этап: ${previous.plan.title}",color=C.amber)
        }
        TimelineCard(progress=saved.progress,stage=current,
            modifier=Modifier.fillMaxWidth(),
            correctedIds=saved.correctedStageIds,
            onStageClick={stageToEdit=it})
        Text("Нажми на любой этап шкалы, чтобы отредактировать фактические часы начала и конца.",
            color=C.secondary,fontSize=12.sp)
        liveActions()
        if(notice!=null) Text(notice,color=C.bronze,fontSize=13.sp)
        if(finished) ParitmPrimaryButton("Завершить программу",
            onClick={finishMode="SAVED";confirmFinish=true},
            enabled=!pending,modifier=Modifier.fillMaxWidth())
        Text("Назад — без остановки хронометража. Этап на паузе, но общее время идёт.",
            color=C.muted,fontSize=12.sp)
        Spacer(Modifier.height(24.dp))
    }

    if(confirmReopen && previous!=null) AlertDialog(
        onDismissRequest={confirmReopen=false},containerColor=C.panel,
        title={Text("Вернуться к предыдущему этапу?",color=C.white)},
        text={Text("«${previous.plan.title}» продолжится с накопленного времени. " +
            "Идущий этап той же дорожки будет поставлен на паузу. " +
            "Журнал событий сохранится.",color=C.secondary)},
        confirmButton={TextButton(enabled=!pending,onClick={
            confirmReopen=false
            onReopen(previous.plan.id)
        }){Text("Вернуться",color=C.amber)}},
        dismissButton={TextButton(onClick={confirmReopen=false}){Text("Отмена",color=C.white)}}
    )
    if(confirmSkip && current!=null) AlertDialog(
        onDismissRequest={confirmSkip=false},containerColor=C.panel,
        title={Text("Пропустить этап?",color=C.white)},
        text={Text("Пропуск сохранится в журнале событий.",color=C.secondary)},
        confirmButton={TextButton(onClick={
            confirmSkip=false
            onCommand(current.plan.id,EventKind.SKIP)
        }){Text("Пропустить",color=C.overrun)}},
        dismissButton={TextButton(onClick={confirmSkip=false}){Text("Отмена",color=C.white)}}
    )
    val edit=all.firstOrNull{it.plan.id==stageToEdit}
    if(edit!=null)P28StageFactEditor(
        stage=edit,history=corrections.filter{it.stageId==edit.plan.id},
        pending=pending,onDismiss={stageToEdit=null},
        onSave={started,ended,reason->
            stageToEdit=null
            onCorrectStage(edit.plan.id,started,ended,reason)
        }
    )
    if(confirmFinish) AlertDialog(
        onDismissRequest={confirmFinish=false},
        containerColor=C.panel,
        title={Text("Завершить всю программу?",color=C.white)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("Выполнено ${all.count{it.status==StageStatus.COMPLETED}} из ${all.size} этапов. "+
                    "Незавершённые будут отмечены как пропущенные; реальные работы останутся в журнале.",
                    color=C.secondary)
                if(activeWorkCount>0)
                    Text("Активных/приостановленных дополнительных процедур: $activeWorkCount. "+
                        "Сначала заверши их, иначе закрытие будет заблокировано.",
                        color=C.overrun)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedButton(onClick={finishMode="SAVED"}){
                        Text("Сохранить",color=if(finishMode=="SAVED")C.fact else C.secondary)
                    }
                    OutlinedButton(onClick={finishMode="INTERRUPTED"}){
                        Text("Прервана",color=if(finishMode=="INTERRUPTED")C.amber else C.secondary)
                    }
                }
                if(finishMode=="INTERRUPTED"){
                    OutlinedTextField(value=finishReason,
                        onValueChange={finishReason=it.take(500)},
                        label={Text("Причина изменения программы")},
                        modifier=Modifier.fillMaxWidth())
                }
                Text("Это закрытие не удаляет выполненные парения и не аннулирует личные записи.",
                    color=C.muted)
            }
        },
        confirmButton={TextButton(enabled=!pending&&activeWorkCount==0&&
            (finishMode!="INTERRUPTED"||finishReason.trim().length>=3),
            onClick={
                val choice=finishMode
                val reason=finishReason
                confirmFinish=false
                onFinishProtected(choice,reason)
            }){Text("Подтвердить завершение",color=C.amber)}},
        dismissButton={TextButton(onClick={confirmFinish=false}){Text("Продолжить",color=C.white)}}
    )
    }
}
