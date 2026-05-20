package com.signalsoop.app.mesh

import android.content.Context
import com.signalsoop.app.mesh.db.MeshDao
import com.signalsoop.app.mesh.db.MeshMessageEntity
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeshRepository(
    private val context: Context,
    private val dao: MeshDao,
) {
    val messages: Flow<List<MeshMessageEntity>> = dao.observeMessages()
    val peers = dao.observePeers()

    suspend fun exportInboxTxt(): File {
        val messages = dao.getAllMessages().sortedBy { it.sentAtEpochMs }
        val dir = File(context.filesDir, "mesh_exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "signal-scoop-mesh-$stamp.txt")
        file.bufferedWriter().use { out ->
            out.appendLine("Signal Scoop — local mesh messages (plaintext export)")
            out.appendLine("Manticore Technologies, LLC")
            out.appendLine()
            messages.forEach { m ->
                out.appendLine("[${m.direction}] ${m.peerPrincipal}")
                out.appendLine(m.plaintext)
                out.appendLine("---")
            }
        }
        return file
    }
}
