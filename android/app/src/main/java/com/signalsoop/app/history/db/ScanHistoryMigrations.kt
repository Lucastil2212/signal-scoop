package com.signalsoop.app.history.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object ScanHistoryMigrations {
    val MIGRATION_1_2 =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS signal_aliases (
                        signalKey TEXT NOT NULL PRIMARY KEY,
                        petName TEXT NOT NULL,
                        notes TEXT,
                        updatedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS graph_media (
                        id TEXT NOT NULL PRIMARY KEY,
                        scanId TEXT,
                        nodeId TEXT,
                        signalKey TEXT,
                        mediaType TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        caption TEXT,
                        capturedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS user_graph_nodes (
                        id TEXT NOT NULL PRIMARY KEY,
                        label TEXT NOT NULL,
                        body TEXT NOT NULL,
                        linkedScanId TEXT,
                        linkedSignalKey TEXT,
                        createdAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS device_links (
                        id TEXT NOT NULL PRIMARY KEY,
                        signalKey TEXT NOT NULL,
                        deviceAddress TEXT NOT NULL,
                        connectionLabel TEXT NOT NULL,
                        isPaired INTEGER NOT NULL,
                        linkedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS evrus_identity_links (
                        id TEXT NOT NULL PRIMARY KEY,
                        signalKey TEXT,
                        scanId TEXT,
                        evrusDid TEXT NOT NULL,
                        displayName TEXT,
                        p2pPeerId TEXT,
                        evrmoreChainRef TEXT,
                        linkedAtEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

    val MIGRATION_2_3 =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mesh_messages (
                        id TEXT NOT NULL PRIMARY KEY,
                        sessionId TEXT NOT NULL,
                        peerPrincipal TEXT NOT NULL,
                        direction TEXT NOT NULL,
                        plaintext TEXT NOT NULL,
                        sentAtEpochMs INTEGER NOT NULL,
                        exported INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mesh_sessions (
                        id TEXT NOT NULL PRIMARY KEY,
                        localPrincipal TEXT NOT NULL,
                        remotePrincipal TEXT NOT NULL,
                        rootKeyHex TEXT NOT NULL,
                        sendChainHex TEXT NOT NULL,
                        sendCounter INTEGER NOT NULL,
                        recvChainHex TEXT NOT NULL,
                        recvCounter INTEGER NOT NULL,
                        dhPubB64 TEXT NOT NULL,
                        skippedJson TEXT NOT NULL,
                        peerAddress TEXT,
                        linkedSignalKey TEXT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mesh_peers (
                        peerId TEXT NOT NULL PRIMARY KEY,
                        principal TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        host TEXT,
                        port INTEGER NOT NULL,
                        bleAddress TEXT,
                        prekeysJson TEXT,
                        lastSeenEpochMs INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }
}
