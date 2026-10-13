package kz.terensai.rhythm.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Matches docs/design-system/tokens.json. Keep B1-specific decoration out of business logic. */
object ParitmColors {
  val canvas = Color(0xFF15110F)
  val panel = Color(0xFF281E1A)
  val raised = Color(0xFF352720)
  val ink = Color(0xFF100D0B)
  val border = Color(0xFF594535)
  val amber = Color(0xFFF2A047)
  val bronze = Color(0xFFC69B66)
  val cream = Color(0xFFEACB92)
  val white = Color(0xFFF6F1E8)
  val secondary = Color(0xFFCFC3B6)
  val muted = Color(0xFFAFA399)
  val fact = Color(0xFF45D6B1)
  val overrun = Color(0xFFF58A35)
  val success = Color(0xFF95AC8C)
}
private val colors = darkColorScheme(
  background = ParitmColors.canvas,
  onBackground = ParitmColors.white,
  surface = ParitmColors.panel,
  onSurface = ParitmColors.white,
  primary = ParitmColors.amber,
  onPrimary = ParitmColors.canvas,
  secondary = ParitmColors.bronze,
  onSecondary = ParitmColors.canvas
)
private val type = Typography(
  headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight=FontWeight.Bold, fontSize=28.sp),
  titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight=FontWeight.SemiBold,fontSize=22.sp),
  bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif,fontSize=16.sp),
  bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif,fontSize=14.sp),
  labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif,fontWeight=FontWeight.Medium,fontSize=14.sp)
)

@Composable
fun ParitmTheme(content:@Composable ()->Unit) {
  MaterialTheme(colorScheme=colors, typography=type, content=content)
}

@Composable
fun ParitmPrimaryButton(
  label:String,
  onClick:()->Unit,
  enabled:Boolean=true,
  modifier:Modifier=Modifier
) {
  Button(onClick=onClick,enabled=enabled,modifier=modifier.defaultMinSize(minHeight=64.dp),
    shape=RoundedCornerShape(20.dp),
    contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp),
    colors=ButtonDefaults.buttonColors(
      containerColor=ParitmColors.amber,contentColor=ParitmColors.canvas,
      disabledContainerColor=ParitmColors.raised,disabledContentColor=ParitmColors.muted
    )) { Text(label,fontWeight=FontWeight.Bold,fontSize=16.sp) }
}
