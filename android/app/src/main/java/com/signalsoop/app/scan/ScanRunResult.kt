package com.signalsoop.app.scan

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.ScanSessionContext

data class ScanRunResult(
    val findings: List<Finding>,
    val sessionContext: ScanSessionContext,
)
