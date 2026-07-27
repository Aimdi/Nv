package dev.naicompanion.app

import android.app.Application
import dev.naicompanion.app.di.AppContainer

class NaiCompanionApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
