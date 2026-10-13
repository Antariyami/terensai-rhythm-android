package kz.terensai.rhythm.feature.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kz.terensai.rhythm.data.LocalInstallIdentity
import kz.terensai.rhythm.data.SavedSession
import kz.terensai.rhythm.data.SessionRepository
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.PilotTanysuPlan
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import java.util.UUID

/**
 * A single selected programme is on-screen, but the projection of every other
 * Room-backed session continues to advance from its own timestamps.
 */
@Composable
fun B23ShiftFlow(onBack: () -> Unit, initialNew: Boolean = false) {
    val context = LocalContext.current
    val repo = remember(context) {
        SessionRepository(
            database = ParitmDatabase.get(context),
            originClientId = LocalInstallIdentity.get(context)
        )
    }
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<SavedSession>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by rememberSaveable { mutableStateOf(initialNew) }
    // Saved across configuration changes; same user Start request has same UUID.
    var creationRequestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    LaunchedEffect(repo) {
        while (true) {
            try {
                if (!pending) items = repo.activeSessions()
                loaded = true
            } catch (e: Exception) {
                notice = "Ошибка чтения: " + (e.message ?: "неизвестна")
                loaded = true
            }
            delay(1000L)
        }
    }
    fun refreshAfter(action: suspend () -> SavedSession, isCreation: Boolean = false) {
        if (pending) return
        pending = true
        scope.launch {
            try {
                val changed = action()
                items = repo.activeSessions()
                selectedId = if (changed.finishedAtMs == null) changed.id else null
                preparing = false
                if (isCreation) creationRequestId = UUID.randomUUID().toString()
                notice = if (changed.finishedAtMs == null) "Сохранено в базе" else "Программа завершена и сохранена"
            } catch (e: Exception) {
                notice = "Ошибка: " + (e.message ?: "проверь действие")
            } finally { pending = false }
        }
    }
    fun showNew() {
        if (items.size >= SessionRepository.MAX_ACTIVE_SESSIONS) {
            notice = "Сначала заверши одну из пяти программ"
            return
        }
        selectedId = null
        creationRequestId = UUID.randomUUID().toString()
        preparing = true
        notice = null
    }
    BackHandler(enabled = preparing || selectedId != null) {
        selectedId = null
        preparing = false
    }
    Box(Modifier.fillMaxSize().background(C.canvas)) {
        when {
            !loaded -> Text("Восстанавливаем мою смену…", color=C.white)
            preparing -> B22PrepareScreen(
                notice=notice, pending=pending,
                onBack={ preparing=false },
                onStart={ minutes, suite, guests ->
                    val request = creationRequestId
                    refreshAfter(
                        action={ repo.start(
                            PilotTanysuPlan.toPlan(minutes),
                            SessionMetadata(suite=suite,guestCount=guests),
                            requestId=request
                        ) },
                        isCreation=true
                    )
                }
            )
            selectedId != null -> {
                val session = items.firstOrNull { it.id == selectedId }
                if (session == null) {
                    // A finished programme drops out of the active list; the shift is
                    // visible once the user returns, without resurrecting that record.
                    B23ShiftScreen(
                        sessions=items,pending=pending,notice=notice,
                        onBack=onBack,onNew=::showNew,onOpen={ selectedId=it }
                    )
                } else {
                    B22TimelineScreen(
                        saved=session,notice=notice,pending=pending,
                        backLabel="‹ К моей смене",
                        onBack={ selectedId=null },
                        onCommand={ stageId,kind ->
                            val id=session.id
                            refreshAfter{ repo.perform(id,stageId,kind) }
                        },
                        onFinish={
                            val id=session.id
                            refreshAfter{ repo.finish(id) }
                        }
                    )
                }
            }
            else -> B23ShiftScreen(
                sessions=items,pending=pending,notice=notice,
                onBack=onBack,onNew=::showNew,onOpen={ selectedId=it }
            )
        }
    }
}
