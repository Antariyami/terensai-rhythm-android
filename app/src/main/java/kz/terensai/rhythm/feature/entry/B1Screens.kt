package kz.terensai.rhythm.feature.entry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.R
import kz.terensai.rhythm.domain.ProgrammeCatalog
import kz.terensai.rhythm.domain.ProgrammeSummary
import kz.terensai.rhythm.domain.TemplateStatus
import kz.terensai.rhythm.domain.displayDuration
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton

@Composable
fun ParitmB1Root(hasOldActiveSession:Boolean,openPilotTimer:()->Unit,
  showQa:Boolean=false,openQa:()->Unit={}) {
  var screen by rememberSaveable { mutableStateOf("welcome") }
  BackHandler(enabled=screen=="programmes") { screen="welcome" }
  if (screen=="welcome") {
    WelcomeScreen(
      hasActiveSession=hasOldActiveSession,
      onProgrammes={screen="programmes"},
      onContinue= openPilotTimer
    )
  } else {
    ProgrammesScreen(
      hasActiveSession=hasOldActiveSession,
      onBack={screen="welcome"},
      onPilot=openPilotTimer,
      showQa=showQa,
      onQa=openQa
    )
  }
}

@Composable
private fun BrandMark(modifier:Modifier=Modifier) {
  // This is a separately packaged approved emblem, not a flattened screenshot of UI.
  Image(
    painter=painterResource(R.drawable.paritm_approved_mark_hq),
    contentDescription="ПАРИТМ — медальон с часами, паром и дубовыми листьями",
    modifier=modifier
  )
}

@Composable
private fun WelcomeScreen(
  hasActiveSession:Boolean,
  onProgrammes:()->Unit,
  onContinue:()->Unit
) {
  Box(Modifier.fillMaxSize().background(C.canvas)) {
    ParitmAtmosphere()
    Column(
      Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal=24.dp,vertical=32.dp),
      horizontalAlignment=Alignment.CenterHorizontally,
      verticalArrangement=Arrangement.Center
    ) {
      Spacer(Modifier.height(16.dp))
      Box(Modifier.size(214.dp)
        .background(
          Brush.radialGradient(listOf(Color(0x66DAA45A),Color(0x22462922),Color.Transparent)),
          RoundedCornerShape(46.dp)
        ),contentAlignment=Alignment.Center) {
        // High-resolution, approved artwork from original reference; avoids enlarging 128px preview.
        BrandMark(Modifier.size(186.dp).clip(RoundedCornerShape(32.dp)))
      }
      Spacer(Modifier.height(26.dp))
      Text("ПАРИТМ",color=C.cream,fontFamily=FontFamily.Serif,fontSize=40.sp,
        fontWeight=FontWeight.SemiBold,letterSpacing=3.sp)
      Text("Ритм банного мастерства",color=C.bronze,fontSize=16.sp,textAlign=TextAlign.Center)
      Spacer(Modifier.height(22.dp))
      Text("Планируй  ·  Проводи  ·  Анализируй",
        color=C.white,fontSize=15.sp,textAlign=TextAlign.Center)
      Spacer(Modifier.height(32.dp))
      ParitmPrimaryButton(label="Выбрать программу  →",onClick=onProgrammes,
        modifier=Modifier.fillMaxWidth())
      if(hasActiveSession) {
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick=onContinue,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
          shape=RoundedCornerShape(18.dp)) {
          Text("Продолжить пилотную сессию",color=C.white)
        }
      }
      Spacer(Modifier.height(30.dp))
      Text("Личный помощник пармастера  •  офлайн",
        color=C.muted,fontSize=12.sp,textAlign=TextAlign.Center)
    }
  }
}

@Composable
private fun ProgrammesScreen(
  hasActiveSession:Boolean,onBack:()->Unit,onPilot:()->Unit,
  showQa:Boolean,onQa:()->Unit
) {
  var selectionId by rememberSaveable { mutableStateOf<String?>(null) }
  val chosen=ProgrammeCatalog.get(selectionId ?: "")
  Column(Modifier.fillMaxSize().background(C.canvas)
    .padding(top=16.dp,start=16.dp,end=16.dp,bottom=8.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically) {
      Text("‹",fontSize=38.sp,color=C.amber,
        modifier=Modifier.clickable { onBack() }
          .semantics { contentDescription="Вернуться к приветствию" }
          .padding(end=10.dp))
      BrandMark(Modifier.size(46.dp))
      Spacer(Modifier.width(12.dp))
      Column {
        Text("ПАРИТМ",color=C.cream,fontSize=19.sp,
          fontFamily=FontFamily.Serif,fontWeight=FontWeight.SemiBold)
        Text("Программы",color=C.muted,fontSize=12.sp)
      }
    }
    Spacer(Modifier.height(20.dp))
    Text("Выберите программу",color=C.white,fontSize=26.sp,fontWeight=FontWeight.Bold)
    Spacer(Modifier.height(6.dp))
    Text("Выберите сценарий. Неподтверждённые программы — пока только для просмотра.",
      color=C.secondary,fontSize=13.sp,lineHeight=19.sp)
    Spacer(Modifier.height(14.dp))
    LazyVerticalGrid(
      columns=GridCells.Fixed(2),
      horizontalArrangement=Arrangement.spacedBy(12.dp),
      verticalArrangement=Arrangement.spacedBy(12.dp),
      modifier=Modifier.weight(1f)
    ){
      items(ProgrammeCatalog.programmes,key={it.id}) { item ->
        ProgrammeTile(item) { selectionId=item.id }
      }
    }
    if(hasActiveSession) {
      Spacer(Modifier.height(8.dp))
      ParitmPrimaryButton("Продолжить текущую сессию",onPilot,
        modifier=Modifier.fillMaxWidth())
    }
    if (showQa) {
      Spacer(Modifier.height(10.dp))
      OutlinedButton(onClick=onQa,modifier=Modifier.fillMaxWidth().heightIn(min=54.dp),
        shape=RoundedCornerShape(18.dp)) {
        Text("Проверка B2.1 · сохранение сессии",color=C.amber)
      }
    }
    Spacer(Modifier.height(10.dp))
    Text("Архив, отчёты и аналитика появятся в следующих версиях.",
      color=C.muted,fontSize=12.sp,modifier=Modifier.fillMaxWidth().padding(bottom=10.dp),
      textAlign=TextAlign.Center)
  }

  chosen?.let { programme ->
    val pilot=programme.status==TemplateStatus.PILOT
    AlertDialog(onDismissRequest={selectionId=null},containerColor=C.panel,
      title={Text(programme.name,color=C.white)},
      text={
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
          Text(programme.summary,color=C.white)
          Text(programme.structure,color=C.secondary)
          Text(displayDuration(programme.durationMinutes),color=C.amber)
          if(!pilot) Text("Сценарий пока не готов к запуску. Детальные этапы будут подключены в B2.",
            color=C.secondary)
          else Text("Доступен старый пилотный таймер «Танысу». Новый план/факт появится в B2.",
            color=C.secondary)
        }
      },
      confirmButton={
        TextButton(onClick={
          selectionId=null
          if(pilot) onPilot()
        }) {
          Text(if(pilot) "Открыть пилотный таймер" else "Понятно",color=C.amber)
        }
      },
      dismissButton={
        if(pilot) TextButton(onClick={selectionId=null}) {
          Text("Отмена",color=C.secondary)
        }
      }
    )
  }
}

@Composable
private fun ProgrammeTile(item:ProgrammeSummary,onClick:()->Unit) {
  val pilot=item.status==TemplateStatus.PILOT
  val shape=RoundedCornerShape(18.dp)
  Box(
    modifier=Modifier.fillMaxWidth().height(196.dp)
      .clip(shape)
      .background(C.panel)
      .border(1.dp,if(pilot) C.bronze else C.border,shape)
      .clickable(onClick=onClick)
      .semantics { contentDescription=item.name+", "+displayDuration(item.durationMinutes) }
  ) {
    ParitmTileAtmosphere()
    Text(item.monogram, color=C.bronze.copy(alpha=.12f), fontSize=80.sp,
      fontFamily=FontFamily.Serif,fontWeight=FontWeight.Bold,
      modifier=Modifier.align(Alignment.CenterEnd).padding(end=10.dp))
    Column(Modifier.fillMaxSize().padding(13.dp),
      verticalArrangement=Arrangement.SpaceBetween) {
      Row(verticalAlignment=Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp))
          .background(Color(0xBB1A120E)).border(1.dp,C.bronze,RoundedCornerShape(12.dp)),
          contentAlignment=Alignment.Center) {
          Text(item.monogram,color=C.cream,fontFamily=FontFamily.Serif,
            fontSize=20.sp,fontWeight=FontWeight.SemiBold)
        }
        Spacer(Modifier.width(8.dp))
        Text(if(pilot) "ПИЛОТ" else if(item.status==TemplateStatus.PREVIEW) "ПРЕВЬЮ" else "ЧЕРНОВИК",
          color=if(pilot) C.success else C.secondary,fontSize=10.sp,
          fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
      }
      Column {
        Text(item.name,color=C.white,fontSize=19.sp,fontWeight=FontWeight.Bold,
          maxLines=1,overflow=TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(displayDuration(item.durationMinutes),color=C.amber,fontSize=14.sp)
        Spacer(Modifier.height(5.dp))
        Text(item.structure,color=C.secondary,fontSize=12.sp,
          maxLines=2,overflow=TextOverflow.Ellipsis,lineHeight=16.sp)
      }
    }
  }
}
