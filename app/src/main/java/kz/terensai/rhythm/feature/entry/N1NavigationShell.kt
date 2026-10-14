package kz.terensai.rhythm.feature.entry

import androidx.activity.compose.BackHandler
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
    var tab by rememberSaveable {mutableStateOf("home")}
    var chosenSuite by rememberSaveable {mutableStateOf("")}
    var mastersInSettings by rememberSaveable {mutableStateOf(false)}
    var newProgrammeRequested by rememberSaveable {mutableIntStateOf(0)}
    var prepareNewProgramme by rememberSaveable {mutableStateOf(false)}
    fun openNewProgramme(){
        prepareNewProgramme=true
        newProgrammeRequested+=1
        tab="shift"
    }
    BackHandler(enabled=tab!="home" && !mastersInSettings){tab="home"}
    Column(Modifier.fillMaxSize().background(C.canvas)){
        Box(Modifier.weight(1f)){
            if(mastersInSettings) {
                B23ShiftFlow(onBack={mastersInSettings=false},initialMasters=true)
            } else when(tab){
                "shift","reports"->{
                    key(tab,newProgrammeRequested){
                        B23ShiftFlow(
                            onBack={tab="home"},
                            initialNew=tab=="shift" && prepareNewProgramme,
                            initialJournal=tab=="reports"
                        )
                    }
                }
                "programmes"->ProgrammesScreen(
                    hasActiveSession=hasActiveSession,
                    onBack={tab="home"},
                    onPilot=::openNewProgramme,
                    showQa=showQa,
                    onQa=openQa,
                    showNewTimeline=true,
                    showMultiSession=true,
                    onShift={tab="shift"}
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
                    onContinue={tab="shift"},
                    showNewTimeline=true,
                    showMultiSession=true,
                    onShift={tab="shift"}
                )
            }
        }
        N1BottomNavigation(
            selected=tab,
            onHome={tab="home"},
            onProgrammes={tab="programmes"},
            onShift={tab="shift"},
            onReports={tab="reports"},
            onAnalytics={tab="analytics"},
            onSettings={mastersInSettings=false;tab="settings"}
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
