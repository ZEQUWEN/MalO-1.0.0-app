package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream

object VideoThumbnailHelper {
    fun extractFrameAsBase64(context: Context, videoUri: Uri, timestampMs: Long = 1000L): String? {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val bitmap = retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            if (bitmap != null) {
                val maxDim = 512
                val width: Int
                val height: Int
                if (bitmap.width > bitmap.height) {
                    width = maxDim
                    val ratio = bitmap.height.toFloat() / bitmap.width
                    height = (maxDim * ratio).toInt()
                } else {
                    height = maxDim
                    val ratio = bitmap.width.toFloat() / bitmap.height
                    width = (maxDim * ratio).toInt()
                }
                
                val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                val base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                if (scaled != bitmap) {
                    scaled.recycle()
                }
                bitmap.recycle()
                return base64
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
        return null
    }

    fun getVideoDurationSeconds(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val time = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = time?.toLong() ?: 0L
            return durationMs / 1000
        } catch (e: Exception) {
            return 0
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
