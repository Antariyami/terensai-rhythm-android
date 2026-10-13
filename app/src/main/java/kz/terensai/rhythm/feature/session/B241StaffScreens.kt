package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import kz.terensai.rhythm.data.room.StaffRow
import kz.terensai.rhythm.ui.theme.ParitmColors as C

/** S10: no pre-seeded employer or invented staff identity. */
@Composable
fun B241MastersScreen(
    members:List<StaffRow>,notice:String?,busy:Boolean,
    onBack:()->Unit,onCreate:(String,String,Boolean)->Unit,
    onUpdate:(String,String,String)->Unit,onSelectMine:(String)->Unit,
    onArchive:(String)->Unit
){
    var editing by remember { mutableStateOf<StaffRow?>(null) }
    var name by remember { mutableStateOf("") }
    var special by remember { mutableStateOf("") }
    var mine by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(C.canvas).verticalScroll(rememberScrollState())
        .padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        TextButton(onClick=onBack){Text("‹ Моя смена",color=C.amber)}
        Text("ПАРИТМ  ·  НАСТРОЙКИ",color=C.bronze,fontSize=12.sp)
        Text("Мастера",fontSize=32.sp,color=C.white,fontWeight=FontWeight.Bold)
        Text("Личный профиль и справочник для выбора ведущего в программах.",
            color=C.secondary)
        val self=members.firstOrNull{it.isMyProfile}
        Text("Мой профиль: ${self?.displayName ?: "не выбран"}",color=C.cream)
        if(notice!=null) Text(notice,color=C.amber)
        members.forEach { person ->
            Column(Modifier.fillMaxWidth().background(C.panel,RoundedCornerShape(14.dp))
                .border(1.dp,C.border,RoundedCornerShape(14.dp)).padding(12.dp),
                verticalArrangement=Arrangement.spacedBy(5.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){
                    Text(person.displayName,Modifier.weight(1f),color=C.white)
                    if(person.isMyProfile) Text("МОЙ ПРОФИЛЬ",color=C.fact,fontSize=10.sp)
                }
                if(person.specialties.isNotBlank()) Text(person.specialties,color=C.secondary,fontSize=12.sp)
                Row {
                    TextButton(enabled=!busy,onClick={onSelectMine(person.id)}){Text("Это я",color=C.amber)}
                    TextButton(enabled=!busy,onClick={
                        editing=person;name=person.displayName;special=person.specialties;mine=person.isMyProfile
                    }){Text("Изменить",color=C.cream)}
                    TextButton(enabled=!busy,onClick={onArchive(person.id)}){Text("Скрыть",color=C.overrun)}
                }
            }
        }
        HorizontalDivider(color=C.border)
        Text(if(editing==null) "Добавить мастера" else "Редактировать мастера",color=C.cream)
        OutlinedTextField(value=name,onValueChange={name=it.take(80)},label={Text("Имя мастера")},
            modifier=Modifier.fillMaxWidth(),singleLine=true)
        OutlinedTextField(value=special,onValueChange={special=it.take(80)},
            label={Text("Специализация (необязательно)")},modifier=Modifier.fillMaxWidth())
        if(editing==null) Row(verticalAlignment=Alignment.CenterVertically){
            Checkbox(checked=mine,onCheckedChange={mine=it})
            Text("Это мой профиль",color=C.white)
        }
        Button(enabled=!busy && name.isNotBlank(),onClick={
            val current=editing
            if(current==null) onCreate(name,special,mine)
            else onUpdate(current.id,name,special)
            name="";special="";editing=null;mine=false
        },modifier=Modifier.fillMaxWidth()){
            Text(if(editing==null) "+ Добавить мастера" else "Сохранить изменения")
        }
        if(editing!=null) TextButton(onClick={editing=null;name="";special=""}){
            Text("Отмена редактирования",color=C.secondary)
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** S11 selection used in programme preparation; there is always at most one PM1. */
@Composable
fun B241Pm1Picker(members:List<StaffRow>,selectedId:String?,onSelected:(String?)->Unit){
    var expanded by remember {mutableStateOf(false)}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Ведущий программы · PM1",color=C.cream,fontWeight=FontWeight.SemiBold)
        Box {
            OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                Text((members.firstOrNull{it.id==selectedId}?.displayName ?: "Выбрать PM1")+" ▾",color=C.white)
            }
            DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}){
                DropdownMenuItem(text={Text("Не назначать пока")},onClick={onSelected(null);expanded=false})
                members.forEach { person ->
                    DropdownMenuItem(text={Text(person.displayName)},onClick={
                        onSelected(person.id);expanded=false
                    })
                }
            }
        }
        if(members.isEmpty()) Text("Добавь мастеров через «Моя смена» → «Мастера».",
            color=C.secondary,fontSize=12.sp)
        Text("PM1 отвечает за ведение программы. Изменение ведущего сохраняется в истории.",
            color=C.secondary,fontSize=12.sp)
    }
}
