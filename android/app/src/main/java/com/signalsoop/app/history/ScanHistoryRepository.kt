package com.signalsoop.app.history

import com.signalsoop.app.history.db.GraphEdgeEntity
import com.signalsoop.app.history.db.GraphMediaEntity
import com.signalsoop.app.history.db.GraphNodeEntity
import com.signalsoop.app.history.db.SavedScanEntity
import com.signalsoop.app.history.db.ScanHistoryDao
import com.signalsoop.app.history.db.SignalAliasEntity
import com.signalsoop.app.history.db.UserGraphNodeEntity
import com.signalsoop.app.model.RiskSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ScanHistoryRepository(
    private val dao: ScanHistoryDao,
) {
    val snapshots: Flow<List<ScanSnapshot>> =
        dao.observeAllScans().map { entities -> entities.map { it.toSnapshot() } }

    suspend fun saveScan(
        findings: List<com.signalsoop.app.model.Finding>,
        riskSummary: RiskSummary?,
        geoFix: ScanGeoFix?,
        name: String? = null,
    ): ScanSnapshot {
        val scannedAt = geoFix?.capturedAtEpochMs ?: System.currentTimeMillis()
        val id = UUID.randomUUID().toString()
        val displayName = name?.trim()?.takeIf { it.isNotEmpty() }
            ?: defaultScanName(scannedAt, geoFix)

        val snapshot =
            ScanSnapshot(
                id = id,
                name = displayName,
                scannedAtEpochMs = scannedAt,
                geoFix = geoFix,
                findings = findings,
                riskSummary = riskSummary,
            )

        val priorCounts = priorSignalObservationCounts(excludeScanId = null)
        val delta = KnowledgeGraphBuilder.buildForScan(snapshot, priorCounts)

        dao.upsertScan(snapshot.toEntity())
        if (delta.nodes.isNotEmpty()) dao.upsertNodes(delta.nodes)
        if (delta.edges.isNotEmpty()) dao.upsertEdges(delta.edges)
        dao.pruneOrphanNodes()

        return snapshot
    }

    suspend fun renameScan(id: String, name: String) {
        dao.renameScan(id, name.trim())
        val scanNodeId = KnowledgeGraphBuilder.scanNodeId(id)
        val nodes = dao.getAllNodes()
        val existing = nodes.find { it.id == scanNodeId }
        if (existing != null) {
            dao.upsertNodes(listOf(existing.copy(label = name.trim())))
        }
    }

    suspend fun deleteScan(id: String) {
        dao.getAllMedia().filter { it.scanId == id }.forEach { GraphMediaStorage.deleteFile(it.filePath) }
        dao.deleteMediaForScan(id)
        dao.deleteEvrusForScan(id)
        dao.deleteUserNodesForScan(id)
        dao.deleteEdgesForScan(id)
        dao.deleteScan(id)
        dao.pruneOrphanNodes()
    }

    val aliases: Flow<Map<String, SignalAliasEntity>> =
        dao.observeAliases().map { list -> list.associateBy { it.signalKey } }

    suspend fun setSignalAlias(signalKey: String, petName: String, notes: String? = null) {
        val trimmed = petName.trim()
        if (trimmed.isEmpty()) {
            dao.deleteAlias(signalKey)
            return
        }
        dao.upsertAlias(
            SignalAliasEntity(
                signalKey = signalKey,
                petName = trimmed,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                updatedAtEpochMs = System.currentTimeMillis(),
            ),
        )
        val nodeId = "signal:$signalKey"
        val nodes = dao.getAllNodes()
        nodes.find { it.id == nodeId }?.let { node ->
            dao.upsertNodes(listOf(node.copy(label = trimmed)))
        }
    }

    suspend fun addUserGraphNote(
        label: String,
        body: String,
        linkedScanId: String? = null,
        linkedSignalKey: String? = null,
    ): UserGraphNodeEntity {
        val entity =
            UserGraphNodeEntity(
                id = UUID.randomUUID().toString(),
                label = label.trim(),
                body = body.trim(),
                linkedScanId = linkedScanId,
                linkedSignalKey = linkedSignalKey,
                createdAtEpochMs = System.currentTimeMillis(),
            )
        dao.upsertUserNode(entity)
        return entity
    }

    suspend fun deleteUserGraphNote(id: String) {
        dao.deleteUserNode(id)
    }

    suspend fun attachMedia(
        filePath: String,
        mediaType: String,
        scanId: String? = null,
        nodeId: String? = null,
        signalKey: String? = null,
        caption: String? = null,
    ): GraphMediaEntity {
        val entity =
            GraphMediaEntity(
                id = UUID.randomUUID().toString(),
                scanId = scanId,
                nodeId = nodeId,
                signalKey = signalKey,
                mediaType = mediaType,
                filePath = filePath,
                caption = caption,
                capturedAtEpochMs = System.currentTimeMillis(),
            )
        dao.upsertMedia(entity)
        return entity
    }

    suspend fun deleteMedia(id: String) {
        dao.getAllMedia().find { it.id == id }?.let { GraphMediaStorage.deleteFile(it.filePath) }
        dao.deleteMedia(id)
    }

    suspend fun linkDevice(signalKey: String, deviceAddress: String, label: String, isPaired: Boolean) {
        dao.upsertDeviceLink(
            com.signalsoop.app.history.db.DeviceLinkEntity(
                id = UUID.randomUUID().toString(),
                signalKey = signalKey,
                deviceAddress = deviceAddress,
                connectionLabel = label,
                isPaired = isPaired,
                linkedAtEpochMs = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun scanGpsById(): Map<String, Pair<Double, Double>> =
        dao.getAllScans().mapNotNull { scan ->
            val lat = scan.latitude ?: return@mapNotNull null
            val lon = scan.longitude ?: return@mapNotNull null
            scan.id to Pair(lat, lon)
        }.toMap()

    suspend fun buildGraphJson(): String {
        ensureGraphMaterialized()
        return GraphJsonExporter.toJson(
            nodes = dao.getAllNodes(),
            edges = dao.getAllEdges(),
            aliases = dao.getAllAliases(),
            userNodes = dao.getAllUserNodes(),
            deviceLinks = dao.getAllDeviceLinks(),
            evrusLinks = dao.getAllEvrusLinks(),
            scanGpsById = scanGpsById(),
        )
    }

    suspend fun ensureGraphMaterialized() {
        if (dao.getAllNodes().isNotEmpty()) return
        val scans = dao.getAllScans()
        if (scans.isEmpty()) return
        scans.sortedBy { it.scannedAtEpochMs }.forEach { entity ->
            val snapshot = entity.toSnapshot()
            val prior = priorSignalObservationCounts(excludeScanId = snapshot.id)
            val delta = KnowledgeGraphBuilder.buildForScan(snapshot, prior)
            if (delta.nodes.isNotEmpty()) dao.upsertNodes(delta.nodes)
            if (delta.edges.isNotEmpty()) dao.upsertEdges(delta.edges)
        }
        dao.pruneOrphanNodes()
    }

    suspend fun buildVisualization(): GraphVisualization {
        ensureGraphMaterialized()
        val scans = dao.getAllScans()
        val edges = dao.getAllEdges()
        return GraphVisualizationBuilder.build(
            nodes = dao.getAllNodes(),
            edges = edges,
            aliases = dao.getAllAliases(),
            userNodes = dao.getAllUserNodes(),
            deviceLinks = dao.getAllDeviceLinks(),
            evrusLinks = dao.getAllEvrusLinks(),
            scanGpsById = scanGpsById(),
            scanEpochById = scans.associate { it.id to it.scannedAtEpochMs },
            scanLabelsById = scans.associate { it.id to it.name },
        )
    }

    suspend fun graphEdges(): List<GraphEdgeEntity> = dao.getAllEdges()

    data class VaultSnapshot(
        val scans: List<ScanSnapshot>,
        val aliases: List<SignalAliasEntity>,
        val media: List<GraphMediaEntity>,
        val userNotes: List<UserGraphNodeEntity>,
        val deviceLinks: List<com.signalsoop.app.history.db.DeviceLinkEntity>,
        val evrusLinks: List<com.signalsoop.app.history.db.EvrusIdentityLinkEntity>,
    )

    val vault: Flow<VaultSnapshot> =
        combine(
            combine(
                snapshots,
                dao.observeAliases(),
                dao.observeMedia(),
                dao.observeUserNodes(),
                dao.observeDeviceLinks(),
            ) { scanList, aliasList, mediaList, userList, deviceList ->
                VaultSnapshot(
                    scans = scanList,
                    aliases = aliasList,
                    media = mediaList,
                    userNotes = userList,
                    deviceLinks = deviceList,
                    evrusLinks = emptyList(),
                )
            },
            dao.observeEvrusLinks(),
        ) { partial, evrusList ->
            partial.copy(evrusLinks = evrusList)
        }

    suspend fun getScan(id: String): ScanSnapshot? =
        dao.getScan(id)?.toSnapshot()

    suspend fun buildInsights(): KnowledgeGraphInsights {
        val scans = dao.getAllScans()
        val nodes = dao.getAllNodes()
        val edges = dao.getAllEdges()
        return KnowledgeGraphInsightsEngine.from(scans, nodes, edges)
    }

    suspend fun formatInsightsDigest(): String =
        KnowledgeGraphInsightsEngine.formatDigest(buildInsights())

    private suspend fun priorSignalObservationCounts(excludeScanId: String?): Map<String, Int> {
        val scans = dao.getAllScans().filter { it.id != excludeScanId }
        val counts = mutableMapOf<String, Int>()
        scans.forEach { entity ->
            val findings = ScanSnapshotCodec.decodeFindings(entity.findingsJson)
            KnowledgeGraphBuilder.signalKeysFrom(findings).keys.forEach { key ->
                counts[key] = (counts[key] ?: 0) + 1
            }
        }
        return counts
    }

    private fun defaultScanName(epochMs: Long, geoFix: ScanGeoFix?): String {
        val time =
            SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(epochMs))
        return if (geoFix != null) {
            "Scan $time · ${geoFix.formatCoordinates()}"
        } else {
            "Scan $time"
        }
    }

    private fun ScanSnapshot.toEntity(): SavedScanEntity =
        SavedScanEntity(
            id = id,
            name = name,
            scannedAtEpochMs = scannedAtEpochMs,
            latitude = geoFix?.latitude,
            longitude = geoFix?.longitude,
            accuracyMeters = geoFix?.accuracyMeters,
            altitudeMeters = geoFix?.altitudeMeters,
            locationCapturedAtEpochMs = geoFix?.capturedAtEpochMs,
            locationProvider = geoFix?.provider,
            findingsJson = ScanSnapshotCodec.encodeFindings(findings),
            riskScore = riskSummary?.score,
            riskLevel = riskSummary?.level?.name,
            riskHighlightsJson =
                ScanSnapshotCodec.encodeRiskHighlights(riskSummary?.highlights.orEmpty()),
            placeKey = geoFix?.let { KnowledgeGraphBuilder.placeKeyFor(it.latitude, it.longitude) },
        )

    private fun SavedScanEntity.toSnapshot(): ScanSnapshot =
        ScanSnapshot(
            id = id,
            name = name,
            scannedAtEpochMs = scannedAtEpochMs,
            geoFix =
                if (latitude != null && longitude != null) {
                    ScanGeoFix(
                        latitude = latitude,
                        longitude = longitude,
                        accuracyMeters = accuracyMeters ?: Float.NaN,
                        altitudeMeters = altitudeMeters,
                        capturedAtEpochMs = locationCapturedAtEpochMs ?: scannedAtEpochMs,
                        provider = locationProvider ?: "gps",
                    )
                } else {
                    null
                },
            findings = ScanSnapshotCodec.decodeFindings(findingsJson),
            riskSummary =
                ScanSnapshotCodec.riskSummaryFrom(
                    riskScore,
                    riskLevel,
                    riskHighlightsJson,
                ),
        )
}
