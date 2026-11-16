package app.voicetranslator.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.voicetranslator.AppContainer
import app.voicetranslator.data.model.ModelDownloadWorker
import app.voicetranslator.data.model.ModelPackage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ModelsViewModel(private val container: AppContainer) : ViewModel() {

    data class PackageUi(
        val pkg: ModelPackage,
        val installed: Boolean,
        val downloading: Boolean,
        val progress: Float?,
        val error: String?,
    )

    data class UiState(
        val packages: List<PackageUi> = emptyList(),
        val baseUrl: String = "",
        val usedBytes: Long = 0,
        val totalBytes: Long = 0,
        val freeBytes: Long = 0,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()

    private var workInfos: List<List<WorkInfo>> = emptyList()

    init {
        viewModelScope.launch {
            container.settings.settings.collect { value ->
                val base = value.baseUrlOverride.ifBlank { container.catalog.catalog.baseUrl }
                _state.update { it.copy(baseUrl = base) }
            }
        }
        val flows = container.catalog.catalog.packages.map { pkg ->
            WorkManager.getInstance(container.context)
                .getWorkInfosForUniqueWorkFlow(ModelDownloadWorker.workName(pkg.id))
        }
        if (flows.isEmpty()) {
            render()
        } else {
            viewModelScope.launch {
                combine(flows) { infos -> infos.toList() }.collect { infos ->
                    workInfos = infos
                    render()
                }
            }
        }
    }

    private fun render() {
        viewModelScope.launch(Dispatchers.Default) {
            val packages = container.catalog.catalog.packages
            val items = withContext(Dispatchers.IO) {
                packages.mapIndexed { index, pkg ->
                    val info = workInfos.getOrNull(index)?.firstOrNull()
                    val installed = container.store.isInstalled(pkg)
                    val running = info?.state == WorkInfo.State.RUNNING || info?.state == WorkInfo.State.ENQUEUED
                    val progress = if (info?.state == WorkInfo.State.RUNNING) {
                        val total = info.progress.getLong(ModelDownloadWorker.KEY_TOTAL, pkg.sizeBytes).coerceAtLeast(1)
                        val done = info.progress.getLong(ModelDownloadWorker.KEY_DONE, 0)
                        (done.toFloat() / total).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    val error = info?.outputData?.getString(ModelDownloadWorker.KEY_ERROR)
                        ?.takeIf { info.state == WorkInfo.State.FAILED }
                    PackageUi(pkg, installed, running, progress, error)
                }
            }
            val used = withContext(Dispatchers.IO) { container.store.installedBytes(packages) }
            _state.update {
                it.copy(
                    packages = items,
                    usedBytes = used,
                    totalBytes = packages.sumOf { pkg -> pkg.sizeBytes },
                    freeBytes = container.store.freeBytes(),
                )
            }
        }
    }

    fun download(packageId: String) {
        val pkg = container.catalog.byId(packageId) ?: return
        val baseUrl = _state.value.baseUrl.trim()
        if (baseUrl.isEmpty()) {
            _state.update { it.copy(message = "URL sumber model belum diatur. Buka Pengaturan.") }
            return
        }
        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setInputData(
                workDataOf(
                    ModelDownloadWorker.KEY_PACKAGE to pkg.id,
                    ModelDownloadWorker.KEY_BASE_URL to baseUrl,
                )
            )
            .build()
        WorkManager.getInstance(container.context)
            .enqueueUniqueWork(ModelDownloadWorker.workName(pkg.id), ExistingWorkPolicy.KEEP, request)
    }

    fun downloadAll() {
        _state.value.packages
            .filter { it.pkg.required && !it.installed && !it.downloading }
            .forEach { download(it.pkg.id) }
    }

    fun delete(packageId: String) {
        val pkg = container.catalog.byId(packageId) ?: return
        WorkManager.getInstance(container.context)
            .cancelUniqueWork(ModelDownloadWorker.workName(pkg.id))
        viewModelScope.launch(Dispatchers.IO) {
            container.store.delete(pkg)
            render()
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    companion object {
        val factory = containerFactory { ModelsViewModel(it) }
    }
}
