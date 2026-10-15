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
import kz.terensai.rhythm.data.WorkJournalRepository
import kz.terensai.rhythm.data.JournalLine
import kz.terensai.rhythm.data.room.StaffRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.data.room.SessionMetadata
import kz.terensai.rhythm.domain.EventKind
import kz.terensai.rhythm.domain.PilotTanysuPlan
import kz.terensai.rhythm.domain.ProgramPlan
import kz.terensai.rhythm.ui.theme.ParitmColors as C
import java.util.UUID

/**
 * A single selected programme is on-screen, but the projection of every other
 * Room-backed session continues to advance from its own timestamps.
 */
@Composable
fun B23ShiftFlow(onBack: () -> Unit, initialNew: Boolean = false,
    initialCustom:Boolean=false,
    initialJournal: Boolean=false, initialMasters: Boolean=false) {
    val context = LocalContext.current
    val repo = remember(context) {
        SessionRepository(
            database = ParitmDatabase.get(context),
            originClientId = LocalInstallIdentity.get(context)
        )
    }
    val staffRepo=remember(context) { StaffRepository(ParitmDatabase.get(context),LocalInstallIdentity.get(context)) }
    val workRepo=remember(context) { WorkRepository(ParitmDatabase.get(context),LocalInstallIdentity.get(context)) }
    val journalRepo=remember(context){WorkJournalRepository(
        ParitmDatabase.get(context),LocalInstallIdentity.get(context))}
    val scope = rememberCoroutineScope()
    var showingJournal by rememberSaveable{mutableStateOf(initialJournal)}
    var journalLines by remember{mutableStateOf<List<JournalLine>>(emptyList())}
    var programmeHistory by remember{mutableStateOf<List<SavedSession>>(emptyList())}
    var showingWork by rememberSaveable { mutableStateOf(false) }
    var personalWorkId by rememberSaveable {mutableStateOf<String?>(null)}
    var personalWork by remember {mutableStateOf<WorkView?>(null)}
    var creatingNewWork by rememberSaveable {mutableStateOf(false)}
    var myWorks by remember { mutableStateOf<List<WorkView>>(emptyList()) }

    var masters by remember { mutableStateOf<List<StaffRow>>(emptyList()) }
    var leaders by remember { mutableStateOf<Map<String,String>>(emptyMap()) }
    var showingMasters by rememberSaveable {mutableStateOf(initialMasters)}
    var selectedPm1 by rememberSaveable {mutableStateOf<String?>(null)}

    var items by remember { mutableStateOf<List<SavedSession>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var preparing by rememberSaveable { mutableStateOf(initialNew) }
    var preparingCustom by rememberSaveable {mutableStateOf(initialCustom)}
    // Saved across configuration changes; same user Start request has same UUID.
    var creationRequestId by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }

    LaunchedEffect(repo) {
        try {
            staffRepo.ensureStarterMasters()
            staffRepo.mine()?.id?.let { workRepo.reconcileLegacyRunning(it) }
        }catch(e:Exception){ notice="Ошибка восстановления профиля: "+(e.message?:"неизвестна") }
        while (true) {
            try {
                if (!pending) {
                    items = repo.activeSessions()
                    masters = staffRepo.all()
                    val mine=masters.firstOrNull { it.isMyProfile }?.id
                    myWorks=if(mine!=null) workRepo.all(mine) else emptyList()
                    if(showingJournal){
                        journalLines=if(mine!=null)journalRepo.all(mine) else emptyList()
                        programmeHistory=repo.allSessions()
                    }
                    if(personalWorkId==null && !creatingNewWork) {
                        personalWorkId=myWorks.firstOrNull { it.state!="COMPLETED" }?.row?.id
                    }
                    personalWork=personalWorkId?.let { id -> myWorks.firstOrNull { it.row.id==id } }
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
    fun refreshAfter(action: suspend () -> SavedSession, isCreation: Boolean = false, stageAction:Boolean=false) {
        if (pending) return
        pending = true
        scope.launch {
            try {
                val changed = action()
                items = repo.activeSessions()
                selectedId = if (changed.finishedAtMs == null) changed.id else null
                preparing = false
                preparingCustom = false
                if (isCreation) {
                    creationRequestId = UUID.randomUUID().toString()
                    selectedPm1=null
                }
                notice = if(stageAction) "Этап сохранён в программе. Учёт личного парения — в Журнале работы."
                    else if(changed.finishedAtMs==null) "Программа сохранена в базе"
                    else "Программа завершена и сохранена"
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
        preparingCustom=false
        selectedPm1=masters.firstOrNull{it.isMyProfile}?.id
        notice = null
    }
    BackHandler(enabled = preparing || preparingCustom || selectedId != null || showingMasters || showingWork || showingJournal) {
        if(showingJournal && initialJournal) onBack()
        showingJournal=false
        showingWork=false
        if(showingMasters && initialMasters) onBack()
        showingMasters=false
        selectedId = null
        preparing = false
        preparingCustom = false
    }
    Box(Modifier.fillMaxSize().background(C.canvas)) {
        when {
            !loaded -> Text("Восстанавливаем мою смену…", color=C.white)
            showingJournal -> B243JournalScreen(
                masterName=masters.firstOrNull{it.isMyProfile}?.displayName?:"Мастер",
                entries=journalLines,pending=pending,notice=notice,
                programmeSessions=programmeHistory,
                onAttributeStage={sessionId,stageId->
                    val mine=masters.firstOrNull{it.isMyProfile}?.id
                    if(mine==null)notice="Выбери свой профиль в разделе «Мастера»"
                    else if(!pending)scope.launch{
                        pending=true
                        try {
                            val result=workRepo.attributeCompletedStage(mine,sessionId,stageId)
                            journalLines=journalRepo.all(mine)
                            myWorks=workRepo.all(mine)
                            notice="Парение учтено: ${result.row.procedureType}. Отчёты обновлены."
                        }catch(e:Exception){notice="Ошибка учёта: "+e.message}
                        finally{pending=false}
                    }
                },
                onBack={if(initialJournal) onBack() else showingJournal=false},
                onOpenWork={id->
                    personalWorkId=id
                    personalWork=myWorks.firstOrNull{it.row.id==id}
                    creatingNewWork=false
                    showingJournal=false;showingWork=true
                },
                onCorrect={id,minutes,reason->
                    val mine=masters.firstOrNull{it.isMyProfile}?.id
                    if(mine==null)notice="Выбери мой профиль"
                    else scope.launch {
                        pending=true
                        try {
                            journalRepo.correctCompleted(id,mine,minutes,reason)
                            journalLines=journalRepo.all(mine)
                            notice="Исправление и причина записаны в историю"
                        }catch(e:Exception){notice="Ошибка: "+e.message}
                        finally{pending=false}
                    }
                }
            )
            showingWork -> B242WorkScreen(
                sessions=items,work=personalWork,pending=pending,notice=notice,masters=masters,
                allWorks=myWorks,onOpenWork={id-> creatingNewWork=false;personalWorkId=id; personalWork=myWorks.firstOrNull{it.row.id==id} },
                onBack={showingWork=false},
                onJournal={showingWork=false;showingJournal=true},
                onCreate={session,role,procedure,minutes,leader,suiteName,programmeName->
                    val myId=masters.firstOrNull{it.isMyProfile}?.id
                    if(myId==null) notice="Сначала выбери свой профиль в разделе «Мастера»"
                    else scope.launch {
                        pending=true
                        try {
                            val created=workRepo.create(myId,session,role=role,procedure=procedure,plannedMinutes=minutes,
                                externalPm1Id=leader,externalSuite=suiteName,externalProgramme=programmeName)
                            personalWorkId=created.row.id;personalWork=created;creatingNewWork=false;notice="Процедура сохранена"
                        }catch(e:Exception){notice="Ошибка: "+e.message}
                        finally{pending=false}
                    }
                },
                onNew={creatingNewWork=true;personalWorkId=null;personalWork=null},
                onAction={kind->
                    val id=personalWorkId
                    if(id!=null)scope.launch{
                        pending=true
                        try{
                            val changed=workRepo.command(id,kind)
                            if(changed.state=="COMPLETED"){
                                personalWorkId=null
                                personalWork=null
                                creatingNewWork=true
                                notice="Процедура завершена и находится в «Завершённых»"
                            }else{
                                personalWork=changed
                                notice=if(kind=="START"||kind=="RESUME")
                                    "Таймер запущен. Другие личные процедуры поставлены на паузу."
                                    else "Действие сохранено"
                            }
                            masters.firstOrNull{it.isMyProfile}?.id?.let{myWorks=workRepo.all(it)}
                        }
                        catch(e:Exception){notice="Ошибка: "+e.message}
                        finally{pending=false}
                    }
                }
            )
            showingMasters -> B241MastersScreen(
                members=masters,notice=notice,busy=pending,onBack={if(initialMasters) onBack() else showingMasters=false},
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
            preparingCustom -> P10CustomProgrammeScreen(
                staff=masters,selectedPm1=selectedPm1,notice=notice,pending=pending,
                onPm1Selected={selectedPm1=it},
                onBack={preparingCustom=false},
                onCreate={plan,suite,guests->
                    val request=creationRequestId
                    val pm1=selectedPm1
                    refreshAfter(
                        action={
                            val saved=repo.start(
                                plan,
                                SessionMetadata(suite=suite,guestCount=guests),
                                requestId=request
                            )
                            if(pm1!=null)staffRepo.appointPm1(saved.id,pm1)
                            saved
                        },isCreation=true
                    )
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
                        onMasters={showingMasters=true},onWork={showingWork=true},onJournal={showingJournal=true},leaders=leaders,
                        workItems=myWorks,onOpenWork={id->creatingNewWork=false;personalWorkId=id;personalWork=myWorks.firstOrNull{it.row.id==id};showingWork=true}
                    )
                } else {
                    B22TimelineScreen(
                        saved=session,notice=notice,pending=pending,
                        backLabel="‹ К моей смене",
                        onBack={ selectedId=null },
                        onCommand={ stageId,kind ->
                            val id=session.id
                            refreshAfter(action={ repo.perform(id,stageId,kind) },stageAction=true)
                        },
                        onReopen={ stageId ->
                            val id=session.id
                            refreshAfter(action={ repo.reopenPrevious(id,stageId) },stageAction=true)
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
                onMasters={showingMasters=true},
                onWork={showingWork=true},
                onJournal={showingJournal=true},
                leaders=leaders,
                workItems=myWorks,
                onOpenWork={id->
                    creatingNewWork=false
                    personalWorkId=id
                    personalWork=myWorks.firstOrNull{it.row.id==id}
                    showingWork=true
                }
            )
        }
    }
}
