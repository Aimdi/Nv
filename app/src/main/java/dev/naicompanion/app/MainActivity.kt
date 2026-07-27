package dev.naicompanion.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import dev.naicompanion.app.ui.NaiCompanionApp
import dev.naicompanion.app.ui.theme.NaiCompanionTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as dev.naicompanion.app.NaiCompanionApp).container
        val sharedText = IncomingText.from(intent)

        setContent {
            NaiCompanionTheme {
                NaiCompanionApp(
                    container = container,
                    initialSharedText = remember { sharedText },
                )
            }
        }
    }
}
