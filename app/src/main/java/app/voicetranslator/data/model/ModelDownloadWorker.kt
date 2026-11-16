package app.voicetranslator.data.model

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val packageId = inputData.getString(KEY_PACKAGE) ?: return Result.failure()
        val catalog = ModelCatalogRepository(applicationContext)
        val pkg = catalog.byId(packageId) ?: return Result.failure()
        val store = ModelStore(applicationContext)
        if (store.isInstalled(pkg)) return Result.success()

        val baseUrl = inputData.getString(KEY_BASE_URL).orEmpty().ifBlank { catalog.catalog.baseUrl }
        if (baseUrl.isBlank()) {
            return Result.failure(workDataOf(KEY_ERROR to "URL sumber model belum diatur."))
        }

        val needed = (pkg.sizeBytes * 1.3).toLong()
        if (store.freeBytes() < needed) {
            return Result.failure(workDataOf(KEY_ERROR to "Ruang penyimpanan tidak cukup. Butuh ${pkg.sizeBytes / 1_000_000} MB."))
        }

        val downloader = ModelDownloader(applicationContext)
        return try {
            val archive = withContext(Dispatchers.IO) {
                downloader.download(pkg, baseUrl) { done, total ->
                    setProgressAsync(
                        workDataOf(
                            KEY_DONE to done,
                            KEY_TOTAL to if (total > 0) total else pkg.sizeBytes,
                        )
                    )
                }
            }
            withContext(Dispatchers.IO) {
                downloader.install(archive, pkg, store)
                store.markInstalled(pkg)
            }
            Result.success()
        } catch (e: IOException) {
            if (runAttemptCount < 3 && !isStopped) Result.retry()
            else Result.failure(workDataOf(KEY_ERROR to (e.message ?: "Download gagal")))
        }
    }

    companion object {
        const val KEY_PACKAGE = "packageId"
        const val KEY_BASE_URL = "baseUrl"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"

        fun workName(packageId: String) = "model-download-$packageId"
    }
}
