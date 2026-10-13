package kz.terensai.rhythm.feature.session

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.PaddingValues
import kz.terensai.rhythm.domain.QuickMinuteRules
import kz.terensai.rhythm.ui.theme.ParitmColors as C

/** All buttons keep >=48dp touch targets. No mutation of the active plan here. */
@Composable
fun MinuteStepper(
    values:List<String>,
    index:Int,
    onUpdate:(List<String>)->Unit
) {
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        listOf(-5,-1).forEach { delta->
            val next=QuickMinuteRules.adjust(values,index,delta)
            OutlinedButton(onClick={if(next!=null) ({onUpdate(next)}) else ({})},
                enabled=next!=null,modifier=Modifier.size(width=47.dp,height=52.dp),
                contentPadding=PaddingValues(0.dp),shape=RoundedCornerShape(12.dp)) {
                Text(delta.toString(),fontSize=15.sp,color=if(next!=null) C.amber else C.muted)
            }
        }
        OutlinedTextField(
            value=values[index],
            onValueChange={v->
                onUpdate(values.toMutableList().also {
                    it[index]=v.filter(Char::isDigit).take(3)
                })
            },
            modifier=Modifier.weight(1f).height(56.dp),
            singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),
            isError=values[index].toIntOrNull()?.let{it<=0} ?: true,
            textStyle=androidx.compose.ui.text.TextStyle(
                color=C.white,fontSize=19.sp,fontWeight=FontWeight.SemiBold
            )
        )
        listOf(1,5).forEach { delta->
            val next=QuickMinuteRules.adjust(values,index,delta)
            OutlinedButton(onClick={if(next!=null) ({onUpdate(next)}) else ({})},
                enabled=next!=null,modifier=Modifier.size(width=47.dp,height=52.dp),
                contentPadding=PaddingValues(0.dp),shape=RoundedCornerShape(12.dp)) {
                Text("+$delta",fontSize=15.sp,color=if(next!=null) C.amber else C.muted)
            }
        }
    }
}
