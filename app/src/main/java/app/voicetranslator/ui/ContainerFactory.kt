package app.voicetranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.voicetranslator.AppContainer
import app.voicetranslator.VoiceTranslatorApp

inline fun <reified T : ViewModel> containerFactory(crossinline create: (AppContainer) -> T): ViewModelProvider.Factory =
    viewModelFactory {
        initializer {
            val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VoiceTranslatorApp
            create(app.container)
        }
    }
