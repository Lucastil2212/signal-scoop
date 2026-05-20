package com.signalsoop.app.scan

object WifiScanUtil {
    fun channelFromFrequencyMhz(mhz: Int): Int? =
        when {
            mhz in 2412..2484 -> (mhz - 2412) / 5 + 1
            mhz in 5170..5825 -> (mhz - 5000) / 5
            else -> null
        }

    fun bandLabel(mhz: Int): String =
        when {
            mhz >= 5000 -> "5 GHz"
            mhz in 2400..2500 -> "2.4 GHz"
            else -> "${mhz} MHz"
        }
}
