package dev.naicompanion.app.data.catalog

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Read-only tag catalog shipped as a prebuilt asset.
 *
 * Keeping it separate from the user database means a catalog refresh in a future release can
 * simply replace the asset file without touching saved prompts, combos or favourites.
 */
@Database(
    entities = [ArtistEntity::class, ArtistFtsEntity::class],
    version = CatalogDatabase.VERSION,
    exportSchema = true,
)
abstract class CatalogDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    companion object {
        const val VERSION = 1
        const val ASSET_PATH = "catalog/catalog.db"
        private const val DATABASE_NAME = "nai_catalog.db"

        fun build(context: Context): CatalogDatabase =
            Room.databaseBuilder(context, CatalogDatabase::class.java, DATABASE_NAME)
                .createFromAsset(ASSET_PATH)
                // The catalog carries no user data, so replacing it wholesale is always safe.
                .fallbackToDestructiveMigration()
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
    }
}
