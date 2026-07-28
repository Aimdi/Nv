package dev.naicompanion.app.data.user

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PromptEntity::class,
        PromptFtsEntity::class,
        ComboEntity::class,
        ComboTagEntity::class,
        FavoriteTagEntity::class,
    ],
    version = UserDatabase.VERSION,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {

    abstract fun promptDao(): PromptDao
    abstract fun comboDao(): ComboDao
    abstract fun favoriteTagDao(): FavoriteTagDao

    companion object {
        const val VERSION = 1
        const val DATABASE_NAME = "nai_user.db"

        fun build(context: Context): UserDatabase =
            Room.databaseBuilder(context, UserDatabase::class.java, DATABASE_NAME)
                .build()
    }
}
