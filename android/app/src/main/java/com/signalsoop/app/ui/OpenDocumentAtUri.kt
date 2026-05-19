package com.signalsoop.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.result.contract.ActivityResultContract

/** Opens the system document picker, optionally starting at [input] (API 26+). */
class OpenDocumentAtUri : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("application/octet-stream", "*/*"))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (input != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
            }
        }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? {
        if (intent == null || resultCode != android.app.Activity.RESULT_OK) return null
        return intent.data
    }
}
