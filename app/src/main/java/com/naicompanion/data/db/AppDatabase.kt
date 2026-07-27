package com.naicompanion.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        PromptEntity::class,
        PromptFts::class,
        ComboEntity::class,
        ArtistTagEntity::class,
        ArtistTagFts::class,
        ArtistFavoriteEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun promptDao(): PromptDao
    abstract fun comboDao(): ComboDao
    abstract fun artistDao(): ArtistDao
    abstract fun artistFavoriteDao(): ArtistFavoriteDao
}
