package com.signalsoop.app.history.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {
    @Query("SELECT * FROM saved_scans ORDER BY scannedAtEpochMs DESC")
    fun observeAllScans(): Flow<List<SavedScanEntity>>

    @Query("SELECT * FROM saved_scans ORDER BY scannedAtEpochMs DESC")
    suspend fun getAllScans(): List<SavedScanEntity>

    @Query("SELECT * FROM saved_scans WHERE id = :id LIMIT 1")
    suspend fun getScan(id: String): SavedScanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScan(entity: SavedScanEntity)

    @Query("DELETE FROM saved_scans WHERE id = :id")
    suspend fun deleteScan(id: String)

    @Query("UPDATE saved_scans SET name = :name WHERE id = :id")
    suspend fun renameScan(id: String, name: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertNodes(nodes: List<GraphNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEdges(edges: List<GraphEdgeEntity>)

    @Query("SELECT * FROM graph_nodes")
    suspend fun getAllNodes(): List<GraphNodeEntity>

    @Query("SELECT * FROM graph_edges")
    suspend fun getAllEdges(): List<GraphEdgeEntity>

    @Query("DELETE FROM graph_edges WHERE scanId = :scanId")
    suspend fun deleteEdgesForScan(scanId: String)

    @Query("DELETE FROM graph_nodes WHERE id NOT IN (SELECT fromNodeId FROM graph_edges UNION SELECT toNodeId FROM graph_edges)")
    suspend fun pruneOrphanNodes()

    @Query("SELECT * FROM signal_aliases")
    suspend fun getAllAliases(): List<SignalAliasEntity>

    @Query("SELECT * FROM signal_aliases")
    fun observeAliases(): Flow<List<SignalAliasEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAlias(entity: SignalAliasEntity)

    @Query("DELETE FROM signal_aliases WHERE signalKey = :signalKey")
    suspend fun deleteAlias(signalKey: String)

    @Query("SELECT * FROM graph_media ORDER BY capturedAtEpochMs DESC")
    fun observeMedia(): Flow<List<GraphMediaEntity>>

    @Query("SELECT * FROM graph_media ORDER BY capturedAtEpochMs DESC")
    suspend fun getAllMedia(): List<GraphMediaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMedia(entity: GraphMediaEntity)

    @Query("DELETE FROM graph_media WHERE id = :id")
    suspend fun deleteMedia(id: String)

    @Query("SELECT * FROM user_graph_nodes ORDER BY createdAtEpochMs DESC")
    fun observeUserNodes(): Flow<List<UserGraphNodeEntity>>

    @Query("SELECT * FROM user_graph_nodes ORDER BY createdAtEpochMs DESC")
    suspend fun getAllUserNodes(): List<UserGraphNodeEntity>

    @Query("SELECT * FROM device_links ORDER BY linkedAtEpochMs DESC")
    fun observeDeviceLinks(): Flow<List<DeviceLinkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUserNode(entity: UserGraphNodeEntity)

    @Query("DELETE FROM user_graph_nodes WHERE id = :id")
    suspend fun deleteUserNode(id: String)

    @Query("SELECT * FROM device_links ORDER BY linkedAtEpochMs DESC")
    suspend fun getAllDeviceLinks(): List<DeviceLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeviceLink(entity: DeviceLinkEntity)

    @Query("SELECT * FROM evrus_identity_links ORDER BY linkedAtEpochMs DESC")
    fun observeEvrusLinks(): Flow<List<EvrusIdentityLinkEntity>>

    @Query("SELECT * FROM evrus_identity_links ORDER BY linkedAtEpochMs DESC")
    suspend fun getAllEvrusLinks(): List<EvrusIdentityLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvrusLink(entity: EvrusIdentityLinkEntity)

    @Query("DELETE FROM graph_media WHERE scanId = :scanId")
    suspend fun deleteMediaForScan(scanId: String)

    @Query("DELETE FROM evrus_identity_links WHERE scanId = :scanId")
    suspend fun deleteEvrusForScan(scanId: String)

    @Query("DELETE FROM user_graph_nodes WHERE linkedScanId = :scanId")
    suspend fun deleteUserNodesForScan(scanId: String)
}
