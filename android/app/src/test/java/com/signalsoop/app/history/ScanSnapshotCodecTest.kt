package com.signalsoop.app.history

import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.FindingExtras
import com.signalsoop.app.model.ScanSessionContext
import com.signalsoop.app.model.SignalCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanSnapshotCodecTest {
    @Test
    fun roundTripsFindingExtras() {
        val finding =
            Finding(
                id = "ble-aa:bb:cc:dd:ee:ff",
                category = SignalCategory.BLE,
                title = "Beacon",
                detail = "aa:bb:cc:dd:ee:ff · -55 dBm",
                signalStrength = -55,
                riskPoints = 2,
                extras =
                    FindingExtras(
                        bleManufacturerId = 0x004C,
                        bleServiceUuids = listOf("0000180f-0000-1000-8000-00805f9b34fb"),
                        bleTxPower = -8,
                        bleConnectable = true,
                        bleAdvertisementHex = "020106",
                    ),
            )
        val decoded = ScanSnapshotCodec.decodeFindings(ScanSnapshotCodec.encodeFindings(listOf(finding)))
        assertEquals(1, decoded.size)
        assertEquals(0x004C, decoded[0].extras.bleManufacturerId)
        assertEquals(1, decoded[0].extras.bleServiceUuids?.size)
        assertEquals(true, decoded[0].extras.bleConnectable)
    }

    @Test
    fun roundTripsSessionContext() {
        val ctx =
            ScanSessionContext(
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                androidVersion = "14",
                sdkInt = 34,
                scanDurationMs = 12_500L,
                permissionsGranted = listOf("location", "bluetooth_scan"),
                airplaneModeOn = false,
                wifiEnabled = true,
                bluetoothEnabled = true,
                nfcEnabled = false,
                vpnActive = null,
            )
        val json = ScanSnapshotCodec.encodeSessionContext(ctx)
        assertNotNull(json)
        val decoded = ScanSnapshotCodec.decodeSessionContext(json)
        assertNotNull(decoded)
        assertEquals("Pixel", decoded!!.deviceModel)
        assertEquals(12_500L, decoded.scanDurationMs)
        assertTrue(decoded.permissionsGranted.contains("location"))
    }
}
