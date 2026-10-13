package kz.terensai.rhythm.feature.entry

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kz.terensai.rhythm.BuildConfig
import kz.terensai.rhythm.feature.qa.B21QaActivity
import kz.terensai.rhythm.MainActivity
import kz.terensai.rhythm.ui.theme.ParitmTheme

/** B1 UI shell. B2 will replace the old Activity with the single Room-backed session route. */
class EntryActivity: ComponentActivity() {
  private var oldSessionActive by mutableStateOf(false)
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    window.statusBarColor = android.graphics.Color.rgb(21,17,15)
    window.navigationBarColor = android.graphics.Color.rgb(21,17,15)
    setContent {
      ParitmTheme {
        ParitmB1Root(
          hasOldActiveSession = oldSessionActive,
          openPilotTimer = { startActivity(Intent(this,MainActivity::class.java)) },
          showQa = BuildConfig.B21_QA,
          openQa = { startActivity(Intent(this,B21QaActivity::class.java)) }
        )
      }
    }
  }
  override fun onResume() {
    super.onResume()
    oldSessionActive = getSharedPreferences("rhythm_state_v1",MODE_PRIVATE)
      .getLong("program",0L)>0L
  }
}
