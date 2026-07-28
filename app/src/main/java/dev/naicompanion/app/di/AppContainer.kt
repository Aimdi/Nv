package dev.naicompanion.app.di

import android.content.Context
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dev.naicompanion.app.BuildConfig
import dev.naicompanion.app.data.backup.BackupRepository
import dev.naicompanion.app.data.catalog.CatalogDatabase
import dev.naicompanion.app.data.packs.PackRepository
import dev.naicompanion.app.data.remote.DanbooruApi
import dev.naicompanion.app.data.remote.DanbooruRepository
import dev.naicompanion.app.data.remote.HfPreviewFetcher
import dev.naicompanion.app.data.remote.HfPreviewKeyer
import dev.naicompanion.app.data.repository.CatalogRepository
import dev.naicompanion.app.data.repository.ComboRepository
import dev.naicompanion.app.data.repository.DraftRepository
import dev.naicompanion.app.data.repository.FavoriteTagRepository
import dev.naicompanion.app.data.repository.PromptRepository
import dev.naicompanion.app.data.settings.SettingsRepository
import dev.naicompanion.app.data.settings.draftDataStore
import dev.naicompanion.app.data.settings.settingsDataStore
import dev.naicompanion.app.data.user.UserDatabase
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Application dependency graph.
 *
 * Structured as singleton providers (Room, Retrofit, Coil, repositories) the same way a Hilt
 * `@Module` / `@Singleton` graph would be — kept hand-rolled so the build stays light.
 */
class AppContainer(private val context: Context) {

    private val userDatabase: UserDatabase by lazy { UserDatabase.build(context) }
    private val catalogDatabase: CatalogDatabase by lazy { CatalogDatabase.build(context) }

    private val json: Json by lazy {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    val httpClient: OkHttpClient by lazy {
        val cacheDir = File(context.cacheDir, "http").apply { mkdirs() }
        OkHttpClient.Builder()
            .cache(Cache(cacheDir, 20L * 1024L * 1024L))
            .connectTimeout(30, TimeUnit.SECONDS)
            // Pack downloads can be hundreds of megabytes on a slow connection.
            .readTimeout(120, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    private val danbooruApi: DanbooruApi by lazy {
        Retrofit.Builder()
            .baseUrl(DanbooruApi.BASE_URL)
            .client(httpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(DanbooruApi::class.java)
    }

    val danbooruRepository: DanbooruRepository by lazy { DanbooruRepository(danbooruApi) }

    /** Shared Coil loader with bounded disk cache and HuggingFace preview fallback. */
    val imageLoader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .okHttpClient(httpClient)
            .memoryCache {
                MemoryCache.Builder(context)
                    .maxSizePercent(0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "coil_previews"))
                    .maxSizeBytes(250L * 1024L * 1024L)
                    .build()
            }
            .components {
                add(HfPreviewKeyer())
                add(HfPreviewFetcher.Factory(httpClient))
            }
            .crossfade(true)
            .build()
    }

    val settingsRepository: SettingsRepository by lazy {
        SettingsRepository(settingsDataStore(context))
    }
    val draftRepository: DraftRepository by lazy { DraftRepository(draftDataStore(context)) }

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
