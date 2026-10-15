package kz.terensai.rhythm.feature.checklists

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kz.terensai.rhythm.checklists.*
import kz.terensai.rhythm.data.room.ChecklistRunRow
import kz.terensai.rhythm.data.room.ParitmDatabase
import kz.terensai.rhythm.ui.theme.ParitmColors as C

/**
 * P2.2 offline checklist workflow. No dependency on SessionRepository/WorkRepository.
 * Settings edit the templates, while My Shift records per-run immutable answers.
 */
@Composable
fun P22ChecklistScreen(onBack:()->Unit,initialEditor:Boolean=false) {
    val context=LocalContext.current
    val repo=remember(context){ChecklistRepository(ParitmDatabase.get(context))}
    val scope=rememberCoroutineScope()
    var templates by remember{mutableStateOf<List<ChecklistTemplate>>(emptyList())}
    var selectedId by rememberSaveable{mutableStateOf<String?>(null)}
    var editing by rememberSaveable{mutableStateOf(initialEditor)}
    var editor by remember{mutableStateOf<ChecklistTemplate?>(null)}
    var run by remember{mutableStateOf<ChecklistRunRow?>(null)}
    var page by rememberSaveable{mutableIntStateOf(0)}
    var notice by remember{mutableStateOf<String?>(null)}
    var busy by remember{mutableStateOf(false)}
    var importPreview by remember{mutableStateOf<Pair<String,ChecklistTemplate>?>(null)}
    var exportedTemplateId by rememberSaveable{mutableStateOf<String?>(null)}
    var editDialog by remember{mutableStateOf<Pair<Int,Int?>?>(null)}
    var editTitle by remember{mutableStateOf("")}
    var editQty by remember{mutableStateOf("")}
    var editUnit by remember{mutableStateOf("")}
    var confirmDecision by remember{mutableStateOf<String?>(null)}
    var summary by remember{mutableStateOf("")}

    fun load() {
        scope.launch {
            try {
                repo.ensureDefaults()
                templates=repo.templates()
                if(selectedId!=null && editor==null && run==null && !editing) {
                    run=repo.activeRun(selectedId!!)
                }
            }catch(e:Exception){notice="Ошибка загрузки: ${e.message}"}
        }
    }
    LaunchedEffect(repo){load()}
    fun applyEdit(next:ChecklistTemplate) {
        if(busy)return
        busy=true
        scope.launch {
            try{
                val saved=repo.saveTemplate(next)
                editor=saved
                templates=repo.templates()
                notice="Шаблон сохранён · версия ${saved.version}"
            }catch(e:Exception){notice="Ошибка шаблона: ${e.message}"}
            finally{busy=false}
        }
    }
    fun start(id:String,forceNew:Boolean=false) {
        if(busy)return
        busy=true
        scope.launch {
            try {
                selectedId=id
                run=if(forceNew) repo.newRun(id) else
                    (repo.activeRun(id)?:repo.newRun(id))
                page=0
                notice=null
                summary=""
            }catch(e:Exception){notice="Ошибка открытия: ${e.message}"}
            finally{busy=false}
        }
    }
    fun toggle(itemId:String) {
        val id=run?.id?:return
        if(busy)return
        busy=true
        scope.launch {
            try {run=repo.toggle(id,itemId)}
            catch(e:Exception){notice="Ошибка пункта: ${e.message}"}
            finally{busy=false}
        }
    }
    fun end(decision:String) {
        val id=run?.id?:return
        if(busy)return
        busy=true
        scope.launch {
            try{
                run=repo.complete(id,decision,summary)
                notice="Итог сохранён в базе. Для следующей смены начни новую проверку."
            }catch(e:Exception){notice="Ошибка завершения: ${e.message}"}
            finally{busy=false;confirmDecision=null}
        }
    }
    fun exit() {
        when {
            editDialog!=null->editDialog=null
            editor!=null->editor=null
            run!=null->run=null
            else->onBack()
        }
    }
    BackHandler {exit()}

    val exporter=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ){uri:Uri?->
        val id=exportedTemplateId
        if(uri!=null && id!=null)scope.launch {
            try {
                val json=repo.export(id)
                withContext(Dispatchers.IO){
                    context.contentResolver.openOutputStream(uri)?.use{
                        it.write(json.toByteArray(Charsets.UTF_8))
                    }?:error("Не удалось создать файл")
                }
                notice="JSON шаблона экспортирован"
            }catch(e:Exception){notice="Ошибка экспорта: ${e.message}"}
        }
    }
    val importer=rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ){uri:Uri?->
        if(uri!=null)scope.launch {
            try {
                val json=withContext(Dispatchers.IO){
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use{
                        it.readText()
                    }?:error("Файл недоступен")
                }
                val parsed=ChecklistJson.decode(json)
                importPreview=json to parsed
            }catch(e:Exception){notice="Ошибка импорта: ${e.message}"}
        }
    }

    Column(Modifier.fillMaxSize().background(C.canvas)
        .verticalScroll(rememberScrollState()).padding(18.dp),
        verticalArrangement=Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick=::exit){Text("‹ Назад",color=C.amber)}
            Spacer(Modifier.weight(1f))
            Text("ПАРИТМ · ЧЕК-ЛИСТЫ",color=C.bronze,fontSize=12.sp)
        }
        if(notice!=null)Text(notice!!,
            color=if(notice!!.startsWith("Ошибка"))C.overrun else C.bronze,fontSize=13.sp)

        val edit=editor
        val active=run
        if(edit!=null){
            Text("Редактор шаблона",fontSize=27.sp,color=C.white,fontWeight=FontWeight.Bold)
            Text(edit.title+" · версия ${edit.version}",color=C.cream)
            Text("Изменения влияют на новые проверки. Начатые и завершённые сохраняют старую версию.",
                color=C.secondary,fontSize=13.sp)
            edit.blocks.forEachIndexed { index,block->
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(block.title,modifier=Modifier.weight(1f),color=C.cream,
                        fontWeight=FontWeight.SemiBold)
                    TextButton(enabled=!busy && index>0,onClick={
                        applyEdit(ChecklistEditing.moveBlock(edit,index,-1))
                    }){Text("↑",color=C.amber)}
                    TextButton(enabled=!busy && index<edit.blocks.lastIndex,onClick={
                        applyEdit(ChecklistEditing.moveBlock(edit,index,1))
                    }){Text("↓",color=C.amber)}
                    TextButton(onClick={
                        editDialog=index to null;editTitle=block.title;editQty="";editUnit=""
                    }){Text("✎",color=C.amber)}
                    TextButton(enabled=!busy && edit.blocks.size>1,onClick={
                        applyEdit(ChecklistEditing.removeBlock(edit,index))
                    }){Text("×",color=C.overrun)}
                }
                block.items.forEachIndexed{j,item->
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(item.title+quantityLabel(item),modifier=Modifier.weight(1f),
                            color=C.secondary,fontSize=13.sp)
                        TextButton(enabled=!busy && j>0,onClick={
                            applyEdit(ChecklistEditing.moveItem(edit,index,j,-1))
                        }){Text("↑",color=C.amber)}
                        TextButton(enabled=!busy && j<block.items.lastIndex,onClick={
                            applyEdit(ChecklistEditing.moveItem(edit,index,j,1))
                        }){Text("↓",color=C.amber)}
                        TextButton(onClick={
                            editDialog=index to j;editTitle=item.title
                            editQty=item.quantity?.toString()?:"";editUnit=item.unit?:""
                        }){Text("✎",color=C.amber)}
                        TextButton(enabled=!busy && edit.totalItems>1,onClick={
                            applyEdit(ChecklistEditing.removeItem(edit,index,j))
                        }){Text("×",color=C.overrun)}
                    }
                }
                OutlinedButton(enabled=!busy,onClick={
                    applyEdit(ChecklistEditing.addItem(edit,index))
                },modifier=Modifier.fillMaxWidth()){
                    Text("+ Пункт в этот блок",color=C.amber)
                }
            }
            OutlinedButton(enabled=!busy,onClick={
                applyEdit(ChecklistEditing.addBlock(edit))
            },modifier=Modifier.fillMaxWidth()) {Text("+ Добавить блок",color=C.amber)}
            OutlinedButton(onClick={
                exportedTemplateId=edit.id;exporter.launch("PARITM-${edit.type.lowercase()}-v${edit.version}.json")
            },modifier=Modifier.fillMaxWidth()){Text("Экспорт шаблона JSON",color=C.amber)}
        } else if(active!=null) {
            val snapshot=remember(active.snapshotJson){ChecklistJson.decode(active.snapshotJson)}
            val checked=remember(active.checkedJson){ChecklistJson.decodeAnswers(active.checkedJson)}
            val notes=remember(active.notesJson){ChecklistJson.decodeNotes(active.notesJson)}
            val block=snapshot.blocks[page.coerceIn(snapshot.blocks.indices)]
            val progress=checked.size
            Text(snapshot.title,color=C.white,fontWeight=FontWeight.Bold,fontSize=26.sp)
            Text("Проверено ${progress} из ${snapshot.totalItems} · Блок ${page+1} из ${snapshot.blocks.size}",
                color=C.amber)
            Text(if(active.status=="IN_PROGRESS")"Проверка продолжается" else
                "Завершено: ${statusLabel(active.status)}",color=C.secondary)
            Text(block.title,color=C.cream,fontSize=20.sp,fontWeight=FontWeight.SemiBold)
            block.items.forEach{item->
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=item.id in checked,
                        enabled=!busy && active.status=="IN_PROGRESS",
                        onCheckedChange={toggle(item.id)})
                    Text(item.title+quantityLabel(item),color=if(item.id in checked)C.muted else C.white,
                        fontSize=15.sp,modifier=Modifier.weight(1f))
                }
            }
            if(active.status=="IN_PROGRESS"){
                val key=if(snapshot.type=="RECEPTION" && block.id=="pm1-1")"important"
                    else if(snapshot.type=="RECEPTION" && block.id=="pm1-2")"remarks"
                    else block.id
                var note by remember(active.id,block.id){mutableStateOf(notes[key]?:"")}
                LaunchedEffect(note,active.id,block.id){
                    kotlinx.coroutines.delay(500L)
                    if(note!=(notes[key]?:"")) {
                        try {
                            val saved=repo.saveNote(active.id,key,note)
                            if(run?.id==active.id)run=saved
                        }catch(e:Exception){notice="Ошибка сохранения заметки: ${e.message}"}
                    }
                }
                OutlinedTextField(value=note,onValueChange={note=it.take(2000)},
                    label={Text(if(snapshot.type=="RECEPTION" && block.id=="pm1-1")
                        "Важно учесть" else if(snapshot.type=="RECEPTION" && block.id=="pm1-2")
                        "Замечания / тепловое ядро" else "Заметки по блоку")},
                    modifier=Modifier.fillMaxWidth(),minLines=2)
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                    OutlinedButton(onClick={page=(page-1).coerceAtLeast(0)},enabled=page>0){
                        Text("← Назад",color=C.amber)
                    }
                    OutlinedButton(onClick={page=(page+1).coerceAtMost(snapshot.blocks.lastIndex)},
                        enabled=page<snapshot.blocks.lastIndex){Text("Далее →",color=C.amber)}
                }
                if(page==snapshot.blocks.lastIndex){
                    Text("ИТОГ ПРОВЕРКИ",color=C.bronze,fontSize=17.sp)
                    OutlinedTextField(value=summary,onValueChange={summary=it.take(2000)},
                        label={Text("Замечания / кому передано")},
                        modifier=Modifier.fillMaxWidth(),minLines=2)
                    if(snapshot.type=="RECEPTION"){
                        OutlinedButton(onClick={confirmDecision="READY"},
                            modifier=Modifier.fillMaxWidth()){
                            Text("✓ Смена принята — готово",color=C.fact)
                        }
                        OutlinedButton(onClick={confirmDecision="NOTES"},
                            modifier=Modifier.fillMaxWidth()){
                            Text("Смена принята с замечаниями",color=C.amber)
                        }
                        OutlinedButton(onClick={confirmDecision="CRITICAL"},
                            modifier=Modifier.fillMaxWidth()){
                            Text("Критические замечания — устранить",color=C.overrun)
                        }
                    } else {
                        OutlinedButton(onClick={confirmDecision="DONE"},
                            modifier=Modifier.fillMaxWidth()){
                            Text("✓ Завершить проверку",color=C.fact)
                        }
                    }
                }
            } else {
                Text("История сохранена. Можно открыть новый экземпляр списка без изменения этого отчёта.",
                    color=C.secondary)
            }
            OutlinedButton(onClick={start(snapshot.id,true)},modifier=Modifier.fillMaxWidth()){
                Text("+ Новая проверка (галочки сбросятся)",color=C.amber)
            }
        } else {
            Text(if(editing)"Шаблоны чек-листов" else "Чек-листы моей смены",
                color=C.white,fontWeight=FontWeight.Bold,fontSize=26.sp)
            Text("Каждая проверка сохраняется в Room. Редактирование шаблона не изменяет уже пройденные проверки.",
                color=C.secondary,fontSize=13.sp)
            if(editing){
                OutlinedButton(onClick={importer.launch(arrayOf("application/json","text/plain"))},
                    modifier=Modifier.fillMaxWidth()){Text("Импортировать шаблон JSON",color=C.amber)}
            }
            templates.forEach { template->
                Column(Modifier.fillMaxWidth().background(C.panel).padding(14.dp),
                    verticalArrangement=Arrangement.spacedBy(5.dp)){
                    Text(template.title,color=C.cream,fontSize=19.sp,
                        fontWeight=FontWeight.SemiBold)
                    Text("${template.blocks.size} блоков · ${template.totalItems} пунктов · v${template.version}",
                        color=C.secondary,fontSize=12.sp)
                    OutlinedButton(onClick={
                        if(editing)editor=template else start(template.id)
                    },modifier=Modifier.fillMaxWidth()){
                        Text(if(editing)"Редактировать шаблон →" else "Пройти / продолжить →",
                            color=C.amber)
                    }
                    if(!editing){
                        OutlinedButton(onClick={start(template.id,true)},
                            modifier=Modifier.fillMaxWidth()){
                            Text("+ Начать новую проверку",color=C.bronze)
                        }
                    }
                }
            }
            if(!editing){
                OutlinedButton(onClick={editing=true},modifier=Modifier.fillMaxWidth()){
                    Text("⚙ Шаблоны и редактор",color=C.amber)
                }
            } else if(!initialEditor){
                OutlinedButton(onClick={editing=false},modifier=Modifier.fillMaxWidth()){
                    Text("← К прохождению",color=C.amber)
                }
            }
            Text("«Сдача смены» и «Подготовка программы»: ждём утверждённых перечней; пустые нормативы не создаются.",
                color=C.muted,fontSize=12.sp)
        }
        Spacer(Modifier.height(30.dp))
    }

    if(editDialog!=null && editor!=null){
        val (blockIndex,itemIndex)=editDialog!!
        AlertDialog(
            onDismissRequest={editDialog=null},
            title={Text(if(itemIndex==null)"Редактировать блок" else "Редактировать пункт")},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                    OutlinedTextField(value=editTitle,onValueChange={editTitle=it.take(250)},
                        label={Text("Название")},modifier=Modifier.fillMaxWidth())
                    if(itemIndex!=null){
                        OutlinedTextField(value=editQty,onValueChange={editQty=it.filter(Char::isDigit).take(5)},
                            label={Text("Количество (можно оставить пустым)")})
                        OutlinedTextField(value=editUnit,onValueChange={editUnit=it.take(30)},
                            label={Text("Единица")})
                    }
                }
            },
            confirmButton={TextButton(onClick={
                val template=editor!!
                val block=template.blocks[blockIndex]
                val updated=template.copy(blocks=template.blocks.toMutableList().also{list->
                    list[blockIndex]=if(itemIndex==null)block.copy(title=editTitle.trim())
                        else block.copy(items=block.items.toMutableList().also{items->
                            items[itemIndex]=items[itemIndex].copy(
                                title=editTitle.trim(),
                                quantity=editQty.toIntOrNull(),
                                unit=editUnit.trim().ifBlank{null})
                        })
                })
                editDialog=null;applyEdit(updated)
            }){Text("Сохранить",color=C.amber)}},
            dismissButton={TextButton(onClick={editDialog=null}){Text("Отмена")}}
        )
    }
    if(confirmDecision!=null){
        val decision=confirmDecision!!
        AlertDialog(onDismissRequest={confirmDecision=null},
            title={Text("Сохранить итог проверки?")},
            text={Text(statusLabel(decision)+". История и отмеченные пункты останутся сохранены.")},
            confirmButton={TextButton(onClick={end(decision)}){Text("Сохранить",color=C.amber)}},
            dismissButton={TextButton(onClick={confirmDecision=null}){Text("Отмена")}})
    }
    if(importPreview!=null){
        val (json,template)=importPreview!!
        AlertDialog(onDismissRequest={importPreview=null},
            title={Text("Импортировать шаблон?")},
            text={Text("${template.title}\n${template.blocks.size} блоков, ${template.totalItems} пунктов. " +
                "Будет создана копия — существующие шаблоны и проверки сохранятся.")},
            confirmButton={TextButton(onClick={
                importPreview=null
                scope.launch {
                    try{
                        val saved=repo.importAsNew(json)
                        templates=repo.templates()
                        notice="Импортирован шаблон «${saved.title}»"
                    }catch(e:Exception){notice="Ошибка импорта: ${e.message}"}
                }
            }){Text("Импортировать",color=C.amber)}},
            dismissButton={TextButton(onClick={importPreview=null}){Text("Отмена")}})
    }
}
private fun quantityLabel(item:ChecklistItem):String =
    item.quantity?.let{" — $it ${item.unit?:"шт."}"}?:""

private fun statusLabel(status:String):String=when(status){
    "READY"->"Смена принята — пространство готово"
    "NOTES"->"Смена принята с замечаниями"
    "CRITICAL"->"Критические замечания — требуется устранение"
    "DONE"->"Проверка завершена"
    else->"В процессе"
}
