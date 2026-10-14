package com.wakeup.alarm.ring

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Last-resort alarm sound, generated in code, used when no ringtone can be played.
 * It guarantees the alarm is never silent.
 */
class FallbackBeep(private val attributes: AudioAttributes) {
    private var track: AudioTrack? = null

    fun start() {
        val sampleRate = 22_050
        val samples = ShortArray(sampleRate) // one second: two short beeps, then looped
        for (i in samples.indices) {
            val t = i / sampleRate.toDouble()
            val beepOn = (t % 0.5) < 0.25
            samples[i] = if (beepOn) (sin(2 * PI * 880 * t) * Short.MAX_VALUE * 0.9).toInt().toShort() else 0
        }
        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        audioTrack.write(samples, 0, samples.size)
        audioTrack.setLoopPoints(0, samples.size, -1)
        audioTrack.setVolume(1f)
        audioTrack.play()
        track = audioTrack
    }

    fun stop() {
        track?.runCatching {
            stop()
            release()
        }
        track = null
    }
}
