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
import androidx.compose.ui.Modifier
import com.example.ui.screens.MainDashboard
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
                MainDashboard(
                    viewModel = musicViewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
