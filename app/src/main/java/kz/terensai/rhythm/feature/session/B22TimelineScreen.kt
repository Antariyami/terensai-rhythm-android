package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
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
    onFinish:()->Unit,
    backLabel:String="‹ К программам"
) {
    val all=saved.progress.stages
    val current=all.firstOrNull{
        it.status==StageStatus.RUNNING || it.status==StageStatus.PAUSED
    } ?: all.firstOrNull { it.status==StageStatus.NOT_STARTED }
    val finished=all.all { it.status==StageStatus.COMPLETED ||
        it.status==StageStatus.SKIPPED }
    var confirmFinish by remember { mutableStateOf(false) }
    var confirmSkip by remember { mutableStateOf(false) }
    val plannedDuration=saved.progress.plannedEndMs-saved.startedAtMs
    val clock=SimpleDateFormat("HH:mm",Locale("ru","RU"))
    val now = Date(saved.startedAtMs + saved.progress.elapsedWallClockMs)
    val plannedEnd = Date(saved.progress.plannedEndMs)
    val shape=RoundedCornerShape(18.dp)

    Box(Modifier.fillMaxSize().background(C.canvas)) {
    ParitmAtmosphere()
    Column(Modifier.fillMaxSize()
        .verticalScroll(rememberScrollState()).padding(horizontal=17.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=onBack){Text(backLabel,color=C.amber)}
            Spacer(Modifier.weight(1f))
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
            Text(if(saved.progress.remainingPlannedMs>0)
                    "До планового завершения ${formatElapsed(saved.progress.remainingPlannedMs)}"
                 else "Плановое время завершилось",
                color=if(saved.progress.remainingPlannedMs>0) C.fact else C.overrun,fontSize=13.sp)
        }
        if(current!=null) {
            StageProgressCard(current)
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
        TimelineCard(progress=saved.progress,stage=current,modifier=Modifier.fillMaxWidth())
        if(notice!=null) Text(notice,color=C.bronze,fontSize=13.sp)
        if(finished) ParitmPrimaryButton("Завершить программу",
            onClick={confirmFinish=true},enabled=!pending,modifier=Modifier.fillMaxWidth())
        Text("Назад — без остановки хронометража. Этап на паузе, но общее время идёт.",
            color=C.muted,fontSize=12.sp)
        Spacer(Modifier.height(24.dp))
    }

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
    if(confirmFinish) AlertDialog(
        onDismissRequest={confirmFinish=false},containerColor=C.panel,
        title={Text("Завершить сессию?",color=C.white)},
        text={Text("Все события останутся в базе данных.",color=C.secondary)},
        confirmButton={TextButton(onClick={
            confirmFinish=false
            onFinish()
        }){Text("Завершить",color=C.amber)}},
        dismissButton={TextButton(onClick={confirmFinish=false}){Text("Отмена",color=C.white)}}
    )
    }
}
