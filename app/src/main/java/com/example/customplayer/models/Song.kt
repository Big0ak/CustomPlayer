// Song.kt
package com.example.customplayer.models

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val uri: Uri
)
