package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "device_links")
data class DeviceLinkEntity(
    @PrimaryKey val id: String,
    val signalKey: String,
    val deviceAddress: String,
    val connectionLabel: String,
    val isPaired: Boolean,
    val linkedAtEpochMs: Long,
)
