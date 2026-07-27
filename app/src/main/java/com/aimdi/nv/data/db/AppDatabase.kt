package com.aimdi.nv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.aimdi.nv.data.model.AppMetaEntity
import com.aimdi.nv.data.model.ArtistEntity
import com.aimdi.nv.data.model.ArtistFts
import com.aimdi.nv.data.model.ComboEntity
import com.aimdi.nv.data.model.Converters
import com.aimdi.nv.data.model.PromptEntity
import com.aimdi.nv.data.model.PromptFts
import com.aimdi.nv.data.repository.ArtistSeedLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun promptDao(): PromptDao
    abstract fun comboDao(): ComboDao
    abstract fun metaDao(): MetaDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: build(context.applicationContext).also { instance = it }
            }
        }

        private fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(context, AppDatabase::class.java, "naicomposer.db")
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        // Seed after open via application scope
                        CoroutineScope(Dispatchers.IO).launch {
                            get(context).let { database ->
                                ArtistSeedLoader.seedIfNeeded(context, database)
                            }
                        }
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        CoroutineScope(Dispatchers.IO).launch {
                            get(context).let { database ->
                                ArtistSeedLoader.seedIfNeeded(context, database)
                            }
                        }
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
