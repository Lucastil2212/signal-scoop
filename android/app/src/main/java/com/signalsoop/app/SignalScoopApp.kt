package com.signalsoop.app

import android.app.Application
import com.signalsoop.app.assistant.ScanAssistant
import com.signalsoop.app.history.ScanHistoryRepository
import com.signalsoop.app.history.db.ScanHistoryDatabase
import com.signalsoop.app.llm.MpLlmInference
import com.signalsoop.app.prefs.LlmPrefs

class SignalScoopApp : Application() {
    lateinit var llmPrefs: LlmPrefs
        private set

    val llm: MpLlmInference = MpLlmInference()
    lateinit var scanAssistant: ScanAssistant
        private set

    lateinit var scanHistoryRepository: ScanHistoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        llmPrefs = LlmPrefs(this)
        scanAssistant = ScanAssistant(llm)
        val historyDb = ScanHistoryDatabase.create(this)
        scanHistoryRepository = ScanHistoryRepository(historyDb.scanHistoryDao())
    }
}
