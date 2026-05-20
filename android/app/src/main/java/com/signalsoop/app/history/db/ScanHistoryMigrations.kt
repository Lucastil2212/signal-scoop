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
}
