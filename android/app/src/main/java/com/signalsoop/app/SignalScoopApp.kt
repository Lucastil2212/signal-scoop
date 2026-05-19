package com.signalsoop.app

import android.app.Application
import com.signalsoop.app.llm.MpLlmInference
import com.signalsoop.app.prefs.LlmPrefs

class SignalScoopApp : Application() {
    lateinit var llmPrefs: LlmPrefs
        private set

    val llm: MpLlmInference = MpLlmInference()

    override fun onCreate() {
        super.onCreate()
        llmPrefs = LlmPrefs(this)
    }
}
