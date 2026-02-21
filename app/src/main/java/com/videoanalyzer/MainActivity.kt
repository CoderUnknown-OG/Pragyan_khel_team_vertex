package com.videoanalyzer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.videoanalyzer.ui.screens.MainScreen
import com.videoanalyzer.ui.theme.DarkBackground

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val testVideoUri = intent?.getStringExtra("test_video_uri")?.let { Uri.parse(it) }
        
        setContent {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = DarkBackground
            ) {
                MainScreen(testVideoUri = testVideoUri)
            }
        }
    }
}
