package com.signalsoop.app

import android.app.Application
import com.signalsoop.app.assistant.ScanAssistant
import com.signalsoop.app.evr.EvrusConnector
import com.signalsoop.app.evr.LocalEvrusConnector
import com.signalsoop.app.history.ScanHistoryRepository
import com.signalsoop.app.history.db.ScanHistoryDatabase
import com.signalsoop.app.mesh.db.MeshDao
import com.signalsoop.app.llm.MpLlmInference
import com.signalsoop.app.prefs.LlmPrefs
import java.util.UUID

class SignalScoopApp : Application() {
    lateinit var llmPrefs: LlmPrefs
        private set

    val llm: MpLlmInference = MpLlmInference()
    lateinit var scanAssistant: ScanAssistant
        private set

    lateinit var scanHistoryRepository: ScanHistoryRepository
        private set

    lateinit var evrusConnector: EvrusConnector
        private set

    lateinit var meshDao: MeshDao
        private set

    val deviceMeshId: String by lazy {
        val prefs = getSharedPreferences("mesh_device", MODE_PRIVATE)
        prefs.getString("mesh_id", null)
            ?: UUID.randomUUID().toString().also { prefs.edit().putString("mesh_id", it).apply() }
    }

    override fun onCreate() {
        super.onCreate()
        llmPrefs = LlmPrefs(this)
        scanAssistant = ScanAssistant(llm)
        val historyDb = ScanHistoryDatabase.create(this)
        val dao = historyDb.scanHistoryDao()
        meshDao = historyDb.meshDao()
        scanHistoryRepository = ScanHistoryRepository(dao)
        evrusConnector = LocalEvrusConnector(this, dao)
    }
}
