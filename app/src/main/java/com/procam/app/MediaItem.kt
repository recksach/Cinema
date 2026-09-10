package com.procam.app

import android.net.Uri

data class MediaItem(
    val uri: Uri,
    val isVideo: Boolean,
    val dateTaken: Long
)
