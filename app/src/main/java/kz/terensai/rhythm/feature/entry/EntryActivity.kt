package kz.terensai.rhythm.feature.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kz.terensai.rhythm.MainActivity
import kz.terensai.rhythm.R
import kz.terensai.rhythm.domain.ProgramCard
import kz.terensai.rhythm.domain.ProgramCatalogue
import kz.terensai.rhythm.domain.TemplateStatus

private val background = Color(0xFF15110F)
private val surface = Color(0xFF281E1A)
private val amber = Color(0xFFF2A047)
private val bronze = Color(0xFFC69B66)
private val ivory = Color(0xFFF6F1E8)
private val muted = Color(0xFFC4B6A6)
private val green = Color(0xFF95AC8C)

class EntryActivity : ComponentActivity() {
    private var hasActiveSession by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(21, 17, 15)
        window.navigationBarColor = android.graphics.Color.rgb(21, 17, 15)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = amber, onPrimary = background, background = background,
                surface = surface, onSurface = ivory
            )) {
                ParitmEntry(
                    activeSession = hasActiveSession,
                    openLegacyTimer = {
                        startActivity(Intent(this, MainActivity::class.java))
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasActiveSession = getSharedPreferences("rhythm_state_v1", MODE_PRIVATE)
            .getLong("program", 0L) > 0L
    }
}

@Composable
private fun ParitmEntry(activeSession: Boolean, openLegacyTimer: () -> Unit) {
    var view by rememberSaveable { mutableStateOf("welcome") }
    when (view) {
        "welcome" -> WelcomeScreen(
            activeSession = activeSession,
            showCatalogue = { view = "programmes" },
            continueSession = openLegacyTimer
        )
        else -> ProgrammeScreen(
            activeSession = activeSession,
            back = { view = "welcome" },
            openLegacyTimer = openLegacyTimer
        )
    }
}

@Composable
private fun WelcomeScreen(
    activeSession: Boolean,
    showCatalogue: () -> Unit,
    continueSession: () -> Unit
) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF3A2419), background, Color(0xFF100D0B)))
        ).padding(horizontal = 24.dp, vertical = 26.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.paritm_logo),
                contentDescription = "Знак ПАРИТМ — часы, пар и дубовые листья",
                modifier = Modifier.size(205.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text("ПАРИТМ", fontSize = 40.sp, color = ivory, fontWeight = FontWeight.Light)
            Text("Ритм банного мастерства", fontSize = 16.sp, color = bronze)
            Spacer(Modifier.height(42.dp))
            Text(
                "Планируй · Проводи · Анализируй",
                fontSize = 17.sp, color = ivory, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(34.dp))
            Button(
                onClick = showCatalogue,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = amber, contentColor = background)
            ) {
                Text("Выбрать программу  →", fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
            if (activeSession) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = continueSession,
                    modifier = Modifier.fillMaxWidth().height(58.dp),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text("Продолжить текущую сессию", color = ivory)
                }
            }
        }
        Text(
            text = "Личный помощник пармастера · офлайн",
            color = muted,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun ProgrammeScreen(
    activeSession: Boolean,
    back: () -> Unit,
    openLegacyTimer: () -> Unit
) {
    var selected by remember { mutableStateOf<ProgramCard?>(null) }
    Column(
        Modifier.fillMaxSize().background(background).padding(
            start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp
        )
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "‹", color = amber, fontSize = 38.sp,
                modifier = Modifier.clickable(onClick = back).padding(end = 16.dp)
                    .semantics { contentDescription = "Назад к приветствию" }
            )
            Image(
                painterResource(R.drawable.paritm_logo),
                contentDescription = null,
                modifier = Modifier.size(48.dp)
            )
            Text("ПАРИТМ", color = bronze, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(18.dp))
        Text("Выберите программу", color = ivory, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Длительность — по доступным описаниям. Пока запускать можно только пилотную «Танысу».",
            color = muted, fontSize = 14.sp, lineHeight = 19.sp
        )
        Spacer(Modifier.height(20.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(ProgramCatalogue.programmes, key = { it.key }) { programme ->
                ProgrammeTile(programme) { selected = programme }
            }
        }
        if (activeSession) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = openLegacyTimer,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(14.dp)
            ) { Text("Продолжить активную сессию", color = ivory) }
        }
    }

    selected?.let { item ->
        val canOpen = item.status == TemplateStatus.PILOT
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(item.title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.description)
                    Text(item.ritualSummary)
                    Text(item.durationMinutes?.let { "${it / 60} ч ${it % 60} мин" }
                        ?: "Продолжительность пока не утверждена")
                    if (!canOpen) Text("Полный сценарий будет подключён в следующих итерациях.")
                }
            },
            confirmButton = {
                if (canOpen) {
                    TextButton(onClick = {
                        selected = null
                        openLegacyTimer()
                    }) { Text(if (activeSession) "Открыть текущую сессию" else "Открыть таймер") }
                } else {
                    TextButton(onClick = { selected = null }) { Text("Понятно") }
                }
            },
            dismissButton = {
                if (canOpen) TextButton(onClick = { selected = null }) { Text("Назад") }
            },
            containerColor = surface, titleContentColor = ivory, textContentColor = muted
        )
    }
}

@Composable
private fun ProgrammeTile(programme: ProgramCard, onClick: () -> Unit) {
    val pilot = programme.status == TemplateStatus.PILOT
    Column(
        modifier = Modifier
            .height(166.dp)
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(if (pilot) Color(0xFF6A3D20) else Color(0xFF39281E), surface)
                ),
                RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick)
            .semantics { contentDescription = "${programme.title}: ${programme.description}" }
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            when(programme.status) {
                TemplateStatus.PILOT -> "● ПИЛОТ"
                TemplateStatus.PREVIEW -> "◌ ПРЕВЬЮ"
                TemplateStatus.UNVERIFIED -> "◌ ЧЕРНОВИК"
            },
            fontSize = 10.sp, color = if (pilot) green else bronze
        )
        Column {
            Text(programme.title, color = ivory, fontSize = 19.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            Text(
                programme.durationMinutes?.let {
                    if (it % 60 == 0) "${it / 60} часа" else "${it / 60} ч ${it % 60} мин"
                } ?: "Время уточняется",
                color = amber, fontSize = 13.sp
            )
            Text(programme.ritualSummary, color = muted, fontSize = 12.sp,
                lineHeight = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
