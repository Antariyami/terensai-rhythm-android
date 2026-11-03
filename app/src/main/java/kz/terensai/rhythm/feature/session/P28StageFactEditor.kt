package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kz.terensai.rhythm.data.room.StageFactCorrectionRow
import kz.terensai.rhythm.domain.StageProgress
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

/** No implicit START/REOPEN. User edits effective start/end with an audit reason. */
@Composable
internal fun P28StageFactEditor(
    stage:StageProgress,
    history:List<StageFactCorrectionRow>,
    pending:Boolean,
    onDismiss:()->Unit,
    onSave:(Long,Long?,String)->Unit
) {
    val zone=remember{ZoneId.of("Asia/Almaty")}
    val fmt=remember{DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
        .withResolverStyle(ResolverStyle.STRICT)}
    fun show(ms:Long?):String=ms?.let{
        LocalDateTime.ofInstant(Instant.ofEpochMilli(it),zone).format(fmt)
    }?:""
    var startText by remember(stage.plan.id){mutableStateOf(show(stage.actualStartedAtMs))}
    var endText by remember(stage.plan.id){mutableStateOf(show(stage.actualFinishedAtMs))}
    var reason by remember(stage.plan.id){mutableStateOf("Уточнение времени после работы")}
    var error by remember(stage.plan.id){mutableStateOf<String?>(null)}
    val noStarted=stage.status==StageStatus.NOT_STARTED
    val isRunning=stage.status==StageStatus.RUNNING || stage.status==StageStatus.PAUSED
    val canEdit=stage.status!=StageStatus.SKIPPED
    fun parse(raw:String):Long?=if(raw.isBlank())null
        else LocalDateTime.parse(raw.trim(),fmt).atZone(zone).toInstant().toEpochMilli()
    val estimate=runCatching{
        val a=parse(startText)
        val b=parse(endText)
        if(a!=null&&b!=null&&b>a)(b-a)/60_000L else null
    }.getOrNull()
    AlertDialog(
        onDismissRequest=onDismiss,
        containerColor=C.panel,
        title={Text("Факт этапа · ${stage.plan.title}",color=C.white)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text("План: ${stage.plan.plannedDurationMs/60_000} мин · факт: ${formatElapsed(stage.elapsedMs)}",
                    color=C.cream)
                Text("Начало/конец — местное время Астаны. Формат: 03.11.2026 14:30",
                    color=C.secondary)
                OutlinedTextField(value=startText,onValueChange={startText=it.take(17)},
                    enabled=canEdit&&!pending,
                    modifier=Modifier.fillMaxWidth(),
                    label={Text("Фактическое начало: дата и время")})
                OutlinedTextField(value=endText,onValueChange={endText=it.take(17)},
                    enabled=canEdit&&!pending,
                    modifier=Modifier.fillMaxWidth(),
                    label={Text("Фактический конец (можно пусто для текущего)")})
                if(noStarted)Text("Для ещё не начатого этапа укажи оба значения: так можно внести выполненную работу задним числом.",
                    color=C.secondary)
                if(isRunning)Text("Оставь конец пустым, чтобы таймер продолжал идти.",
                    color=C.secondary)
                if(estimate!=null)Text("Интервал до учёта внутренних пауз: $estimate мин",
                    color=C.amber)
                OutlinedTextField(value=reason,onValueChange={reason=it.take(500)},
                    enabled=canEdit&&!pending,
                    modifier=Modifier.fillMaxWidth(),label={Text("Причина исправления")})
                if(stage.status==StageStatus.SKIPPED)Text(
                    "Пропущенные этапы восстанавливаются отдельным действием.",
                    color=C.overrun)
                if(error!=null)Text(error!!,color=C.overrun)
                if(history.isNotEmpty()){
                    Text("История исправлений: ${history.size}",color=C.bronze)
                    history.takeLast(3).forEach{row->
                        Text("${show(row.priorStartedAtMs)} — ${show(row.priorFinishedAtMs)} → "+
                            "${show(row.effectiveStartedAtMs)} — ${show(row.effectiveFinishedAtMs)} "+
                            "(${row.reason})",color=C.secondary)
                    }
                }
                Text("Исходные события и журнал работы сохраняются. Уже учтённое парение потребует отдельной согласованной корректировки личной записи.",
                    color=C.muted)
            }
        },
        confirmButton={TextButton(enabled=canEdit&&!pending,onClick={
            val parsed=runCatching{parse(startText) to parse(endText)}.getOrNull()
            val start=parsed?.first
            val end=parsed?.second
            error=when{
                parsed==null->"Неверный формат даты и времени"
                start==null->"Заполни время начала"
                reason.trim().length<3->"Напиши причину исправления"
                end!=null&&end<=start->"Окончание должно быть позже начала"
                noStarted&&end==null->"Для внесения пройденного этапа укажи время окончания"
                stage.status==StageStatus.COMPLETED&&end==null->
                    "У завершённого этапа нельзя оставить пустое окончание"
                else->null
            }
            if(error==null&&start!=null)onSave(start,end,reason.trim())
        }){Text("Сохранить факт",color=C.amber)}},
        dismissButton={TextButton(onClick=onDismiss){Text("Отмена",color=C.white)}}
    )
}
