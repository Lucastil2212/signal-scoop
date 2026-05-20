package com.signalsoop.app.model

import com.signalsoop.app.util.JsonEncoding.encodeToString
import org.json.JSONArray
import org.json.JSONObject

/** Device and environment snapshot captured once per scan (v1.7+). */
data class ScanSessionContext(
    val deviceManufacturer: String,
    val deviceModel: String,
    val androidVersion: String,
    val sdkInt: Int,
    val scanDurationMs: Long,
    val permissionsGranted: List<String>,
    val airplaneModeOn: Boolean,
    val wifiEnabled: Boolean,
    val bluetoothEnabled: Boolean,
    val nfcEnabled: Boolean,
    val vpnActive: Boolean?,
) {
    fun formatOneLiner(): String =
        buildList {
            add("$deviceManufacturer $deviceModel")
            add("Android $androidVersion (API $sdkInt)")
            add("${scanDurationMs / 1000}s scan")
            if (airplaneModeOn) add("airplane mode")
            if (vpnActive == true) add("VPN active")
        }.joinToString(" · ")
}

object ScanSessionContextCodec {
    fun encode(ctx: ScanSessionContext): String =
        JSONObject().apply {
            put("deviceManufacturer", ctx.deviceManufacturer)
            put("deviceModel", ctx.deviceModel)
            put("androidVersion", ctx.androidVersion)
            put("sdkInt", ctx.sdkInt)
            put("scanDurationMs", ctx.scanDurationMs)
            put("permissionsGranted", JSONArray().apply { ctx.permissionsGranted.forEach { put(it) } })
            put("airplaneModeOn", ctx.airplaneModeOn)
            put("wifiEnabled", ctx.wifiEnabled)
            put("bluetoothEnabled", ctx.bluetoothEnabled)
            put("nfcEnabled", ctx.nfcEnabled)
            ctx.vpnActive?.let { put("vpnActive", it) }
        }.encodeToString()

    fun decode(json: String?): ScanSessionContext? {
        if (json.isNullOrBlank()) return null
        val o = JSONObject(json)
        val perms =
            o.optJSONArray("permissionsGranted")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            } ?: emptyList()
        return ScanSessionContext(
            deviceManufacturer = o.getString("deviceManufacturer"),
            deviceModel = o.getString("deviceModel"),
            androidVersion = o.getString("androidVersion"),
            sdkInt = o.getInt("sdkInt"),
            scanDurationMs = o.getLong("scanDurationMs"),
            permissionsGranted = perms,
            airplaneModeOn = o.optBoolean("airplaneModeOn"),
            wifiEnabled = o.optBoolean("wifiEnabled"),
            bluetoothEnabled = o.optBoolean("bluetoothEnabled"),
            nfcEnabled = o.optBoolean("nfcEnabled"),
            vpnActive = if (o.has("vpnActive") && !o.isNull("vpnActive")) o.getBoolean("vpnActive") else null,
        )
    }
}
