package dev.naicompanion.app.di

import android.content.Context
import dev.naicompanion.app.BuildConfig
import dev.naicompanion.app.data.backup.BackupRepository
import dev.naicompanion.app.data.catalog.CatalogDatabase
import dev.naicompanion.app.data.packs.PackRepository
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.DraftRepository
import dev.naicompanion.app.data.repository.FavoriteTagRepository
import dev.naicompanion.app.data.repository.PromptRepository
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.data.user.UserDatabase
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Hand-rolled dependency container.
 *
 * The graph is small and entirely singleton-scoped, so a full DI framework would add build time
 * and indirection without buying anything here.
 */
class AppContainer(private val context: Context) {

    private val userDatabase: UserDatabase by lazy { UserDatabase.build(context) }
    private val catalogDatabase: CatalogDatabase by lazy { CatalogDatabase.build(context) }

    val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            // Pack downloads can be hundreds of megabytes on a slow connection.
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy { SettingsRepository(context) }
    val draftRepository: DraftRepository by lazy { DraftRepository(context) }

    val promptRepository: PromptRepository by lazy { PromptRepository(userDatabase.promptDao()) }
    val comboRepository: ComboRepository by lazy { ComboRepository(userDatabase.comboDao()) }
    val favoriteTagRepository: FavoriteTagRepository by lazy {
        FavoriteTagRepository(userDatabase.favoriteTagDao())
    }
    val catalogRepository: CatalogRepository by lazy {
        CatalogRepository(catalogDatabase.catalogDao())
    }
    val packRepository: PackRepository by lazy { PackRepository(context, httpClient) }

    val backupRepository: BackupRepository by lazy {
        BackupRepository(
            context = context,
            promptDao = userDatabase.promptDao(),
            comboDao = userDatabase.comboDao(),
            favoriteTagDao = userDatabase.favoriteTagDao(),
            appVersion = BuildConfig.VERSION_NAME,
        )
    }
}
