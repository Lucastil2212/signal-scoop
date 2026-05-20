package com.signalsoop.app.mesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MeshDao {
    @Query("SELECT * FROM mesh_messages ORDER BY sentAtEpochMs DESC")
    fun observeMessages(): Flow<List<MeshMessageEntity>>

    @Query("SELECT * FROM mesh_messages ORDER BY sentAtEpochMs ASC")
    suspend fun getAllMessages(): List<MeshMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(entity: MeshMessageEntity)

    @Query("SELECT * FROM mesh_sessions")
    suspend fun getAllSessions(): List<MeshSessionEntity>

    @Query("SELECT * FROM mesh_sessions WHERE id = :id LIMIT 1")
    suspend fun getSession(id: String): MeshSessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSession(entity: MeshSessionEntity)

    @Query("SELECT * FROM mesh_peers ORDER BY lastSeenEpochMs DESC")
    fun observePeers(): Flow<List<MeshPeerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeer(entity: MeshPeerEntity)

    @Query("SELECT * FROM mesh_peers WHERE bleAddress = :address LIMIT 1")
    suspend fun peerByBle(address: String): MeshPeerEntity?
}
