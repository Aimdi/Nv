package com.nai.promptcompanion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.nai.promptcompanion.ui.AppRoot
import com.nai.promptcompanion.ui.theme.NaiCompanionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NaiCompanionTheme {
                AppRoot()
            }
        }
    }
}
