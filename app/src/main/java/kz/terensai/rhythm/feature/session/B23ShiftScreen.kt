package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.R
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.feature.entry.ParitmAtmosphere
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton

@Composable
fun B23ShiftScreen(
    sessions: List<SavedSession>,
    pending: Boolean,
    notice: String?,
    onBack: () -> Unit,
    onNew: () -> Unit,
    onOpen: (String) -> Unit,
    onMasters: () -> Unit = {},
    leaders: Map<String,String> = emptyMap()
) {
    Box(Modifier.fillMaxSize().background(C.canvas)) {
        ParitmAtmosphere()
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal=18.dp,vertical=16.dp),
            verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick=onBack) { Text("‹ К программам",color=C.amber) }
                Spacer(Modifier.weight(1f))
                Image(painter=painterResource(R.drawable.paritm_approved_mark_hq),
                    contentDescription="ПАРИТМ",modifier=Modifier.size(48.dp))
            }
            OutlinedButton(onClick=onMasters,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)){
                Text("Мастера · личный профиль и справочник",color=C.amber)
            }
            Text("ПАРИТМ  ·  МОЯ СМЕНА",color=C.bronze,
                fontWeight=FontWeight.SemiBold,fontSize=12.sp,letterSpacing=2.sp)
            Text("Активные программы",color=C.white,
                fontWeight=FontWeight.Bold,fontSize=27.sp)
            Text("${sessions.size} из 5 возможных",color=C.amber,fontSize=17.sp)
            Text("Каждая программа ведёт независимый отсчёт времени. Переключение не ставит другие программы на паузу.",
                color=C.secondary,fontSize=13.sp,lineHeight=19.sp)
            if (notice!=null) Text(notice,color=if(notice.startsWith("Ошибка")) C.overrun else C.bronze)
            if (sessions.isEmpty()) {
                Column(Modifier.fillMaxWidth()
                    .background(C.panel,RoundedCornerShape(20.dp))
                    .padding(19.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Пока нет активных программ",color=C.cream,fontSize=18.sp,
                        fontWeight=FontWeight.SemiBold)
                    Text("Запусти первую программу «Танысу» и вернись сюда. Она продолжит идти по собственным временным меткам.",
                        color=C.secondary)
                }
            }
            sessions.forEachIndexed { index, session ->
                val shape=RoundedCornerShape(18.dp)
                val current=session.progress.stages.firstOrNull {
                    it.status==StageStatus.RUNNING || it.status==StageStatus.PAUSED
                } ?: session.progress.stages.firstOrNull { it.status==StageStatus.NOT_STARTED }
                Column(Modifier.fillMaxWidth().background(C.panel,shape)
                    .border(1.dp,C.border,shape).padding(16.dp),
                    verticalArrangement=Arrangement.spacedBy(9.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text("ПРОГРАММА ${index+1}",color=C.bronze,fontSize=11.sp)
                        Spacer(Modifier.weight(1f))
                        Text(session.metadata.suite?.takeIf{it.isNotBlank()} ?: "Без сьюта",
                            color=C.cream,fontSize=13.sp)
                    }
                    Text("PM1: " + (leaders[session.id] ?: "не назначен"),color=C.bronze,fontSize=13.sp)
                    Text(session.programmeTitle,color=C.white,fontSize=19.sp,
                        fontWeight=FontWeight.SemiBold)
                    Text("С начала: ${formatElapsed(session.progress.elapsedWallClockMs)}",
                        color=C.fact,fontSize=19.sp)
                    Text(
                        current?.let { "${it.plan.title} · " + when(it.status) {
                            StageStatus.RUNNING -> "идёт"
                            StageStatus.PAUSED -> "пауза"
                            StageStatus.NOT_STARTED -> "ожидает запуска"
                            StageStatus.COMPLETED -> "завершено"
                            StageStatus.SKIPPED -> "пропущено"
                        }} ?: "Все этапы выполнены",
                        color=C.secondary,fontSize=13.sp
                    )
                    OutlinedButton(
                        onClick={ onOpen(session.id) },
                        enabled=!pending,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                    ) { Text("Открыть программу →",color=C.amber) }
                }
            }
            ParitmPrimaryButton(
                label=if(sessions.size >= 5) "Предел: 5 активных программ" else "+ Новая программа",
                onClick=onNew,
                enabled=!pending && sessions.size < 5,
                modifier=Modifier.fillMaxWidth()
            )
            Text("Пока программы находятся на одном телефоне. Общая синхронизация между мастерами появится в будущем.",
                color=C.muted,fontSize=12.sp,lineHeight=18.sp)
            Spacer(Modifier.height(25.dp))
        }
    }
}
