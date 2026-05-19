package com.signalsoop.app.scan

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.signalsoop.app.model.Finding
import com.signalsoop.app.model.SignalCategory
import java.util.UUID

class SensorScanner(context: Context) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    fun scan(): List<Finding> {
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        if (sensors.isEmpty()) {
            return listOf(
                Finding(
                    id = UUID.randomUUID().toString(),
                    category = SignalCategory.SENSORS,
                    title = "No sensors reported",
                    detail = "This device did not expose any sensors to the app.",
                ),
            )
        }

        return sensors.map { sensor ->
            Finding(
                id = "sensor-${sensor.type}-${sensor.name}",
                category = SignalCategory.SENSORS,
                title = sensor.name,
                detail = "Vendor: ${sensor.vendor} · Type: ${sensor.stringType}",
            )
        }
    }
}
