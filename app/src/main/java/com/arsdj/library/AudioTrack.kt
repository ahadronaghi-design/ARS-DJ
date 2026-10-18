package com.arsdj.library

import android.net.Uri

data class AudioTrack(
    val id: Long,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val uri: Uri
)
