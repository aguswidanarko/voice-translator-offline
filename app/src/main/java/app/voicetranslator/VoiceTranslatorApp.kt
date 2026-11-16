package app.voicetranslator

import android.app.Application
import android.content.ComponentCallbacks2
import android.util.Log

class VoiceTranslatorApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Log.i(TAG, "Melepas model AI karena tekanan memori (level $level)")
            container.engines.releaseAllAsync()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        container.engines.releaseAllAsync()
    }

    override fun onTerminate() {
        container.engines.releaseAllAsync()
        super.onTerminate()
    }

    private companion object {
        const val TAG = "VoiceTranslator"
    }
}
