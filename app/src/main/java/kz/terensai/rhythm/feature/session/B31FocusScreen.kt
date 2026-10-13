package kz.terensai.rhythm.feature.session

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.FocusStageProjection
import kz.terensai.rhythm.domain.ScheduleOffsetKind
import kz.terensai.rhythm.domain.ScheduleOffsets
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton

private tailrec fun Context.hostActivity():Activity? = when(this) {
    is Activity -> this
    is ContextWrapper -> baseContext.hostActivity()
    else -> null
}

/**
 * B3.1 portrait: hide system chrome and keep screen awake ONLY while focus is
 * visible. Restores the caller's exact flags when closing the focus screen.
 * This does not request an orientation change; landscape follows in B3.2.
 */
@Composable
private fun FocusWindowPolicy() {
    val context=LocalContext.current
    val activity=remember(context){context.hostActivity()}
    DisposableEffect(activity) {
        val window=activity?.window
        val decor=window?.decorView
        val oldUiFlags=decor?.systemUiVisibility
        val wasKeepAwake=window?.attributes?.flags?.and(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )?.let { it!=0 } ?: false
        if(decor!=null){
            @Suppress("DEPRECATION")
            decor.systemUiVisibility = (oldUiFlags?:0) or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        }
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            if(decor!=null && oldUiFlags!=null) {
                @Suppress("DEPRECATION")
                decor.systemUiVisibility=oldUiFlags
            }
            if(!wasKeepAwake)window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

@Composable
fun B31FocusScreen(
    saved:SavedSession,
    notice:String?,
    pending:Boolean,
    onClose:()->Unit,
    onCommand:(String,EventKind)->Unit
) {
    BackHandler(onBack=onClose)
    FocusWindowPolicy()
    val stages=saved.progress.stages
    val current=stages.firstOrNull{it.status==StageStatus.RUNNING||it.status==StageStatus.PAUSED}
        ?:stages.firstOrNull{it.status==StageStatus.NOT_STARTED}
    val position=if(current==null)stages.size else stages.indexOf(current)+1
    var confirmCompleteId by remember {mutableStateOf<String?>(null)}
    // Cancel a stale confirmation if this stage changed on another refresh.
    LaunchedEffect(current?.plan?.id){
        if(confirmCompleteId!=current?.plan?.id)confirmCompleteId=null
    }

    if(confirmCompleteId!=null && confirmCompleteId==current?.plan?.id){
        AlertDialog(
            onDismissRequest={confirmCompleteId=null},
            containerColor=C.panel,
            title={Text("Завершить этап?",color=C.white)},
            text={Text("Фактическая длительность будет сохранена. Следующий этап не запустится автоматически.",
                color=C.secondary)},
            confirmButton={
                TextButton(enabled=!pending,onClick={
                    val id=confirmCompleteId
                    confirmCompleteId=null
                    if(id!=null)onCommand(id,EventKind.COMPLETE)
                }){Text("Завершить этап",color=C.amber)}
            },
            dismissButton={
                TextButton(onClick={confirmCompleteId=null}){Text("Отмена",color=C.white)}
            }
        )
    }

    Column(
        Modifier.fillMaxSize().background(C.canvas)
            .verticalScroll(rememberScrollState())
            .padding(horizontal=19.dp,vertical=12.dp),
        verticalArrangement=Arrangement.spacedBy(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
            TextButton(onClick=onClose){Text("‹ Ход программы",color=C.amber)}
            Spacer(Modifier.weight(1f))
            Text("ФОКУС · B3.1",color=C.bronze,fontSize=12.sp,letterSpacing=1.2.sp)
        }
        Text(saved.programmeTitle,color=C.white,fontWeight=FontWeight.SemiBold,fontSize=23.sp)
        val context=listOfNotNull(
            saved.metadata.suite?.takeIf{it.isNotBlank()},
            saved.metadata.guestCount?.let{"${it} гостей"}
        )
        if(context.isNotEmpty())Text(context.joinToString(" · "),color=C.secondary,fontSize=13.sp)
        Text("ЭТАП ${position} ИЗ ${stages.size}",color=C.bronze,fontSize=12.sp,
            fontWeight=FontWeight.SemiBold)

        if(current==null) {
            Column(Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(22.dp))
                .padding(24.dp),horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(18.dp)){
                Text("Все этапы выполнены",color=C.fact,fontSize=26.sp,fontWeight=FontWeight.Bold)
                Text("Вернись к ходу программы, чтобы завершить сессию.",
                    color=C.secondary,textAlign=TextAlign.Center)
                ParitmPrimaryButton("Вернуться к программе",onClick=onClose,
                    modifier=Modifier.fillMaxWidth())
            }
        }else{
            val m=FocusStageProjection.of(current,saved.progress)
            val statusLabel=when(m.status){
                StageStatus.NOT_STARTED->"ОЖИДАЕТ НАЧАЛА"
                StageStatus.RUNNING->"ИДЁТ"
                StageStatus.PAUSED->"НА ПАУЗЕ"
                else->"ЗАВЕРШЁН"
            }
            val stateColor=when(m.status){
                StageStatus.RUNNING->C.fact
                StageStatus.PAUSED->C.amber
                else->C.bronze
            }
            val clockColor=if(m.overtime)C.overrun else if(m.status==StageStatus.PAUSED)C.amber else C.fact
            val timeDisplay=if(m.overtime)"+${formatElapsed(m.overtimeMs)}"
                else formatElapsed(m.remainingMs)

            Column(Modifier.fillMaxWidth().background(C.raised,RoundedCornerShape(24.dp))
                .border(1.dp,C.border,RoundedCornerShape(24.dp)).padding(18.dp),
                horizontalAlignment=Alignment.CenterHorizontally,
                verticalArrangement=Arrangement.spacedBy(11.dp)){
                Text(current.plan.title,color=C.white,fontSize=25.sp,
                    fontWeight=FontWeight.SemiBold,textAlign=TextAlign.Center)
                Text(statusLabel,color=stateColor,fontSize=13.sp,
                    fontWeight=FontWeight.Bold,letterSpacing=1.4.sp)
                Text(if(m.overtime)"ПРЕВЫШЕНИЕ ПЛАНА" else "ДО КОНЦА ЭТАПА",
                    color=C.secondary,fontSize=12.sp)
                Text(timeDisplay,color=clockColor,fontSize=54.sp,
                    fontWeight=FontWeight.Bold,textAlign=TextAlign.Center,
                    maxLines=1)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text("Прошло ${formatElapsed(m.elapsedMs)}",color=C.white,fontSize=12.sp)
                    Text("План ${formatElapsed(m.plannedMs)}",color=C.cream,fontSize=12.sp)
                }
                Box(Modifier.fillMaxWidth().height(12.dp)
                    .background(C.border,RoundedCornerShape(8.dp))) {
                    if(m.stageProgress>0f)
                        Box(Modifier.fillMaxWidth(m.stageProgress).fillMaxHeight()
                            .background(clockColor,RoundedCornerShape(8.dp)))
                }
                if(m.status==StageStatus.PAUSED)
                    Text("Пауза: время этапа не увеличивается",color=C.amber,fontSize=12.sp)
            }
            Column(Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(18.dp))
                .padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Text("ОБЩЕЕ ВРЕМЯ ПРОГРАММЫ",color=C.bronze,fontSize=11.sp)
                Text(formatElapsed(m.overallElapsedMs),color=C.white,
                    fontWeight=FontWeight.Bold,fontSize=25.sp)
                Text(if(m.overallRemainingMs>0)
                    "До планового завершения ${formatElapsed(m.overallRemainingMs)}"
                    else "Плановое время программы истекло",
                    color=if(m.overallRemainingMs>0)C.fact else C.overrun,fontSize=13.sp)
                m.startDeltaMs?.let{delta->
                    val offset=ScheduleOffsets.classify(delta)
                    when(offset.kind){
                        ScheduleOffsetKind.EARLY->
                            Text("Опережение графика ${formatElapsed(offset.absoluteMillis)}",
                                color=C.fact,fontSize=12.sp)
                        ScheduleOffsetKind.LATE->
                            Text("Отставание от графика ${formatElapsed(offset.absoluteMillis)}",
                                color=C.overrun,fontSize=12.sp)
                        ScheduleOffsetKind.ON_TIME->Text("По графику",color=C.success,fontSize=12.sp)
                    }
                }
            }
            if(notice!=null)Text(notice,color=C.bronze,fontSize=12.sp)
            when(m.status){
                StageStatus.NOT_STARTED->
                    ParitmPrimaryButton("▶ Начать этап",onClick={
                        onCommand(current.plan.id,EventKind.START)
                    },enabled=!pending,modifier=Modifier.fillMaxWidth())
                StageStatus.RUNNING->{
                    OutlinedButton(onClick={onCommand(current.plan.id,EventKind.PAUSE)},
                        enabled=!pending,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)){
                        Text("Ⅱ Пауза",color=C.white,fontSize=17.sp)
                    }
                    ParitmPrimaryButton("✓ Завершить этап",onClick={
                        confirmCompleteId=current.plan.id
                    },enabled=!pending,modifier=Modifier.fillMaxWidth())
                }
                StageStatus.PAUSED->{
                    ParitmPrimaryButton("▶ Продолжить этап",onClick={
                        onCommand(current.plan.id,EventKind.RESUME)
                    },enabled=!pending,modifier=Modifier.fillMaxWidth())
                    OutlinedButton(onClick={confirmCompleteId=current.plan.id},
                        enabled=!pending,modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)){
                        Text("✓ Завершить этап",color=C.cream,fontSize=17.sp)
                    }
                }
                else->Unit
            }
            Text("Личный таймер процедуры и таймер этапа независимы. Выход из фокуса не останавливает программу.",
                color=C.muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(20.dp))
    }
}
