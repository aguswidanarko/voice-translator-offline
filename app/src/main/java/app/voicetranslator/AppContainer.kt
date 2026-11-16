package app.voicetranslator

import android.content.Context
import androidx.room.Room
import app.voicetranslator.data.db.AppDatabase
import app.voicetranslator.data.db.HistoryDao
import app.voicetranslator.data.model.ModelCatalogRepository
import app.voicetranslator.data.model.ModelDownloader
import app.voicetranslator.data.model.ModelStore
import app.voicetranslator.data.settings.SettingsRepository
import app.voicetranslator.engine.EngineHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val context: Context get() = appContext

    val catalog = ModelCatalogRepository(appContext)
    val store = ModelStore(appContext)
    val settings = SettingsRepository(appContext)
    val downloader = ModelDownloader(appContext)

    val database: AppDatabase = Room.databaseBuilder(appContext, AppDatabase::class.java, "translator.db")
        .fallbackToDestructiveMigration()
        .build()

    val history: HistoryDao = database.history()

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val engines = EngineHost(catalog, store, engineScope)
}
