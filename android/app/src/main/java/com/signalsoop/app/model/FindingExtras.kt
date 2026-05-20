package com.signalsoop.app.model

import org.json.JSONArray
import org.json.JSONObject

/** Optional per-signal fields collected during scan (v1.7+). */
data class FindingExtras(
    val frequencyMhz: Int? = null,
    val wifiChannel: Int? = null,
    val wifiBand: String? = null,
    val isConnectedAp: Boolean? = null,
    val bleManufacturerId: Int? = null,
    val bleServiceUuids: List<String>? = null,
    val bleTxPower: Int? = null,
    val bleAdvertisementHex: String? = null,
    val bleConnectable: Boolean? = null,
    val bluetoothBondState: String? = null,
    val bluetoothDeviceClass: String? = null,
    val bluetoothDeviceType: String? = null,
    val sensorStringType: String? = null,
    val sensorVendor: String? = null,
    val sensorMinDelayUs: Int? = null,
    val sensorMaxRange: Float? = null,
    val sensorResolution: Float? = null,
    val sensorPowerMa: Float? = null,
    val firstSeenEpochMs: Long? = null,
    val lastSeenEpochMs: Long? = null,
) {
    fun isEmpty(): Boolean =
        frequencyMhz == null &&
            wifiChannel == null &&
            wifiBand == null &&
            isConnectedAp == null &&
            bleManufacturerId == null &&
            bleServiceUuids.isNullOrEmpty() &&
            bleTxPower == null &&
            bleAdvertisementHex == null &&
            bleConnectable == null &&
            bluetoothBondState == null &&
            bluetoothDeviceClass == null &&
            bluetoothDeviceType == null &&
            sensorStringType == null &&
            sensorVendor == null &&
            sensorMinDelayUs == null &&
            sensorMaxRange == null &&
            sensorResolution == null &&
            sensorPowerMa == null &&
            firstSeenEpochMs == null &&
            lastSeenEpochMs == null

    companion object {
        val EMPTY = FindingExtras()
    }
}

object FindingExtrasCodec {
    fun toJson(extras: FindingExtras): JSONObject? {
        if (extras.isEmpty()) return null
        return JSONObject().apply {
            extras.frequencyMhz?.let { put("frequencyMhz", it) }
            extras.wifiChannel?.let { put("wifiChannel", it) }
            extras.wifiBand?.let { put("wifiBand", it) }
            extras.isConnectedAp?.let { put("isConnectedAp", it) }
            extras.bleManufacturerId?.let { put("bleManufacturerId", it) }
            extras.bleServiceUuids?.takeIf { it.isNotEmpty() }?.let { uuids ->
                put("bleServiceUuids", JSONArray().apply { uuids.forEach { put(it) } })
            }
            extras.bleTxPower?.let { put("bleTxPower", it) }
            extras.bleAdvertisementHex?.let { put("bleAdvertisementHex", it) }
            extras.bleConnectable?.let { put("bleConnectable", it) }
            extras.bluetoothBondState?.let { put("bluetoothBondState", it) }
            extras.bluetoothDeviceClass?.let { put("bluetoothDeviceClass", it) }
            extras.bluetoothDeviceType?.let { put("bluetoothDeviceType", it) }
            extras.sensorStringType?.let { put("sensorStringType", it) }
            extras.sensorVendor?.let { put("sensorVendor", it) }
            extras.sensorMinDelayUs?.let { put("sensorMinDelayUs", it) }
            extras.sensorMaxRange?.let { put("sensorMaxRange", it.toDouble()) }
            extras.sensorResolution?.let { put("sensorResolution", it.toDouble()) }
            extras.sensorPowerMa?.let { put("sensorPowerMa", it.toDouble()) }
            extras.firstSeenEpochMs?.let { put("firstSeenEpochMs", it) }
            extras.lastSeenEpochMs?.let { put("lastSeenEpochMs", it) }
        }
    }

    fun fromJson(obj: JSONObject?): FindingExtras {
        if (obj == null) return FindingExtras.EMPTY
        val uuids =
            obj.optJSONArray("bleServiceUuids")?.let { arr ->
                buildList {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
            }
        return FindingExtras(
            frequencyMhz = obj.optIntOrNull("frequencyMhz"),
            wifiChannel = obj.optIntOrNull("wifiChannel"),
            wifiBand = obj.optStringOrNull("wifiBand"),
            isConnectedAp = obj.optBooleanOrNull("isConnectedAp"),
            bleManufacturerId = obj.optIntOrNull("bleManufacturerId"),
            bleServiceUuids = uuids,
            bleTxPower = obj.optIntOrNull("bleTxPower"),
            bleAdvertisementHex = obj.optStringOrNull("bleAdvertisementHex"),
            bleConnectable = obj.optBooleanOrNull("bleConnectable"),
            bluetoothBondState = obj.optStringOrNull("bluetoothBondState"),
            bluetoothDeviceClass = obj.optStringOrNull("bluetoothDeviceClass"),
            bluetoothDeviceType = obj.optStringOrNull("bluetoothDeviceType"),
            sensorStringType = obj.optStringOrNull("sensorStringType"),
            sensorVendor = obj.optStringOrNull("sensorVendor"),
            sensorMinDelayUs = obj.optIntOrNull("sensorMinDelayUs"),
            sensorMaxRange = obj.optFloatOrNull("sensorMaxRange"),
            sensorResolution = obj.optFloatOrNull("sensorResolution"),
            sensorPowerMa = obj.optFloatOrNull("sensorPowerMa"),
            firstSeenEpochMs = obj.optLongOrNull("firstSeenEpochMs"),
            lastSeenEpochMs = obj.optLongOrNull("lastSeenEpochMs"),
        )
    }

    fun mergeInto(metadata: JSONObject, extras: FindingExtras) {
        toJson(extras)?.let { metadata.put("extras", it) }
        FindingFormatter.extrasOneLiner(extras)?.let { metadata.put("extrasSummary", it) }
    }

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key) else null

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key) else null

    private fun JSONObject.optFloatOrNull(key: String): Float? =
        if (has(key) && !isNull(key)) optDouble(key).toFloat() else null

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (has(key) && !isNull(key)) optBoolean(key) else null

    private fun JSONObject.optStringOrNull(key: String): String? =
        optString(key).takeIf { it.isNotBlank() && it != "null" }
}
