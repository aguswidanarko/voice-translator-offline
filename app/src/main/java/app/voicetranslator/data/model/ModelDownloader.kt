package app.voicetranslator.data.model

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class ModelDownloader(private val context: Context) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(30, TimeUnit.MINUTES)
        .build()

    /**
     * Downloads (resuming a partial file when possible) and verifies the archive checksum.
     * Keep the returned file until [install] has been called.
     */
    fun download(pkg: ModelPackage, baseUrl: String, onProgress: (Long, Long) -> Unit): File {
        val part = File(context.cacheDir, "downloads/${pkg.archive}.part")
        part.parentFile?.mkdirs()

        val already = if (part.isFile) part.length() else 0L
        val url = baseUrl.trimEnd('/') + "/" + pkg.archive
        val request = Request.Builder()
            .url(url)
            .apply { if (already > 0) header("Range", "bytes=$already-") }
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code} untuk ${pkg.archive}")

            val resuming = response.code == 206 && already > 0
            if (!resuming && part.exists()) part.delete()
            val startAt = if (resuming) already else 0L

            val body = response.body ?: throw IOException("respons kosong untuk ${pkg.archive}")
            var written = startAt
            body.byteStream().use { input ->
                FileOutputStream(part, resuming).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        if (Thread.currentThread().isInterrupted) throw IOException("download dibatalkan")
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(written, pkg.sizeBytes)
                    }
                }
            }
        }

        val actual = sha256(part)
        if (!actual.equals(pkg.sha256, ignoreCase = true)) {
            part.delete()
            throw IOException("checksum tidak cocok untuk ${pkg.archive}")
        }
        return part
    }

    fun install(archive: File, pkg: ModelPackage, store: ModelStore) {
        val target = store.dir(pkg)
        val temp = File(store.root, "${pkg.installPath}.tmp")
        temp.deleteRecursively()
        temp.mkdirs()

        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val outFile = File(temp, entry.name)
                if (!outFile.canonicalPath.startsWith(temp.canonicalPath)) {
                    throw IOException("entri arsip tidak valid: ${entry.name}")
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
            }
        }

        target.deleteRecursively()
        target.parentFile?.mkdirs()
        if (!temp.renameTo(target)) throw IOException("gagal memasang ${pkg.id}")
        archive.delete()
    }

    fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 20)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
