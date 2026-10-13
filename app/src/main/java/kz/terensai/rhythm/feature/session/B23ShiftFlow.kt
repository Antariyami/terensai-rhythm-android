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
import kz.terensai.rhythm.data.StaffRepository
import kz.terensai.rhythm.data.WorkRepository
import kz.terensai.rhythm.data.WorkView
import kz.terensai.rhythm.data.room.StaffRow
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
    val staffRepo=remember(context) { StaffRepository(ParitmDatabase.get(context),LocalInstallIdentity.get(context)) }
    val workRepo=remember(context) { WorkRepository(ParitmDatabase.get(context),LocalInstallIdentity.get(context)) }
    val scope = rememberCoroutineScope()
    var showingWork by rememberSaveable { mutableStateOf(false) }
    var personalWorkId by rememberSaveable {mutableStateOf<String?>(null)}
    var personalWork by remember {mutableStateOf<WorkView?>(null)}

    var masters by remember { mutableStateOf<List<StaffRow>>(emptyList()) }
    var leaders by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var showingMasters by rememberSaveable {mutableStateOf(false)}
    var selectedPm1 by rememberSaveable {mutableStateOf<String?>(null)}

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
                if (!pending) {
                    items = repo.activeSessions()
                    masters = staffRepo.all()
                    personalWorkId?.let { personalWork=workRepo.get(it) }
                    leaders = items.associate { it.id to (staffRepo.leader(it.id)?.displayName ?: "не назначен") }
                }
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
                if (isCreation) {
                    creationRequestId = UUID.randomUUID().toString()
                    selectedPm1=null
                }
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
        selectedPm1=masters.firstOrNull{it.isMyProfile}?.id
        notice = null
    }
    BackHandler(enabled = preparing || selectedId != null || showingMasters || showingWork) {
        showingWork=false
        showingMasters=false
        selectedId = null
        preparing = false
    }
    Box(Modifier.fillMaxSize().background(C.canvas)) {
        when {
            !loaded -> Text("Восстанавливаем мою смену…", color=C.white)
            showingWork -> B242WorkScreen(
                sessions=items,work=personalWork,pending=pending,notice=notice,masters=masters,
                onBack={showingWork=false},
                onCreate={session,role,procedure,minutes,leader,suiteName,programmeName->
                    val myId=masters.firstOrNull{it.isMyProfile}?.id
                    if(myId==null) notice="Сначала выбери свой профиль в разделе «Мастера»"
                    else scope.launch {
                        pending=true
                        try {
                            val created=workRepo.create(myId,session,role=role,procedure=procedure,plannedMinutes=minutes,
                                externalPm1Id=leader,externalSuite=suiteName,externalProgramme=programmeName)
                            personalWorkId=created.row.id; personalWork=created;notice="Процедура сохранена"
                        }catch(e:Exception){notice="Ошибка: "+e.message}
                        finally{pending=false}
                    }
                },
                onNew={personalWorkId=null;personalWork=null},
                onAction={kind->
                    val id=personalWorkId
                    if(id!=null)scope.launch{
                        pending=true
                        try{personalWork=workRepo.command(id,kind);notice="Действие сохранено"}
                        catch(e:Exception){notice="Ошибка: "+e.message}
                        finally{pending=false}
                    }
                }
            )
            showingMasters -> B241MastersScreen(
                members=masters,notice=notice,busy=pending,onBack={showingMasters=false},
                onCreate={name,spec,mine ->
                    scope.launch {
                        pending=true
                        try { staffRepo.create(name,spec,mine); masters=staffRepo.all();notice="Мастер сохранён" }
                        catch(e:Exception){notice=e.message}
                        finally {pending=false}
                    }
                },
                onUpdate={id,name,spec ->
                    scope.launch {
                        pending=true
                        try {staffRepo.edit(id,name,spec);masters=staffRepo.all();notice="Изменения сохранены"}
                        catch(e:Exception){notice=e.message}
                        finally {pending=false}
                    }
                },
                onSelectMine={id ->
                    scope.launch {
                        pending=true
                        try{staffRepo.chooseMine(id);masters=staffRepo.all();notice="Профиль выбран"}
                        catch(e:Exception){notice=e.message}
                        finally{pending=false}
                    }
                },
                onArchive={id ->
                    scope.launch {
                        pending=true
                        try{staffRepo.archive(id);masters=staffRepo.all();notice="Мастер скрыт"}
                        catch(e:Exception){notice=e.message}
                        finally{pending=false}
                    }
                }
            )
            preparing -> B22PrepareScreen(
                notice=notice, pending=pending,
                staff=masters,selectedPm1=selectedPm1,onPm1Selected={selectedPm1=it},
                onBack={ preparing=false },
                onStart={ minutes, suite, guests ->
                    val request = creationRequestId
                    val pm1=selectedPm1
                    refreshAfter(
                        action={
                            val saved=repo.start(
                                PilotTanysuPlan.toPlan(minutes),
                                SessionMetadata(suite=suite,guestCount=guests),
                                requestId=request
                            )
                            if(pm1!=null) staffRepo.appointPm1(saved.id,pm1)
                            saved
                        },
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
                        onBack=onBack,onNew=::showNew,onOpen={ selectedId=it },
                        onMasters={showingMasters=true},onWork={showingWork=true},leaders=leaders
                    )
                } else {
                    B22TimelineScreen(
                        saved=session,notice=notice,pending=pending,
                        backLabel="‹ К моей смене",
                        onBack={ selectedId=null },
                        onCommand={ stageId,kind ->
                            val id=session.id
                            refreshAfter(action={ repo.perform(id,stageId,kind) })
                        },
                        onFinish={
                            val id=session.id
                            refreshAfter(action={ repo.finish(id) })
                        }
                    )
                }
            }
            else -> B23ShiftScreen(
                sessions=items,pending=pending,notice=notice,
                onBack=onBack,onNew=::showNew,onOpen={ selectedId=it },
                        onMasters={showingMasters=true},onWork={showingWork=true},leaders=leaders
            )
        }
    }
}
