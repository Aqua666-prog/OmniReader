package app.omnireader.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.omnireader.android.ui.OmniReaderApp
import app.omnireader.android.ui.theme.OmniReaderTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { OmniReaderTheme { OmniReaderApp() } }
    }
}
