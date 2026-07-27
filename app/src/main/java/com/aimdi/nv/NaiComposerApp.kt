package com.aimdi.nv

import android.app.Application
import com.aimdi.nv.data.db.AppDatabase
import com.aimdi.nv.data.export.BackupExporter
import com.aimdi.nv.data.preview.PreviewPackManager
import com.aimdi.nv.data.repository.ArtistRepository
import com.aimdi.nv.data.repository.ComboRepository
import com.aimdi.nv.data.repository.PromptRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NaiComposerApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    lateinit var database: AppDatabase
        private set
    lateinit var artists: ArtistRepository
        private set
    lateinit var prompts: PromptRepository
        private set
    lateinit var combos: ComboRepository
        private set
    lateinit var backup: BackupExporter
        private set
    lateinit var previewPack: PreviewPackManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = AppDatabase.get(this)
        artists = ArtistRepository(database.artistDao())
        prompts = PromptRepository(database.promptDao())
        combos = ComboRepository(database.comboDao())
        backup = BackupExporter(prompts, combos)
        previewPack = PreviewPackManager(this, database.metaDao())
        appScope.launch { previewPack.refresh() }
    }

    companion object {
        lateinit var instance: NaiComposerApp
            private set
    }
}
