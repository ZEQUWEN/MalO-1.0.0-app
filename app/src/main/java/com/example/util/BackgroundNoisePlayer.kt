package com.example.util

import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.audiofx.PresetReverb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sin

class BackgroundNoisePlayer {
    private var audioTrack: AudioTrack? = null
    private var reverb: PresetReverb? = null
    private var job: Job? = null
    private val sampleRate = 44100

    fun start() {
        if (job?.isActive == true) return

        val minSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        try {
            reverb = PresetReverb(1, audioTrack!!.audioSessionId).apply {
                preset = PresetReverb.PRESET_LARGEHALL
                enabled = true
            }
            audioTrack?.attachAuxEffect(reverb!!.id)
            audioTrack?.setAuxEffectSendLevel(1.0f)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        audioTrack?.play()

        job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(minSize)
            var phase = 0.0
            val frequency = 55.0 // low hum
            while (isActive) {
                for (i in buffer.indices) {
                    // Combine low hum and static noise
                    val hum = (sin(phase) * 6000).toInt()
                    val static = (-1500..1500).random()
                    // Add periodic "scratch/glitch" in noise
                    val modifier = if (kotlin.random.Random.nextFloat() < 0.005f) 4000 else 0
                    val glitch = if (modifier > 0) (-modifier..modifier).random() else 0
                    
                    var out = hum + static + glitch
                    if (out > Short.MAX_VALUE) out = Short.MAX_VALUE.toInt()
                    if (out < Short.MIN_VALUE) out = Short.MIN_VALUE.toInt()
                    
                    buffer[i] = out.toShort()
                    phase += 2 * Math.PI * frequency / sampleRate
                    if (phase > 2 * Math.PI) phase -= 2 * Math.PI
                }
                try {
                    audioTrack?.write(buffer, 0, buffer.size)
                } catch (e: Exception) {
                    break
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {}
        audioTrack = null
        try {
            reverb?.release()
        } catch (e: Exception) {}
        reverb = null
    }
}
