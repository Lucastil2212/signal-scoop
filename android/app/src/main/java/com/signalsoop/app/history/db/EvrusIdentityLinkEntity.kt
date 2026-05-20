package com.signalsoop.app.history.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "evrus_identity_links")
data class EvrusIdentityLinkEntity(
    @PrimaryKey val id: String,
    val signalKey: String?,
    val scanId: String?,
    val evrusDid: String,
    val displayName: String?,
    val p2pPeerId: String?,
    val evrmoreChainRef: String?,
    val linkedAtEpochMs: Long,
)
