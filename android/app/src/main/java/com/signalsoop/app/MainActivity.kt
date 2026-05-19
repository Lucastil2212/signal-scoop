package com.signalsoop.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.signalsoop.app.scan.ScanPermissions
import com.signalsoop.app.ui.SignalScoopScreen
import com.signalsoop.app.ui.theme.SignalScoopTheme
import com.signalsoop.app.ui.theme.ScoopBlack

class MainActivity : ComponentActivity() {
    private val viewModel: ScanViewModel by viewModels()
    private var requestingPermissions = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            requestingPermissions = false
            viewModel.refreshPermissionState()
            if (results.isNotEmpty() && results.values.all { it }) {
                viewModel.startScan()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        viewModel.refreshPermissionState()

        setContent {
            SignalScoopTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ScoopBlack,
                ) {
                    SignalScoopScreen(
                        viewModel = viewModel,
                        onScanClick = { viewModel.startScan() },
                        onRequestPermissions = { requestPermissionsThenScan() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPermissionState()
    }

    override fun onStop() {
        super.onStop()
        if (!requestingPermissions && !isChangingConfigurations) {
            viewModel.clearSensitiveResults()
        }
    }

    override fun onDestroy() {
        viewModel.clearSensitiveResults()
        super.onDestroy()
    }

    private fun requestPermissionsThenScan() {
        val missing = ScanPermissions.missing(this)
        if (missing.isNotEmpty()) {
            requestingPermissions = true
            permissionLauncher.launch(missing)
        } else {
            viewModel.startScan()
        }
    }
}
