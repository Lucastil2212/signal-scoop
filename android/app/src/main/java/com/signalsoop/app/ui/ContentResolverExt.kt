package com.signalsoop.app.ui

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns

fun ContentResolver.queryDisplayName(uri: Uri): String? {
    query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (index >= 0 && cursor.moveToFirst()) {
            return cursor.getString(index)
        }
    }
    return null
}
