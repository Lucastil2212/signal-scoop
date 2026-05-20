package com.signalsoop.app.history.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedScanEntity::class,
        GraphNodeEntity::class,
        GraphEdgeEntity::class,
        SignalAliasEntity::class,
        GraphMediaEntity::class,
        UserGraphNodeEntity::class,
        DeviceLinkEntity::class,
        EvrusIdentityLinkEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class ScanHistoryDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        private const val DB_NAME = "signal_scoop_scan_history.db"

        fun create(context: Context): ScanHistoryDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                ScanHistoryDatabase::class.java,
                DB_NAME,
            )
                .addMigrations(ScanHistoryMigrations.MIGRATION_1_2)
                .build()
    }
}
