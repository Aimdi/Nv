package com.aimdi.nv.data.repository

import android.content.Context
import com.aimdi.nv.data.db.AppDatabase
import com.aimdi.nv.data.model.AppMetaEntity
import com.aimdi.nv.data.model.ArtistEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ArtistSeedLoader {
    private const val META_SEEDED = "artists_seeded_v1"
    private val mutex = Mutex()
    private val gson = Gson()

    suspend fun seedIfNeeded(context: Context, db: AppDatabase) = mutex.withLock {
        val meta = db.metaDao()
        if (meta.get(META_SEEDED) == "1" && db.artistDao().count() > 0) return

        val artists = loadFromAssets(context)
        if (artists.isEmpty()) return

        // Insert in batches to avoid binder transaction limits
        artists.chunked(500).forEach { chunk ->
            db.artistDao().insertAll(chunk)
        }
        meta.put(AppMetaEntity(META_SEEDED, "1"))
    }

    private fun loadFromAssets(context: Context): List<ArtistEntity> {
        return try {
            context.assets.open("artists_seed.json").bufferedReader().use { reader ->
                val type = object : TypeToken<List<SeedArtist>>() {}.type
                val seeds: List<SeedArtist> = gson.fromJson(reader, type) ?: emptyList()
                seeds.map {
                    ArtistEntity(
                        id = it.id,
                        name = it.name,
                        postCount = it.postCount,
                        source = it.source ?: "nai-v3",
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private data class SeedArtist(
        val id: Long,
        val name: String,
        val postCount: Int = 0,
        val source: String? = "nai-v3",
    )
}
