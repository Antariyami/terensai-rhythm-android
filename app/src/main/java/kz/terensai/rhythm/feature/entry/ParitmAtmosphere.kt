package kz.terensai.rhythm.feature.entry

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.min

/** Resolution-independent atmosphere. Actual text/buttons always remain Compose controls. */
@Composable
fun ParitmAtmosphere(modifier: Modifier = Modifier) {
  Canvas(modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    drawRect(brush = Brush.verticalGradient(listOf(
      Color(0xFF4A2B20), Color(0xFF1B1410), Color(0xFF100D0B)
    )))
    drawCircle(
      brush = Brush.radialGradient(
        listOf(Color(0x5CF2A047), Color(0x17BD7547), Color.Transparent),
        center = Offset(w * .79f, h * .14f), radius = w * .88f
      ), radius = w * .88f, center = Offset(w * .79f, h * .14f)
    )
    drawCircle(
      brush = Brush.radialGradient(
        listOf(Color(0x225D8F5A), Color(0x085A9A5B), Color.Transparent),
        center = Offset(w * .08f, h * .47f), radius = w * .78f
      ), radius = w * .78f, center = Offset(w * .08f, h * .47f)
    )
    for (i in 0 until 5) {
      val startX = w * (.20f + i * .14f)
      val startY = h * (.04f + i * .018f)
      val plume = Path().apply {
        moveTo(startX, startY + 95f)
        cubicTo(startX - 56f, startY + 63f, startX + 54f, startY + 37f, startX - 5f, startY)
      }
      drawPath(plume, Color(0x26F4D9B2), style = Stroke(width = min(w, h) * .005f))
    }
    drawLine(Color(0x3ACA9058), Offset(0f, h * .90f), Offset(w, h * .90f), 1.1f)
  }
}

@Composable
fun ParitmTileAtmosphere(modifier: Modifier = Modifier, highlighted: Boolean = false) {
  Canvas(modifier.fillMaxSize()) {
    val w = size.width
    val h = size.height
    drawRect(brush = Brush.verticalGradient(
      if (highlighted) listOf(Color(0xFF603E24), Color(0xFF30231B), Color(0xFF251C18))
      else listOf(Color(0xFF493023), Color(0xFF30231D), Color(0xFF261E1B))
    ))
    drawCircle(
      brush = Brush.radialGradient(
        listOf(Color(0x4DE7AC60), Color(0x0D9A6440), Color.Transparent),
        center = Offset(w * .80f, h * .17f), radius = w * .75f
      ), radius = w * .75f, center = Offset(w * .80f, h * .17f)
    )
    drawLine(Color(0x35E7B879), Offset(w * .62f, h * .47f), Offset(w * .92f, h * .35f), 2f)
    drawLine(Color(0x25E7B879), Offset(w * .56f, h * .53f), Offset(w * .95f, h * .37f), 1f)
  }
}
