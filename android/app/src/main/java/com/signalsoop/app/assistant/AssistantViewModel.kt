package com.signalsoop.app.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.signalsoop.app.ScanUiState
import com.signalsoop.app.SignalScoopApp
import com.signalsoop.app.llm.DeviceAdvisor
import com.signalsoop.app.llm.LiteRtModelPreset
import com.signalsoop.app.llm.ModelDiskLocation
import com.signalsoop.app.llm.ModelStorage
import com.signalsoop.app.llm.downloadLiteRtCheckpoint
import java.io.File
import com.signalsoop.app.prefs.LlmPrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(val fromUser: Boolean, val text: String)

data class LocalModelOption(
    val label: String,
    val absolutePath: String,
    val isCurrentPresetDownload: Boolean,
)

data class AssistantUiState(
    val preset: LiteRtModelPreset = LiteRtModelPreset.DEFAULT,
    val modelReady: Boolean = false,
    val downloading: Boolean = false,
    val downloadBytes: Long = 0L,
    val deviceHints: List<String> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val userInput: String = "",
    val isGenerating: Boolean = false,
    val hfToken: String = "",
    val statusLine: String = "Summarize & analyze work without a model. Load .task for open-ended questions.",
    val modelsFolderPath: String = "",
    val localModelOptions: List<LocalModelOption> = emptyList(),
)

class AssistantViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as SignalScoopApp
    private val prefs: LlmPrefs = app.llmPrefs

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    init {
        val preset = LiteRtModelPreset.fromOrdinal(prefs.presetOrdinal)
        _uiState.update {
            it.copy(
                preset = preset,
                hfToken = prefs.hfTokenOptional,
                modelReady = app.llm.isLoaded(),
            )
        }
        refreshDeviceHints()
        refreshLocalModelOptions()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { ensureModelLoaded(preset) }
                .onSuccess {
                    _uiState.update { s -> s.copy(modelReady = app.llm.isLoaded()) }
                }
        }
    }

    fun refreshDeviceHints() {
        val preset = _uiState.value.preset
        _uiState.update {
            it.copy(deviceHints = DeviceAdvisor.lines(getApplication(), preset))
        }
    }

    fun setPreset(preset: LiteRtModelPreset) {
        prefs.presetOrdinal = preset.ordinal
        app.llm.release()
        _uiState.update {
            it.copy(
                preset = preset,
                modelReady = false,
                statusLine = "Preset: ${preset.title}. Download or import a matching .task file.",
            )
        }
        refreshDeviceHints()
        refreshLocalModelOptions()
    }

    fun refreshLocalModelOptions() {
        val preset = _uiState.value.preset
        val presetPath = ModelStorage.presetFile(app, preset).absolutePath
        val options =
            ModelStorage.listTaskFiles(app).map { file ->
                LocalModelOption(
                    label = file.name,
                    absolutePath = file.absolutePath,
                    isCurrentPresetDownload = file.absolutePath == presetPath,
                )
            }
        _uiState.update {
            it.copy(
                modelsFolderPath = ModelStorage.folderLabel(app),
                localModelOptions = options,
            )
        }
    }

    fun selectLocalModel(path: String) {
        onModelImported(path)
    }

    fun setHfToken(token: String) {
        prefs.hfTokenOptional = token
        _uiState.update { it.copy(hfToken = token) }
    }

    fun setUserInput(text: String) {
        _uiState.update { it.copy(userInput = text) }
    }

    fun importModelFromUri(
        openStream: () -> java.io.InputStream,
        suggestedName: String = "imported-${System.nanoTime()}.task",
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val safeName =
                    suggestedName
                        .substringAfterLast('/')
                        .takeIf { it.endsWith(".task", ignoreCase = true) }
                        ?: "$suggestedName.task"
                val dst = File(ModelStorage.modelsDir(app), safeName)
                openStream().use { input ->
                    dst.outputStream().use { output -> input.copyTo(output) }
                }
                dst.setReadOnly()
                onModelImported(dst.absolutePath)
            }.onFailure { err ->
                _uiState.update {
                    it.copy(statusLine = err.message ?: "Model import failed")
                }
            }
        }
    }

    fun onModelImported(path: String) {
        prefs.localModelPath = path
        app.llm.release()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                ensureModelLoaded(_uiState.value.preset)
            }.onSuccess {
                refreshLocalModelOptions()
                _uiState.update {
                    it.copy(
                        modelReady = true,
                        statusLine = "Model ready · ${path.substringAfterLast('/')}",
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        modelReady = false,
                        statusLine = err.message ?: "Model import failed",
                    )
                }
            }
        }
    }

    fun downloadPreset() {
        val preset = _uiState.value.preset
        if (preset.needsHfAuth && _uiState.value.hfToken.isBlank()) {
            _uiState.update { it.copy(statusLine = "Gated model — add Hugging Face token first.") }
            return
        }
        if (_uiState.value.downloading) return

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(downloading = true, downloadBytes = 0L) }
            try {
                val dst = preset.targetFile(app)
                downloadLiteRtCheckpoint(
                    preset.canonicalDownloadUrl,
                    dst,
                    _uiState.value.hfToken.takeIf { preset.needsHfAuth },
                ) { received ->
                    withContext(Dispatchers.Main) {
                        _uiState.update { s -> s.copy(downloadBytes = received) }
                    }
                }
                prefs.localModelPath = dst.absolutePath
                app.llm.release()
                ensureModelLoaded(preset)
                refreshLocalModelOptions()
                _uiState.update {
                    it.copy(
                        modelReady = true,
                        statusLine =
                            "Model downloaded · ${dst.length() / (1024L * 1024L)} MiB · ${ModelStorage.folderLabel(app)}",
                    )
                }
            } catch (err: Exception) {
                _uiState.update {
                    it.copy(statusLine = err.message ?: "Download failed")
                }
            } finally {
                _uiState.update { it.copy(downloading = false) }
            }
        }
    }

    fun ask(scanState: ScanUiState) {
        val question = _uiState.value.userInput.trim()
        if (question.isBlank() || _uiState.value.isGenerating) return

        _uiState.update {
            it.copy(
                messages = it.messages + ChatMessage(fromUser = true, text = question),
                userInput = "",
                isGenerating = true,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                if (scanState.findings.isEmpty()) {
                    error("Run a scan first so the assistant has signal data to reference.")
                }
                val historyInsights = app.scanHistoryRepository.buildInsights()
                val response =
                    app.scanAssistant.respond(
                        question = question,
                        findings = scanState.findings,
                        riskSummary = scanState.riskSummary,
                        historyInsights = historyInsights,
                    )
                response
            }.onSuccess { response ->
                _uiState.update {
                    it.copy(
                        messages = it.messages + ChatMessage(fromUser = false, text = response.text),
                        modelReady = app.llm.isLoaded(),
                        isGenerating = false,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        messages =
                            it.messages +
                                ChatMessage(
                                    fromUser = false,
                                    text = err.message ?: "Could not generate an answer.",
                                ),
                        isGenerating = false,
                    )
                }
            }
        }
    }

    private fun ensureModelLoaded(preset: LiteRtModelPreset) {
        if (app.llm.isLoaded()) return
        val hint =
            prefs.localModelPath.trim().takeIf { it.isNotEmpty() }?.let { ModelDiskLocation(it) }
        app.llm.load(app, preset, hint)
    }
}
