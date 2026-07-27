package com.nai.promptcompanion

import android.app.Application
import androidx.room.Room
import com.nai.promptcompanion.data.backup.BackupIo
import com.nai.promptcompanion.data.catalog.CatalogDatabase
import com.nai.promptcompanion.data.catalog.CatalogSeeder
import com.nai.promptcompanion.data.imagepack.ImagePackManager
import com.nai.promptcompanion.data.prefs.SettingsStore
import com.nai.promptcompanion.data.repo.CatalogRepository
import com.nai.promptcompanion.data.repo.ComboRepository
import com.nai.promptcompanion.data.repo.FavoritesRepository
import com.nai.promptcompanion.data.repo.PromptRepository
import com.nai.promptcompanion.data.user.UserDatabase
import com.nai.promptcompanion.ui.builder.BuilderStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** State of the first-run catalog seeding, surfaced in the Tags tab. */
sealed interface CatalogSeedState {
    data object Seeding : CatalogSeedState
    data class Ready(val count: Int) : CatalogSeedState
    data class Failed(val message: String) : CatalogSeedState
}

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsStore(app)

    val userDb: UserDatabase = Room.databaseBuilder(app, UserDatabase::class.java, "user.db")
        .fallbackToDestructiveMigration()
        .build()

    val catalogDb: CatalogDatabase =
        Room.databaseBuilder(app, CatalogDatabase::class.java, "catalog.db")
            .fallbackToDestructiveMigration()
            .build()

    val prompts = PromptRepository(userDb)
    val combos = ComboRepository(userDb)
    val favorites = FavoritesRepository(userDb)
    val catalog = CatalogRepository(catalogDb)

    val backupIo = BackupIo(app)
    val imagePack = ImagePackManager(app, settings)

    val builderState = BuilderStateHolder(settings, appScope).apply {
        restoreThenAutoPersist()
    }

    private val _catalogSeedState = MutableStateFlow<CatalogSeedState>(CatalogSeedState.Seeding)
    val catalogSeedState: StateFlow<CatalogSeedState> = _catalogSeedState

    fun startSeeding(app: Application) {
        appScope.launch {
            runCatching {
                CatalogSeeder(app, catalogDb).seedIfEmpty()
            }.onSuccess { count ->
                settings.setSeededCatalogCount(count)
                _catalogSeedState.value = CatalogSeedState.Ready(count)
            }.onFailure { e ->
                _catalogSeedState.value = CatalogSeedState.Failed(e.message ?: "Seeding failed")
            }
        }
        appScope.launch { imagePack.refresh() }
    }
}

class NaiCompanionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.startSeeding(this)
    }
}
