package com.example.util

import android.media.MediaPlayer
import java.io.File

class AudioPlayerHelper(
    private val onProgress: (Float) -> Unit,
    private val onCompletion: () -> Unit
) {
    private var mediaPlayer: MediaPlayer? = null
    var isPlaying = false
        private set

    fun play(filePath: String) {
        if (isPlaying) stop()
        mediaPlayer = MediaPlayer().apply {
            setDataSource(filePath)
            prepare()
            start()
            setOnCompletionListener {
                this@AudioPlayerHelper.isPlaying = false
                this@AudioPlayerHelper.onCompletion()
            }
        }
        isPlaying = true
    }

    fun stop() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        isPlaying = false
    }
}
