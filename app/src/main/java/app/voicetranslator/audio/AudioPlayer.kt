package app.voicetranslator.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack

/** Plays synthesized speech with AudioTrack (stream mode) so playback can be interrupted. */
class AudioPlayer {

    private var track: AudioTrack? = null

    fun play(samples: FloatArray, sampleRate: Int) {
        stop()
        if (samples.isEmpty()) return

        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return

        val bufferSize = maxOf(minBuffer, samples.size * 4)
        val player = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        if (player.state != AudioTrack.STATE_INITIALIZED) {
            player.release()
            return
        }

        track = player
        player.play()
        player.write(samples, 0, samples.size, AudioTrack.WRITE_BLOCKING)
    }

    fun stop() {
        track?.let {
            runCatching { if (it.playState == AudioTrack.PLAYSTATE_PLAYING) it.pause() }
            runCatching { it.flush() }
            it.release()
        }
        track = null
    }
}
