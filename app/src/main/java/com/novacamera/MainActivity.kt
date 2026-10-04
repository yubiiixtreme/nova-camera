package com.novacamera

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.novacamera.presentation.navigation.NovaNavGraph
import com.novacamera.presentation.theme.NovaCameraTheme
import com.novacamera.util.ShutterEvents
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            NovaCameraTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Black) {
                    NovaNavGraph()
                }
            }
        }
    }

    /** Volume-down acts as the shutter release (volume-up keeps system behavior). */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN && ShutterEvents.isListening) {
            if (event?.repeatCount == 0) ShutterEvents.press() // ignore key auto-repeat
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
