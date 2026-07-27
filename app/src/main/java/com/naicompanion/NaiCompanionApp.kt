package com.naicompanion

import android.app.Application
import android.content.Context
import androidx.room.Room
import com.naicompanion.data.AppRepository
import com.naicompanion.data.db.AppDatabase

class AppContainer(context: Context) {
    val db: AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "nai_companion.db",
    ).build()

    val repository: AppRepository = AppRepository(context.applicationContext, db)
}

class NaiCompanionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
