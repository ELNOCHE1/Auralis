package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.example.ui.screens.MainDashboard
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MusicPlayerViewModel

class MainActivity : ComponentActivity() {
    private val musicViewModel: MusicPlayerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val isDarkThemeOverride by musicViewModel.isDarkTheme.collectAsState()
            val useDarkTheme = isDarkThemeOverride ?: isSystemInDarkTheme()

            MyApplicationTheme(darkTheme = useDarkTheme) {
                var showSplash by remember { mutableStateOf(true) }
                if (showSplash) {
                    SplashScreen(
                        onFinished = { showSplash = false },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    MainDashboard(
                        viewModel = musicViewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
