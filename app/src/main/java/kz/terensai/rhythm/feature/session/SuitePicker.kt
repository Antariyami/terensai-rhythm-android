package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kz.terensai.rhythm.data.settings.SuiteNames
import kz.terensai.rhythm.data.settings.SuiteSettingsStore
import kz.terensai.rhythm.ui.theme.ParitmColors as C

/**
 * A lightweight entry to personal suite settings, usable before full Settings (D).
 * List is stored locally and snapshotted by NAME when a session is started.
 */
@Composable
fun SuitePicker(selected: String, onSelected: (String) -> Unit) {
    val context = LocalContext.current
    val store = remember(context) { SuiteSettingsStore(context) }
    var suites by remember { mutableStateOf(store.load()) }
    var expanded by remember { mutableStateOf(false) }
    var managerOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var alert by remember { mutableStateOf<String?>(null) }

    fun refresh(updated: List<String>) {
        suites = updated
        if (selected.isNotBlank() && selected !in suites) onSelected("")
    }
    Text("Место работы / сьют",color=C.secondary)
    Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Box(Modifier.weight(1f)) {
            OutlinedButton(
                onClick={expanded=true},
                modifier=Modifier.fillMaxWidth().heightIn(min=54.dp)
            ) {
                Text((selected.ifBlank { "Выбрать сьют" }) + "  ▾", color=C.white)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
                DropdownMenuItem(text={Text("Не указывать")},onClick={
                    onSelected("");expanded=false
                })
                suites.forEach { name ->
                    DropdownMenuItem(text={Text(name)},onClick={
                        onSelected(name); expanded=false
                    })
                }
            }
        }
        OutlinedButton(
            onClick={managerOpen=true},
            modifier=Modifier.heightIn(min=54.dp)
        ) { Text("Изменить",color=C.amber) }
    }
    if (suites.isEmpty()) Text(
        "Список пуст. Нажми «Изменить», чтобы добавить своё место работы.",
        color=C.muted
    )

    if (managerOpen) AlertDialog(
        onDismissRequest={managerOpen=false},
        containerColor=C.panel,
        title={Text("Мои сьюты",color=C.cream)},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
                Text("Список хранится только на этом устройстве.",color=C.secondary)
                Column(Modifier.heightIn(max=150.dp).verticalScroll(rememberScrollState())) {
                    suites.forEach { name ->
                        TextButton(onClick={
                            editing=name; input=name; alert=null
                        },modifier=Modifier.fillMaxWidth()) {
                            Text(if(name==editing) "✎ $name" else name,color=if(name==editing) C.amber else C.white)
                        }
                    }
                }
                OutlinedTextField(value=input,onValueChange={ input=it.take(60);alert=null },
                    singleLine=true,label={Text(if(editing==null) "Новый сьют" else "Переименовать")})
                if(alert!=null) Text(alert!!,color=C.overrun)
                Row(horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    TextButton(onClick={
                        val cleaned=SuiteNames.normalized(input)
                        val updated=if(editing==null) SuiteNames.add(suites,input)
                            else SuiteNames.rename(suites,editing!!,input)
                        if(cleaned.isBlank() || (updated==suites && cleaned!=editing)) {
                            alert="Введите уникальное название (до 48 символов)"
                        } else {
                            try {
                                val old=editing
                                refresh(if(old==null) store.add(input) else store.rename(old,input))
                                if(old==selected) onSelected(cleaned)
                                input=""; editing=null; alert=null
                            } catch (e:Exception) { alert=e.message ?: "Ошибка сохранения" }
                        }
                    }) { Text(if(editing==null) "Добавить" else "Сохранить",color=C.amber) }
                    if(editing!=null) TextButton(onClick={
                        try {
                            refresh(store.remove(editing!!))
                            editing=null; input="";alert=null
                        } catch(e:Exception){alert=e.message ?: "Ошибка удаления"}
                    }) { Text("Удалить",color=C.overrun) }
                }
                if(editing!=null) TextButton(onClick={editing=null;input="";alert=null}){
                    Text("Добавить другой",color=C.secondary)
                }
            }
        },
        confirmButton={
            TextButton(onClick={managerOpen=false}) { Text("Готово",color=C.amber) }
        }
    )
}
