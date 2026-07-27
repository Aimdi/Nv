package app.promptcompanion.nai

import android.app.Application
import app.promptcompanion.nai.data.db.AppDatabase
import app.promptcompanion.nai.data.repository.ArtistRepository
import app.promptcompanion.nai.data.repository.PreviewPackManager
import app.promptcompanion.nai.data.repository.PromptRepository
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

class PromptCompanionApp : Application(), ImageLoaderFactory {
    lateinit var database: AppDatabase
        private set
    lateinit var promptRepository: PromptRepository
        private set
    lateinit var artistRepository: ArtistRepository
        private set
    lateinit var previewPackManager: PreviewPackManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.get(this)
        promptRepository = PromptRepository(database)
        artistRepository = ArtistRepository(database)
        previewPackManager = PreviewPackManager(this, database)
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("coil_disk"))
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: PromptCompanionApp
            private set
    }
}
