package com.novacamera

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.novacamera.presentation.navigation.NovaNavGraph
import com.novacamera.presentation.theme.NovaCameraTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NovaCameraTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NovaNavGraph()
                }
            }
        }
    }
}
