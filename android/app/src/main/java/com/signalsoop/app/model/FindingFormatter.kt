package com.signalsoop.app.model

object FindingFormatter {
    fun extrasLines(extras: FindingExtras): List<String> {
        if (extras.isEmpty()) return emptyList()
        return buildList {
            extras.frequencyMhz?.let { mhz ->
                val ch = extras.wifiChannel?.let { " · ch $it" }.orEmpty()
                val band = extras.wifiBand?.let { " · $it" }.orEmpty()
                add("$mhz MHz$ch$band")
            }
            if (extras.isConnectedAp == true) add("Connected to this phone")
            extras.bleManufacturerId?.let { add("Manufacturer ID 0x${it.toString(16)}") }
            extras.bleServiceUuids?.takeIf { it.isNotEmpty() }?.let { uuids ->
                val shown = uuids.take(3).joinToString(", ")
                val more = if (uuids.size > 3) " (+${uuids.size - 3})" else ""
                add("Services: $shown$more")
            }
            extras.bleTxPower?.let { add("TX power $it dBm") }
            extras.bleConnectable?.let { add(if (it) "Connectable" else "Non-connectable") }
            extras.bleAdvertisementHex?.let { add("Adv ${it.take(48)}${if (it.length > 48) "…" else ""}") }
            extras.bluetoothBondState?.let { add("Bond: $it") }
            extras.bluetoothDeviceClass?.let { add("Class: $it") }
            extras.bluetoothDeviceType?.let { add("Type: $it") }
            extras.sensorMaxRange?.let { add("Max range ${"%.2f".format(it)}") }
            extras.sensorResolution?.let { add("Resolution ${"%.4f".format(it)}") }
            extras.sensorMinDelayUs?.let { add("Min delay ${it}µs") }
            extras.sensorPowerMa?.let { add("Power ${"%.2f".format(it)} mA") }
            extras.firstSeenEpochMs?.let { first ->
                extras.lastSeenEpochMs?.let { last ->
                    if (last != first) add("Seen ${last - first}ms in scan window")
                }
            }
        }
    }

    fun extrasOneLiner(extras: FindingExtras): String? {
        val lines = extrasLines(extras)
        if (lines.isEmpty()) return null
        return lines.joinToString(" · ")
    }
}
