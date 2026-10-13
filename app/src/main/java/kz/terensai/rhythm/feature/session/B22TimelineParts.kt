package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.domain.SessionProgress
import kz.terensai.rhythm.domain.StageProgress
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kotlin.math.min

internal fun formatElapsed(ms:Long):String {
    val seconds = ms.coerceAtLeast(0L) / 1000
    return "%02d:%02d:%02d".format(
        java.util.Locale.ROOT, seconds/3600, (seconds/60)%60, seconds%60
    )
}
internal fun formatDelta(ms:Long):String {
    val seconds = kotlin.math.abs(ms)/1000
    val sign = if (ms>0) "+" else if (ms<0) "−" else "±"
    return sign + "%02d:%02d".format(java.util.Locale.ROOT, seconds/60, seconds%60)
}

@Composable
internal fun TimelineCard(
    progress:SessionProgress,
    stage:StageProgress?,
    modifier:Modifier=Modifier
) {
    val shape = RoundedCornerShape(18.dp)
    Column(modifier.background(C.panel,shape).border(1.dp,C.border,shape).padding(15.dp),
        verticalArrangement=Arrangement.spacedBy(10.dp)) {
        Text("Единая шкала программы",color=C.cream,
            fontSize=18.sp,fontWeight=FontWeight.SemiBold)
        Text("План · этапы",color=C.secondary,fontSize=12.sp)
        Row(Modifier.fillMaxWidth().height(13.dp),
            horizontalArrangement=Arrangement.spacedBy(2.dp)) {
            val total = progress.stages.sumOf { it.plan.plannedDurationMs }.coerceAtLeast(1L)
            progress.stages.forEach { s->
                val shade=when(s.status) {
                    StageStatus.COMPLETED -> C.success
                    StageStatus.RUNNING -> C.amber
                    StageStatus.PAUSED -> C.bronze
                    StageStatus.SKIPPED -> C.muted
                    StageStatus.NOT_STARTED -> C.border
                }
                Box(Modifier.weight(s.plan.plannedDurationMs.toFloat()/total.toFloat())
                    .fillMaxHeight().background(shade,RoundedCornerShape(3.dp)))
            }
        }
        Text("Факт · положение в программе",color=C.secondary,fontSize=12.sp)
        Box(Modifier.fillMaxWidth().height(9.dp).background(C.border,RoundedCornerShape(8.dp))) {
            val planned = (progress.plannedEndMs -
                (progress.plannedEndMs - progress.remainingPlannedMs - progress.elapsedWallClockMs))
            // The total planned duration is reconstructed from the stage plan,
            // not the changing 'remaining' value.
            val duration = progress.stages.maxOfOrNull {
                it.plan.startOffsetMs+it.plan.plannedDurationMs
            }?.coerceAtLeast(1L) ?: 1L
            val fraction=(progress.elapsedWallClockMs.toFloat()/duration.toFloat()).coerceIn(0f,1f)
            if(fraction>0f) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                .background(C.fact,RoundedCornerShape(8.dp)))
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("0:00",color=C.muted,fontSize=11.sp)
            Text("3:00 · конец плана",color=C.amber,fontSize=11.sp)
        }
        val totalOverdue = progress.elapsedWallClockMs -
            (progress.plannedEndMs - (progress.plannedEndMs - progress.elapsedWallClockMs
                - progress.remainingPlannedMs))
        // The meaningful global indicator is elapsed beyond full programme plan;
        // stage start delay is shown separately below.
        val planMs=progress.stages.maxOfOrNull {
            it.plan.startOffsetMs+it.plan.plannedDurationMs
        } ?: 0L
        if(progress.elapsedWallClockMs>planMs)
            Text("Общее время сверх плана ${formatDelta(progress.elapsedWallClockMs-planMs)}",
                color=C.overrun,fontWeight=FontWeight.Bold,fontSize=13.sp)
        if(stage?.startDeltaMs!=null && stage.startDeltaMs!=0L)
            Text("Сдвиг начала этапа: ${formatDelta(stage.startDeltaMs)}",
                color=C.bronze,fontSize=13.sp)
        progress.stages.forEachIndexed { index,s->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(when(s.status) {
                    StageStatus.COMPLETED -> "✓"
                    StageStatus.RUNNING -> "●"
                    StageStatus.PAUSED -> "Ⅱ"
                    StageStatus.SKIPPED -> "–"
                    StageStatus.NOT_STARTED -> "○"
                },color=if(s.status==StageStatus.RUNNING) C.fact else C.bronze,
                    fontSize=18.sp)
                Spacer(Modifier.width(8.dp))
                Text("${index+1}. ${s.plan.title}",Modifier.weight(1f),
                    color=if(s.status==StageStatus.RUNNING || s.status==StageStatus.PAUSED)
                        C.white else C.secondary,fontSize=12.sp)
                Text("${s.plan.plannedDurationMs/60000} мин",
                    color=C.muted,fontSize=11.sp)
            }
        }
    }
}

@Composable
internal fun StageProgressCard(stage:StageProgress,modifier:Modifier=Modifier) {
    val p=stage.plan.plannedDurationMs
    val e=stage.elapsedMs
    val remaining=(p-e).coerceAtLeast(0L)
    val overrun=(e-p).coerceAtLeast(0L)
    val shape=RoundedCornerShape(18.dp)
    Column(modifier.background(C.raised,shape).border(1.dp,C.bronze,shape).padding(15.dp),
        verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Text("ТЕКУЩИЙ ЭТАП",color=C.bronze,fontSize=11.sp,letterSpacing=1.5.sp)
        Text(stage.plan.title,color=C.white,fontSize=21.sp,fontWeight=FontWeight.SemiBold)
        Text(formatElapsed(e),color=C.fact,fontSize=38.sp,fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.SpaceBetween,modifier=Modifier.fillMaxWidth()) {
            Text("План ${formatElapsed(p)}",color=C.amber,fontSize=13.sp)
            Text(if(overrun>0) "Превышение +${formatElapsed(overrun)}"
                else "Осталось ${formatElapsed(remaining)}",
                color=if(overrun>0) C.overrun else C.fact,fontSize=13.sp)
        }
        Box(Modifier.fillMaxWidth().height(10.dp).background(C.border,RoundedCornerShape(7.dp))) {
            val fraction= if (p<=0) 1f else (e.toFloat()/p.toFloat()).coerceIn(0f,1f)
            if(fraction>0) Box(Modifier.fillMaxWidth(fraction).fillMaxHeight()
                .background(if(overrun>0) C.overrun else C.fact,RoundedCornerShape(7.dp)))
        }
        if(stage.status==StageStatus.PAUSED)
            Text("Пауза: фактическое время этапа остановлено",color=C.bronze,fontSize=12.sp)
        if(overrun>0)
            Text("Время превышено, этап продолжается",color=C.overrun,fontSize=12.sp)
    }
}
