package com.signalsoop.app.llm

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object DeviceAdvisor {
    fun lines(context: Context, preset: LiteRtModelPreset): List<String> {
        val msgs = mutableListOf<String>()
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also(am::getMemoryInfo)
        val totalMi = (mi.totalMem / (1024L * 1024L)).coerceAtLeast(1L)

        msgs += "Device RAM ≈ ${totalMi} MiB (OS-reported)."
        msgs += if (mi.lowMemory) {
            "Low-memory flag: active — close background apps before loading a model."
        } else {
            "Low-memory flag: not critical."
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            msgs += "Android API ${Build.VERSION.SDK_INT} — prefer SmolLM preset on older devices."
        }

        val has64 =
            Build.SUPPORTED_64_BIT_ABIS.any { abi ->
                abi.contains("arm64", ignoreCase = true) ||
                    abi.contains("x86_64", ignoreCase = true)
            }
        if (!has64) {
            msgs += "No 64-bit ABI — on-device LLM may be very slow."
        }

        if (totalMi < preset.recommendedRamMb) {
            msgs += "${preset.title} recommends ≥ ${preset.recommendedRamMb} MiB RAM — try SmolLM-135M."
        }
        return msgs
    }
}
