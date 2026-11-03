package kz.terensai.rhythm.feature.entry

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kz.terensai.rhythm.data.ParitmBackupService
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.feature.checklists.P22ChecklistScreen
import kz.terensai.rhythm.feature.session.SuitePicker
import kz.terensai.rhythm.feature.session.B23ShiftFlow
import kz.terensai.rhythm.ui.theme.ParitmColors as C

/**
 * N1 shell. All six sections are visible. Available features route into the
 * existing Room-backed screens; unfinished modules clearly say so, with no fake
 * controls or analytics. The authoritative state remains in the repositories.
 */
@Composable
fun N1NavigationShell(
    hasActiveSession:Boolean,
    openPilotTimer:()->Unit,
    openShift:()->Unit,
    openReports:()->Unit,
    openMasters:()->Unit,
    showQa:Boolean=false,
    openQa:()->Unit={}
) {
    val context=LocalContext.current
    val scope=rememberCoroutineScope()
    var backupMessage by remember{mutableStateOf<String?>(null)}
    var restoreDialog by remember{mutableStateOf(false)}
    var backupBusy by remember{mutableStateOf(false)}
    val backupExport=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ){uri->
        if(uri!=null)scope.launch{
            backupBusy=true
            try{backupMessage=ParitmBackupService.export(context,uri)}
            catch(e:Exception){backupMessage="Ошибка экспорта: ${e.message}"}
            finally{backupBusy=false}
        }
    }
    val backupImport=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ){uri->
        if(uri!=null)scope.launch{
            backupBusy=true
            try{
                backupMessage=ParitmBackupService.stageImport(context,uri)
                restoreDialog=true
            }catch(e:Exception){backupMessage="Ошибка проверки копии: ${e.message}"}
            finally{backupBusy=false}
        }
    }
    var tab by rememberSaveable {mutableStateOf("home")}
    var chosenSuite by rememberSaveable {mutableStateOf("")}
    var mastersInSettings by rememberSaveable {mutableStateOf(false)}
    var templatesInSettings by rememberSaveable {mutableStateOf(false)}
    var newProgrammeRequested by rememberSaveable {mutableIntStateOf(0)}
    var prepareNewProgramme by rememberSaveable {mutableStateOf(false)}
    var prepareCustomProgramme by rememberSaveable {mutableStateOf(false)}
    var standardProgrammeToPrepare by rememberSaveable {mutableStateOf<String?>(null)}
    fun openStandardProgramme(id:String){
        prepareNewProgramme=false
        prepareCustomProgramme=false
        standardProgrammeToPrepare=id
        newProgrammeRequested+=1
        tab="shift"
    }
    fun openNewProgramme(){
        prepareNewProgramme=true
        prepareCustomProgramme=false
        standardProgrammeToPrepare=null
        newProgrammeRequested+=1
        tab="shift"
    }
    fun openCustomProgramme(){
        prepareNewProgramme=false
        prepareCustomProgramme=true
        standardProgrammeToPrepare=null
        newProgrammeRequested+=1
        tab="shift"
    }
    BackHandler(enabled=tab!="home" && !mastersInSettings && !templatesInSettings){tab="home"}
    // Do not clear pending navigation here: the standard-route request must
    // reach B23ShiftFlow before it is consumed. Clear only for ordinary shift navigation.
    fun openOrdinaryShift(){
        prepareNewProgramme=false
        prepareCustomProgramme=false
        standardProgrammeToPrepare=null
        tab="shift"
    }
    if(restoreDialog)AlertDialog(
        onDismissRequest={
            restoreDialog=false
            ParitmBackupService.cancelStagedRestore(context)
        },
        title={Text("Восстановить данные из копии?")},
        text={Text("Текущая база будет сохранена во внутреннюю резервную папку, а выбранная копия заменит её при следующем запуске. Приложение сейчас закроется. Не продолжай работу с текущими сессиями после восстановления.")},
        confirmButton={TextButton(onClick={
            try{
                ParitmBackupService.armStagedRestore(context)
                restoreDialog=false
                android.os.Process.killProcess(android.os.Process.myPid())
            }catch(e:Exception){
                backupMessage="Не удалось подтвердить восстановление: ${e.message}"
                restoreDialog=false
            }
        }){Text("Подтверждаю · закрыть приложение",color=C.amber)}},
        dismissButton={TextButton(onClick={
            restoreDialog=false
            ParitmBackupService.cancelStagedRestore(context)
        }){Text("Отмена",color=C.white)}}
    )
    Column(Modifier.fillMaxSize().background(C.canvas)){
        Box(Modifier.weight(1f)){
            if(mastersInSettings) {
                B23ShiftFlow(onBack={mastersInSettings=false},initialMasters=true)
            } else if(templatesInSettings) {
                P22ChecklistScreen(onBack={templatesInSettings=false},initialEditor=true)
            } else when(tab){
                "shift","reports"->{
                    key(tab,newProgrammeRequested){
                        B23ShiftFlow(
                            onBack={tab="home"},
                            initialNew=tab=="shift" && prepareNewProgramme,
                            initialCustom=tab=="shift" && prepareCustomProgramme,
                            initialStandard=if(tab=="shift") standardProgrammeToPrepare else null,
                            initialJournal=tab=="reports"
                        )
                    }
                }
                "programmes"->ProgrammesScreen(
                    hasActiveSession=hasActiveSession,
                    onBack={tab="home"},
                    onPilot=::openNewProgramme,
                    onCustom=::openCustomProgramme,
                    onStandard=::openStandardProgramme,
                    showQa=showQa,
                    onQa=openQa,
                    showNewTimeline=true,
                    showMultiSession=true,
                    onShift=::openOrdinaryShift
                )
                "analytics"->N1InfoScreen(
                    title="Аналитика",
                    status="В разработке · этап A",
                    paragraphs=listOf(
                        "Этот раздел появился в навигации, но расчёт статистики пока не реализован.",
                        "Здесь будут итоги выполненных процедур, работа по сьютам и PM1, динамика смен и программы. Незавершённые процедуры не будут считаться выполненными.",
                        "Для текущих подтверждённых записей используй «Отчёты» → Журнал работы."
                    ),
                    primaryLabel="Открыть мои отчёты",
                    onPrimary={tab="reports"}
                )
                "settings"->Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                        .padding(horizontal=18.dp,vertical=18.dp),
                    verticalArrangement=Arrangement.spacedBy(16.dp)
                ){
                    Text("ПАРИТМ  ·  НАСТРОЙКИ",color=C.bronze,fontSize=12.sp)
                    Text("Настройки",color=C.white,fontSize=29.sp,fontWeight=FontWeight.Bold)
                    Column(
                        Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(18.dp))
                            .border(1.dp,C.border,RoundedCornerShape(18.dp)).padding(15.dp),
                        verticalArrangement=Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Справочник сьютов",color=C.cream,
                            fontWeight=FontWeight.SemiBold,fontSize=20.sp)
                        Text("Добавляй и переименовывай места работы. Исторические отчёты сохраняют название сьюта на момент записи.",
                            color=C.secondary,fontSize=13.sp)
                        SuitePicker(selected=chosenSuite,onSelected={chosenSuite=it})
                        Text("Порядок: 1 Көркемдік · 2 Өркендеу · 3 Байлық · 4 Амандық · 5 Сәттілік. История предыдущих сессий сохраняется.",
                            color=C.bronze,fontSize=12.sp)
                    }
                    Text("Резервная копия данных",color=C.cream,fontSize=20.sp,
                        fontWeight=FontWeight.SemiBold)
                    Text("Полная локальная копия SQLite содержит историю программ, отчёты, чек-листы и имена мастеров. Храни её только в доверенном месте. Файл не отправляется автоматически.",
                        color=C.secondary,fontSize=13.sp)
                    OutlinedButton(enabled=!backupBusy,
                        onClick={backupExport.launch("PARITM-backup-${System.currentTimeMillis()}.sqlite")},
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Сохранить резервную копию",color=C.amber)
                    }
                    OutlinedButton(enabled=!backupBusy,
                        onClick={backupImport.launch(arrayOf("*/*"))},
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Восстановить базу из копии…",color=C.bronze)
                    }
                    if(backupMessage!=null)Text(backupMessage!!,color=C.amber,fontSize=13.sp)
                    OutlinedButton(onClick={templatesInSettings=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Шаблоны чек-листов · редактор и JSON",color=C.amber)
                    }
                    OutlinedButton(onClick={mastersInSettings=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Мастера · мой профиль и справочник",color=C.amber)
                    }
                    Column(
                        Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(18.dp))
                            .padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp)
                    ){
                        Text("Звуки и напоминания",color=C.cream,fontSize=19.sp)
                        Text("Настройка звуков, уведомлений и шаблонов подготовки печей и трав появится в версии D. Пока эти функции не активны.",
                            color=C.secondary,fontSize=13.sp)
                        Text("Конструктор программ — отдельный этап P2.",
                            color=C.secondary,fontSize=13.sp)
                    }
                }
                else->WelcomeScreen(
                    hasActiveSession=hasActiveSession,
                    onProgrammes={tab="programmes"},
                    onContinue=::openOrdinaryShift,
                    showNewTimeline=true,
                    showMultiSession=true,
                    onShift=::openOrdinaryShift
                )
            }
        }
        N1BottomNavigation(
            selected=tab,
            onHome={tab="home"},
            onProgrammes={tab="programmes"},
            onShift=::openOrdinaryShift,
            onReports={tab="reports"},
            onAnalytics={tab="analytics"},
            onSettings={mastersInSettings=false;templatesInSettings=false;tab="settings"}
        )
    }
}

@Composable
private fun N1InfoScreen(
    title:String,status:String,paragraphs:List<String>,
    primaryLabel:String,onPrimary:()->Unit
){
    Column(Modifier.fillMaxSize().background(C.canvas).verticalScroll(rememberScrollState())
        .padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("ПАРИТМ  ·  РАЗДЕЛ",color=C.bronze,fontSize=12.sp)
        Text(title,color=C.white,fontSize=30.sp,fontWeight=FontWeight.Bold)
        Text(status,color=C.amber,fontSize=14.sp)
        paragraphs.forEach { paragraph->
            Text(paragraph,color=C.secondary,fontSize=14.sp)
        }
        OutlinedButton(onClick=onPrimary,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)){
            Text(primaryLabel,color=C.amber)
        }
    }
}

/** Compact 2×3 menu keeps every destination visible on a narrow phone. */
@Composable
private fun N1BottomNavigation(
    selected:String,
    onHome:()->Unit,
    onProgrammes:()->Unit,
    onShift:()->Unit,
    onReports:()->Unit,
    onAnalytics:()->Unit,
    onSettings:()->Unit
){
    val routes=listOf(
        Triple("home","Главная",onHome),
        Triple("programmes","Программы",onProgrammes),
        Triple("shift","Моя смена",onShift),
        Triple("reports","Отчёты",onReports),
        Triple("analytics","Аналитика",onAnalytics),
        Triple("settings","Настройки",onSettings)
    )
    Column(Modifier.fillMaxWidth().background(C.ink).padding(horizontal=7.dp,vertical=6.dp),
        verticalArrangement=Arrangement.spacedBy(5.dp)){
        routes.chunked(3).forEach { rowItems->
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){
                rowItems.forEach {(key,label,click)->
                    val active=selected==key
                    OutlinedButton(
                        onClick=click,modifier=Modifier.weight(1f).heightIn(min=45.dp),
                        shape=RoundedCornerShape(12.dp)
                    ){
                        Text(label,color=if(active)C.fact else C.cream,
                            fontWeight=if(active)FontWeight.Bold else FontWeight.Normal,
                            fontSize=11.sp,maxLines=1)
                    }
                }
            }
        }
    }
}
