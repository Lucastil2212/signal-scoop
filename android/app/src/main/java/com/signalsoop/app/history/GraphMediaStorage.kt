package com.signalsoop.app.history

import android.content.Context
import java.io.File
import java.util.UUID

object GraphMediaStorage {
    fun mediaRoot(context: Context): File =
        File(context.filesDir, "graph_media").apply { mkdirs() }

    fun newPhotoFile(context: Context): File =
        File(mediaRoot(context), "photo-${UUID.randomUUID()}.jpg")

    fun newVideoFile(context: Context): File =
        File(mediaRoot(context), "video-${UUID.randomUUID()}.mp4")

    fun deleteFile(path: String) {
        runCatching { File(path).delete() }
    }
}
