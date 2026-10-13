package kz.terensai.rhythm.feature.qa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.data.SessionRepository
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.domain.StagePlan
import kz.terensai.rhythm.domain.StageStatus
import kz.terensai.rhythm.domain.Track
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import kz.terensai.rhythm.ui.theme.ParitmPrimaryButton
import kz.terensai.rhythm.ui.theme.ParitmTheme
import java.util.Locale

/** Technical test route only: B2.2 will supply the approved actual programme screen. */
class B21QaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ParitmTheme { B21PersistenceCheck() } }
    }
}

private fun qaPlan() = ProgramPlan(
    id = "qa-b21-sandbox",
    title = "Тест сохранения B2.1 (не стандарт TERENSAI)",
    plannedDurationMs = 15 * 60_000L,
    stages = listOf(
        StagePlan("qa-one", "Тестовый этап", Track.GUEST, 0L, 5 * 60_000L)
    )
)

private fun asTimer(ms: Long): String {
    val n = ms.coerceAtLeast(0L) / 1000L
    return String.format(Locale.ROOT, "%02d:%02d:%02d", n / 3600L, n / 60L % 60L, n % 60L)
}

@Composable
private fun B21PersistenceCheck() {
    val context = LocalContext.current
    val repo = remember { SessionRepository(ParitmDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<SavedSession?>(null) }
    var working by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Загружаем сохранённую сессию…") }

    LaunchedEffect(repo) {
        while (true) {
            try {
                // UI merely refreshes an immutable projection from persisted timestamps.
                // It never operates a second stopwatch.
                val live = repo.active()
                if (live != null) current = live
                if (notice == "Загружаем сохранённую сессию…") {
                    notice = if (live == null) "Активных тестовых сессий нет" else "Восстановлена из Room"
                }
            } catch (e: Exception) {
                notice = "Ошибка чтения: " + (e.message ?: "неизвестна")
            }
            delay(1000L)
        }
    }

    fun execute(block: suspend () -> SavedSession) {
        if (working) return
        working = true
        scope.launch {
            try {
                current = block()
                notice = "Действие сохранено в Room"
            } catch (e: Exception) {
                notice = "Ошибка: " + (e.message ?: "проверьте состояние")
            } finally {
                working = false
            }
        }
    }

    val live = current?.takeIf { it.finishedAtMs == null }
    val stage = live?.progress?.stages?.firstOrNull()
    Column(
        modifier = Modifier.fillMaxSize().background(C.canvas)
            .verticalScroll(rememberScrollState())
            .fillMaxWidth()
            .then(Modifier),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        androidx.compose.foundation.layout.Box(Modifier.height(12.dp))
        Text("ПАРИТМ · B2.1", modifier=Modifier.fillMaxWidth(),
            color=C.amber,fontSize=24.sp,fontWeight=FontWeight.Bold)
        Text("Проверка сохранения, не финальный экран таймера",
            color=C.secondary,fontSize=15.sp)
        Text(notice,color=C.bronze,fontSize=13.sp)
        if (live == null) {
            Text("Тест: одна программа 15 мин, один этап 5 мин. Эти минуты не являются регламентом банных программ.",
                color=C.white,fontSize=14.sp)
            ParitmPrimaryButton("Создать тестовую сессию", { execute { repo.start(qaPlan()) } },
                enabled=!working,modifier=Modifier.fillMaxWidth())
        } else {
            Text("Сессия: " + live.id.take(12),color=C.secondary)
            Text("Прошло с запуска: " + asTimer(live.progress.elapsedWallClockMs),
                color=C.white,fontSize=22.sp)
            Text("Активный этап: " + (stage?.plan?.title ?: "нет"),color=C.white)
            Text("Этап: " + asTimer(stage?.elapsedMs ?: 0L),color=C.amber,fontSize=37.sp)
            Text("Статус: " + (stage?.status?.name ?: "нет") +
                " · сохранённых событий: " + live.eventCount,
                color=C.secondary,fontSize=14.sp)
            val sessionId = live.id
            val stageId = stage?.plan?.id ?: "qa-one"
            if(stage?.status == StageStatus.NOT_STARTED) {
                ParitmPrimaryButton("Начать этап", { execute {
                    repo.perform(sessionId,stageId,EventKind.START)
                }},enabled=!working,modifier=Modifier.fillMaxWidth())
            }
            if(stage?.status == StageStatus.RUNNING) {
                OutlinedButton(onClick={execute{
                    repo.perform(sessionId,stageId,EventKind.PAUSE)
                }},enabled=!working,modifier=Modifier.fillMaxWidth()) { Text("Пауза этапа") }
            }
            if(stage?.status == StageStatus.PAUSED) {
                ParitmPrimaryButton("Продолжить этап", {execute{
                    repo.perform(sessionId,stageId,EventKind.RESUME)
                }},enabled=!working,modifier=Modifier.fillMaxWidth())
            }
            if(stage?.status == StageStatus.RUNNING || stage?.status == StageStatus.PAUSED) {
                OutlinedButton(onClick={execute{
                    repo.perform(sessionId,stageId,EventKind.COMPLETE)
                }},enabled=!working,modifier=Modifier.fillMaxWidth()) {
                    Text("Завершить этап")
                }
            }
            if(stage?.status == StageStatus.COMPLETED || stage?.status == StageStatus.SKIPPED) {
                OutlinedButton(onClick={execute{repo.finish(sessionId)}},
                    enabled=!working,modifier=Modifier.fillMaxWidth()) {
                    Text("Завершить тестовую сессию")
                }
            }
        }
        Text("Проверка: запустите этап, нажмите «Пауза», выйдите назад, полностью закройте приложение и откройте проверку повторно. ID и фактическое время должны восстановиться. Во время паузы идёт только общее время.",
            color=C.muted,fontSize=13.sp)
        Spacer(Modifier.height(18.dp))
    }
}
