package app.voicetranslator.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.concurrent.thread

/** Push-to-talk capture: 16 kHz mono PCM in memory only, never written to disk. */
class AudioRecorder {

    private var record: AudioRecord? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    private val chunks = mutableListOf<ShortArray>()

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuffer <= 0) return false

        val recorder = try {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minBuffer * 2, SAMPLE_RATE),
            )
        } catch (e: SecurityException) {
            return false
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return false
        }

        chunks.clear()
        running = true
        record = recorder
        recorder.startRecording()
        worker = thread(name = "audio-capture", isDaemon = true) {
            val buffer = ShortArray(2048)
            while (running) {
                val read = recorder.read(buffer, 0, buffer.size)
                if (read > 0) {
                    synchronized(chunks) { chunks.add(buffer.copyOf(read)) }
                }
            }
        }
        return true
    }

    /** Stops capture and returns float samples in [-1, 1]. */
    fun stop(): FloatArray {
        running = false
        worker?.join(500)
        worker = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null

        val joined = synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            val all = ShortArray(total)
            var offset = 0
            for (chunk in chunks) {
                chunk.copyInto(all, offset)
                offset += chunk.size
            }
            chunks.clear()
            all
        }
        val samples = FloatArray(joined.size)
        for (i in joined.indices) samples[i] = joined[i] / 32768f
        return samples
    }

    fun cancel() {
        running = false
        worker?.join(200)
        worker = null
        record?.let {
            runCatching { it.stop() }
            it.release()
        }
        record = null
        synchronized(chunks) { chunks.clear() }
    }

    companion object {
        const val SAMPLE_RATE = 16000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
    }
}
