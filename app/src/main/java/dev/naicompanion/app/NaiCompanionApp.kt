package dev.naicompanion.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import dev.naicompanion.app.di.AppContainer

class NaiCompanionApp : Application(), ImageLoaderFactory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(): ImageLoader = container.imageLoader
}
