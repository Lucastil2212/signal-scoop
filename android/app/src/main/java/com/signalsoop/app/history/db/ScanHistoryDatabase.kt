package com.signalsoop.app.history.db

import android.content.Context
import android.util.Log
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
        com.signalsoop.app.mesh.db.MeshMessageEntity::class,
        com.signalsoop.app.mesh.db.MeshSessionEntity::class,
        com.signalsoop.app.mesh.db.MeshPeerEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class ScanHistoryDatabase : RoomDatabase() {
    abstract fun scanHistoryDao(): ScanHistoryDao

    abstract fun meshDao(): com.signalsoop.app.mesh.db.MeshDao

    companion object {
        private const val DB_NAME = "signal_scoop_scan_history.db"

        fun create(context: Context): ScanHistoryDatabase {
            val appContext = context.applicationContext
            return runCatching { open(appContext) }
                .getOrElse { error ->
                    Log.e(TAG, "Scan history DB open failed; recreating local database", error)
                    appContext.deleteDatabase(DB_NAME)
                    open(appContext)
                }
        }

        private fun open(context: Context): ScanHistoryDatabase =
            Room.databaseBuilder(context, ScanHistoryDatabase::class.java, DB_NAME)
                .addMigrations(
                    ScanHistoryMigrations.MIGRATION_1_2,
                    ScanHistoryMigrations.MIGRATION_2_3,
                    ScanHistoryMigrations.MIGRATION_3_4,
                )
                .build()

        private const val TAG = "ScanHistoryDatabase"
    }
}
