package kz.terensai.rhythm.feature.session

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.data.JournalLine
import kz.terensai.rhythm.data.WorkJournalLabels
import kz.terensai.rhythm.data.WorkJournalReport
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import java.time.LocalDate

/**
 * S13 · Shift work journal. Dates are local to the phone, grouped by the
 * procedure's first START (or creation if it has not started yet).
 */
@Composable
fun B243JournalScreen(
    masterName:String,
    entries:List<JournalLine>,
    pending:Boolean,
    notice:String?,
    onBack:()->Unit,
    onCorrect:(String,Long,String)->Unit,
    onOpenWork:(String)->Unit
) {
    val context=LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now()) }
    var statusFilter by remember { mutableStateOf("ALL") }
    var suiteFilter by remember { mutableStateOf("Все сьюты") }
    var pm1Filter by remember { mutableStateOf("Все PM1") }
    var suiteMenu by remember { mutableStateOf(false) }
    var pm1Menu by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<String?>(null) }
    var correctionTarget by remember { mutableStateOf<JournalLine?>(null) }
    var revisedMinutes by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var copied by remember { mutableStateOf(false) }
    val dayRows=entries.filter{WorkJournalReport.dayOf(it)==date}
    val suites=(listOf("Все сьюты")+dayRows.map{it.work.row.suiteNameSnapshot?:"Не указан"}.distinct().sorted())
    val leaders=(listOf("Все PM1")+dayRows.map{it.work.row.pm1NameSnapshot?:"Не указан"}.distinct().sorted())
    val filtered=dayRows.filter { line ->
        (statusFilter=="ALL" || if(statusFilter=="DONE")line.work.state=="COMPLETED"
            else line.work.state!="COMPLETED") &&
        (suiteFilter=="Все сьюты" || (line.work.row.suiteNameSnapshot?:"Не указан")==suiteFilter) &&
        (pm1Filter=="Все PM1" || (line.work.row.pm1NameSnapshot?:"Не указан")==pm1Filter)
    }
    val done=WorkJournalReport.completed(filtered)
    val summary=WorkJournalReport.message(date,masterName,filtered)

    if(correctionTarget!=null) AlertDialog(
        onDismissRequest={correctionTarget=null},
        containerColor=C.panel,
        title={Text("Исправить факт",color=C.cream)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
                Text("Измеренное время остаётся в журнале. Укажи новый факт для отчёта и причину.",
                    color=C.secondary,fontSize=13.sp)
                OutlinedTextField(
                    value=revisedMinutes,
                    onValueChange={revisedMinutes=it.filter(Char::isDigit).take(4)},
                    label={Text("Фактические минуты (0–1440)")},
                    singleLine=true,modifier=Modifier.fillMaxWidth()
                )
                OutlinedTextField(value=reason,onValueChange={reason=it.take(300)},
                    label={Text("Причина исправления")},modifier=Modifier.fillMaxWidth(),
                    minLines=2)
            }
        },
        confirmButton={
            TextButton(enabled=!pending && (revisedMinutes.toLongOrNull()?:-1L) in 0L..1440L &&
                reason.trim().length>=5,onClick={
                    val id=correctionTarget!!.work.row.id
                    onCorrect(id,revisedMinutes.toLong(),reason)
                    correctionTarget=null
                }){Text("Сохранить исправление",color=C.amber)}
        },
        dismissButton={TextButton(onClick={correctionTarget=null}){Text("Отмена",color=C.secondary)}}
    )

    Column(
        Modifier.fillMaxSize().background(C.canvas).verticalScroll(rememberScrollState())
            .padding(horizontal=18.dp,vertical=16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)
    ){
        TextButton(onClick=onBack){Text("‹ Моя смена",color=C.amber)}
        Text("ПАРИТМ  ·  ЖУРНАЛ РАБОТЫ",fontSize=12.sp,color=C.bronze)
        Text("Мой журнал",fontSize=30.sp,color=C.white,fontWeight=FontWeight.Bold)
        Text("Мастер: $masterName",color=C.cream)
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick={date=date.minusDays(1)}){Text("‹",color=C.amber)}
            OutlinedButton(
                modifier=Modifier.weight(1f),
                onClick={
                    DatePickerDialog(context,{_,year,month,day->
                        date=LocalDate.of(year,month+1,day)
                        suiteFilter="Все сьюты"; pm1Filter="Все PM1"
                    },date.year,date.monthValue-1,date.dayOfMonth).show()
                }
            ){ Text(WorkJournalReport.dayLabel(date),color=C.white) }
            OutlinedButton(onClick={date=date.plusDays(1)}){Text("›",color=C.amber)}
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(7.dp)){
            listOf("ALL" to "Все","DONE" to "Завершённые","ACTIVE" to "Незавершённые")
                .forEach{(key,label)->
                    FilterChip(selected=statusFilter==key,onClick={statusFilter=key},label={Text(label)})
                }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Box(Modifier.weight(1f)){
                OutlinedButton(onClick={suiteMenu=true},modifier=Modifier.fillMaxWidth()){
                    Text(suiteFilter.take(19)+" ▾",maxLines=1,color=C.cream)
                }
                DropdownMenu(expanded=suiteMenu,onDismissRequest={suiteMenu=false}){
                    suites.forEach{value->
                        DropdownMenuItem(text={Text(value)},onClick={suiteFilter=value;suiteMenu=false})
                    }
                }
            }
            Box(Modifier.weight(1f)){
                OutlinedButton(onClick={pm1Menu=true},modifier=Modifier.fillMaxWidth()){
                    Text(pm1Filter.take(19)+" ▾",maxLines=1,color=C.cream)
                }
                DropdownMenu(expanded=pm1Menu,onDismissRequest={pm1Menu=false}){
                    leaders.forEach{value->
                        DropdownMenuItem(text={Text(value)},onClick={pm1Filter=value;pm1Menu=false})
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().background(C.raised,RoundedCornerShape(16.dp))
            .padding(15.dp),verticalArrangement=Arrangement.spacedBy(5.dp)){
            Text("ИТОГ ЗА ДЕНЬ · ПО ФИЛЬТРАМ",color=C.bronze,fontSize=11.sp)
            Text("Завершено: ${done.size} · Всего записей: ${filtered.size}",color=C.cream)
            Text("Суммарный факт: ${WorkJournalReport.totalMinutes(filtered)} мин",
                color=C.fact,fontWeight=FontWeight.Bold,fontSize=21.sp)
            Text("Только завершённые личные процедуры. Это не часы всей смены.",
                color=C.secondary,fontSize=11.sp)
        }
        if(notice!=null)Text(notice,color=C.amber)
        if(filtered.isEmpty()){
            Text("За выбранную дату процедур по этим фильтрам нет.",color=C.secondary)
        }
        filtered.sortedByDescending{
            it.work.events.firstOrNull{event->event.kind=="START"}?.atMs
                ?:it.work.row.createdAtMs
        }.forEach{line->
            val row=line.work.row
            val firstStart=line.work.events.firstOrNull{it.kind=="START"}?.atMs
            val finalEnd=line.work.events.lastOrNull{it.kind=="COMPLETE"}?.atMs
            val expanded=selected==row.id
            Column(
                Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(16.dp))
                    .border(1.dp,C.border,RoundedCornerShape(16.dp)).padding(13.dp),
                verticalArrangement=Arrangement.spacedBy(7.dp)
            ){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(WorkJournalLabels.procedure(row.procedureType),
                        modifier=Modifier.weight(1f),color=C.white,
                        fontWeight=FontWeight.SemiBold)
                    Text(WorkJournalLabels.state(line.work.state),
                        color=if(line.work.state=="RUNNING")C.fact else C.amber,fontSize=12.sp)
                }
                Text("Сьют: ${row.suiteNameSnapshot?:"локальная программа"} · PM1: ${row.pm1NameSnapshot?:"не указан"}",
                    color=C.secondary,fontSize=12.sp)
                Text("Роль ${row.role} · план ${row.plannedMs/60000} мин · факт ${line.reportedMinutes} мин"+
                    if(line.isAdjusted)" (исправлен)" else "",
                    color=C.cream,fontSize=13.sp)
                val times=listOfNotNull(firstStart?.let{"Начало ${WorkJournalReport.hhmm(it)}"},
                    finalEnd?.let{"Конец ${WorkJournalReport.hhmm(it)}"}).joinToString(" · ")
                if(times.isNotBlank())Text(times,color=C.secondary,fontSize=12.sp)
                TextButton(onClick={selected=if(expanded)null else row.id}){
                    Text(if(expanded)"Скрыть события" else "История и действия ▾",color=C.amber)
                }
                if(expanded){
                    Text("Измерено таймером: ${(line.work.elapsedMs+30000L)/60000L} мин",
                        color=C.secondary,fontSize=12.sp)
                    line.work.events.forEach{event->
                        Text("${WorkJournalReport.hhmm(event.atMs)} · ${when(event.kind){
                            "START"->"Начало";"PAUSE"->"Пауза";
                            "RESUME"->"Продолжение";"COMPLETE"->"Завершение";else->event.kind
                        }}",color=C.secondary,fontSize=12.sp)
                    }
                    line.corrections.forEach{entry->
                        Text("✎ ${WorkJournalReport.hhmm(entry.createdAtMs)} · ${entry.priorMinutes} → ${entry.correctedMinutes} мин\n${entry.reason}",
                            color=C.bronze,fontSize=12.sp)
                    }
                    if(line.work.state=="COMPLETED"){
                        OutlinedButton(enabled=!pending,onClick={
                            correctionTarget=line
                            revisedMinutes=line.reportedMinutes.toString();reason=""
                        },modifier=Modifier.fillMaxWidth()){
                            Text("Исправить факт с историей",color=C.amber)
                        }
                    }else{
                        OutlinedButton(onClick={onOpenWork(row.id)},modifier=Modifier.fillMaxWidth()){
                            Text("Перейти к таймеру",color=C.amber)
                        }
                    }
                }
            }
        }
        HorizontalDivider(color=C.border)
        Button(modifier=Modifier.fillMaxWidth(),onClick={
            val clipboard=context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("ПАРИТМ · отчёт",summary))
            copied=true
        }){Text(if(copied)"Отчёт скопирован" else "Скопировать отчёт для WhatsApp")}
        OutlinedButton(modifier=Modifier.fillMaxWidth(),onClick={
            val intent=Intent(Intent.ACTION_SEND).apply{
                type="text/plain"
                putExtra(Intent.EXTRA_TEXT,summary)
            }
            context.startActivity(Intent.createChooser(intent,"Отправить отчёт"))
        }){Text("Поделиться отчётом")}
        Text("Записи хранятся локально в Room. Исправление факта не удаляет событий таймера.",
            color=C.secondary,fontSize=12.sp)
        Spacer(Modifier.height(24.dp))
    }
}
