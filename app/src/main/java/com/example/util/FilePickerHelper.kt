package com.example.util

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable

object FilePickerHelper {

    @Composable
    fun rememberGalleryPicker(
        onResult: (Uri?) -> Unit
    ): ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?> {
        return rememberLauncherForActivityResult(
            contract = ActivityResultContracts.PickVisualMedia(),
            onResult = onResult
        )
    }
}
