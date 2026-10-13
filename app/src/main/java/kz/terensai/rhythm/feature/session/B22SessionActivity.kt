package kz.terensai.rhythm.feature.session

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import kz.terensai.rhythm.ui.theme.ParitmTheme
import kz.terensai.rhythm.BuildConfig

class B22SessionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ParitmTheme {
                if (BuildConfig.B23_QA || BuildConfig.B241_QA || BuildConfig.B242_QA) {
                    B23ShiftFlow(
                        onBack = { finish() },
                        initialNew = intent?.getBooleanExtra("prepare", false) == true,
                        initialJournal = intent?.getBooleanExtra("openJournal",false)==true,
                        initialMasters = intent?.getBooleanExtra("openMasters",false)==true
                    )
                } else {
                    B22SessionFlow(onBack = { finish() })
                }
            }
        }
    }
}
