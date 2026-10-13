package kz.terensai.rhythm.feature.session

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kz.terensai.rhythm.ui.theme.ParitmTheme

class B22SessionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParitmTheme {
                B22SessionFlow(onBack = { finish() })
            }
        }
    }
}
