package com.signalsoop.app.llm

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import java.io.File

/**
 * On-device model checkpoints live under app-specific external storage so they appear in
 * file managers and the system document picker (Android/data/.../files/models/).
 */
object ModelStorage {
    private const val MODELS_SUBDIR = "models"
    private const val MIN_TASK_BYTES = 512L

    fun modelsDir(context: Context): File {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, MODELS_SUBDIR)
        if (!dir.exists()) dir.mkdirs()
        migrateLegacyModels(context, dir)
        return dir
    }

    fun presetFile(context: Context, preset: LiteRtModelPreset): File =
        File(modelsDir(context), preset.filename)

    fun listTaskFiles(context: Context): List<File> =
        modelsDir(context)
            .listFiles()
            ?.filter { file -> file.isFile && file.name.endsWith(".task", ignoreCase = true) }
            ?.filter { file -> file.length() > MIN_TASK_BYTES }
            ?.sortedBy { it.name.lowercase() }
            .orEmpty()

    fun folderLabel(context: Context): String {
        val dir = modelsDir(context)
        return dir.absolutePath
    }

    /** URI for [DocumentsContract.EXTRA_INITIAL_URI] when opening the document picker. */
    fun modelsFolderDocumentUri(context: Context): Uri? {
        val dir = modelsDir(context)
        if (!dir.exists()) dir.mkdirs()
        return FileProvider.getUriForFile(context, providerAuthority(context), dir)
    }

    fun downloadsFolderDocumentUri(): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:${Environment.DIRECTORY_DOWNLOADS}",
            )
        } else {
            null
        }

    fun providerAuthority(context: Context): String = "${context.packageName}.fileprovider"

    private fun migrateLegacyModels(context: Context, destDir: File) {
        val sources =
            listOf(context.filesDir) +
                context.getExternalFilesDir(null)?.let { listOf(it) }.orEmpty()
        for (source in sources) {
            source.listFiles()
                ?.filter { it.isFile && it.name.endsWith(".task", ignoreCase = true) && it.length() > MIN_TASK_BYTES }
                ?.forEach { legacy ->
                    val target = File(destDir, legacy.name)
                    if (!target.exists() || target.length() < legacy.length()) {
                        legacy.copyTo(target, overwrite = true)
                    }
                }
        }
    }
}
