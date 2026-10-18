package com.arsdj.library

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

class MediaStoreScanner(private val context: Context) {
    fun loadAudio(): List<AudioTrack> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val result = mutableListOf<AudioTrack>()
        context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                result += AudioTrack(
                    id = id,
                    title = cursor.getString(titleCol) ?: "بدون عنوان",
                    artist = cursor.getString(artistCol)?.takeUnless { it == "<unknown>" } ?: "هنرمند نامشخص",
                    durationMs = cursor.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return result
    }
}
