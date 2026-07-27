package dev.naicompanion.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dev.naicompanion.app.ui.NaiCompanionRoot
import dev.naicompanion.app.ui.theme.NaiCompanionTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    /**
     * Text shared into the app. Held in a flow rather than read once from [getIntent] so that a
     * share arriving while the app is already open is picked up too.
     */
    private val sharedText = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as NaiCompanionApp).container
        sharedText.value = IncomingText.from(intent)

        setContent {
            val incoming by sharedText.collectAsState()
            NaiCompanionTheme {
                NaiCompanionRoot(
                    container = container,
                    sharedText = incoming,
                    onSharedTextHandled = { sharedText.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        IncomingText.from(intent)?.let { sharedText.value = it }
    }
}
