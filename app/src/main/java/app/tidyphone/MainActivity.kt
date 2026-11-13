package app.tidyphone

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.tidyphone.ui.StoragePermission
import app.tidyphone.ui.TidyAppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    private var granted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        granted = StoragePermission.isGranted(this)
        setContent { TidyAppRoot(permissionGranted = granted) }
    }

    override fun onResume() {
        super.onResume()
        // The user may grant or revoke "All files access" in system settings at any time.
        granted = StoragePermission.isGranted(this)
    }
}
