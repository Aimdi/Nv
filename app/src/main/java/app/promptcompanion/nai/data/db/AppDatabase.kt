package app.promptcompanion.nai.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import app.promptcompanion.nai.data.db.dao.ArtistDao
import app.promptcompanion.nai.data.db.dao.ComboDao
import app.promptcompanion.nai.data.db.dao.MetaDao
import app.promptcompanion.nai.data.db.dao.PromptDao
import app.promptcompanion.nai.data.db.entity.AppMetaEntity
import app.promptcompanion.nai.data.db.entity.ArtistEntity
import app.promptcompanion.nai.data.db.entity.ArtistFts
import app.promptcompanion.nai.data.db.entity.ComboEntity
import app.promptcompanion.nai.data.db.entity.PromptEntity
import app.promptcompanion.nai.data.db.entity.PromptFts
import org.json.JSONArray

@Database(
    entities = [
        ArtistEntity::class,
        ArtistFts::class,
        PromptEntity::class,
        PromptFts::class,
        ComboEntity::class,
        AppMetaEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun promptDao(): PromptDao
    abstract fun comboDao(): ComboDao
    abstract fun metaDao(): MetaDao

    companion object {
        const val NAME = "prompt_companion.db"
        const val META_ARTISTS_SEEDED = "artists_seeded_v1"
        const val META_PREVIEW_PACK_VERSION = "preview_pack_version"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    NAME,
                ).build().also { instance = it }
            }
        }
    }
}

object ArtistSeeder {
    suspend fun seedIfNeeded(context: Context, database: AppDatabase) {
        val meta = database.metaDao()
        if (meta.get(AppDatabase.META_ARTISTS_SEEDED) == "1" && database.artistDao().count() > 0) {
            return
        }
        val artists = loadFromAssets(context)
        if (artists.isEmpty()) return
        database.artistDao().clear()
        artists.chunked(500).forEach { chunk ->
            database.artistDao().insertAll(chunk)
        }
        meta.put(AppMetaEntity(AppDatabase.META_ARTISTS_SEEDED, "1"))
    }

    fun loadFromAssets(context: Context): List<ArtistEntity> {
        return try {
            context.assets.open("artists.json").bufferedReader().use { reader ->
                parseArtistsJson(reader.readText())
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseArtistsJson(json: String): List<ArtistEntity> {
        val array = JSONArray(json)
        val out = ArrayList<ArtistEntity>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val name = obj.getString("name")
            if (name.contains("banned", ignoreCase = true)) continue
            val id = obj.getLong("id")
            val postCount = obj.optInt("post_count", 0)
            val display = name.replace('_', ' ')
            val thumb = sanitizeFileName(name) + ".webp"
            out += ArtistEntity(
                id = id,
                name = name,
                displayName = display,
                postCount = postCount,
                source = ArtistEntity.SOURCE_NAI_V3,
                thumbnailFile = thumb,
                detailFile = sanitizeFileName(name) + "_detail.webp",
            )
        }
        return out
    }

    fun sanitizeFileName(name: String): String {
        return name.lowercase()
            .replace(Regex("[^a-z0-9._-]+"), "_")
            .trim('_')
    }
}
