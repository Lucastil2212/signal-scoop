package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_aliases")
data class SignalAliasEntity(
    @PrimaryKey val signalKey: String,
    val petName: String,
    val notes: String?,
    val updatedAtEpochMs: Long,
)
