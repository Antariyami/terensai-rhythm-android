package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.data.SessionRepository
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.PilotTanysuPlan
import kz.terensai.rhythm.ui.theme.ParitmColors as C

@Composable
fun B22SessionFlow(onBack: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SessionRepository(ParitmDatabase.get(context)) }
    val scope = rememberCoroutineScope()
    var current by remember { mutableStateOf<SavedSession?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(repo) {
        while (true) {
            try {
                val restored = repo.active()
                if (!pending) current = restored
                loaded = true
            } catch (e: Exception) {
                message = "Ошибка чтения базы: " + (e.message ?: "неизвестная")
                loaded = true
            }
            delay(650L)
        }
    }
    fun execute(action: suspend () -> SavedSession) {
        if (pending) return
        pending = true
        scope.launch {
            try {
                val result = action()
                current = if (result.finishedAtMs == null) result else null
                message = if (result.finishedAtMs != null) "Сессия сохранена" else "Изменение сохранено"
            } catch (e: Exception) {
                message = "Ошибка сохранения: " + (e.message ?: "проверьте действие")
            } finally {
                pending = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(C.canvas)) {
        if (!loaded) Text("Восстанавливаем сессию из Room…", color=C.white)
        else if (current == null) {
            B22PrepareScreen(
                notice = message, pending = pending, onBack = onBack,
                onStart = { durations, suite, guests ->
                    execute { repo.start(PilotTanysuPlan.toPlan(durations),
                        SessionMetadata(suite = suite, guestCount = guests)) }
                }
            )
        } else {
            val active = current!!
            B22TimelineScreen(
                saved = active, notice = message, pending = pending, onBack = onBack,
                onCommand = { stageId, kind ->
                    execute { repo.perform(active.id, stageId, kind) }
                },
                onReopen = { stageId -> execute { repo.reopenPrevious(active.id, stageId) } },
                onFinish = { execute { repo.finish(active.id) } }
            )
        }
    }
}
