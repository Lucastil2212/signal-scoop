package com.signalsoop.app.ui.graph

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.signalsoop.app.history.GraphColorPalette
import com.signalsoop.app.history.GraphTimelineFilter
import com.signalsoop.app.history.GraphVisLink
import com.signalsoop.app.history.GraphVisNode
import com.signalsoop.app.history.GraphVisualization
import com.signalsoop.app.history.KnowledgeGraphBuilder
import com.signalsoop.app.history.key
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

private val cartoDarkTiles =
    object : OnlineTileSourceBase(
        "CartoDark",
        0,
        19,
        256,
        ".png",
        arrayOf("https://a.basemaps.cartocdn.com/dark_all/"),
    ) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val z = MapTileIndex.getZoom(pMapTileIndex)
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            return baseUrl + z + "/" + x + "/" + y + mImageFilenameEnding
        }
    }

@Composable
fun KnowledgeGraphMapLayer(
    visualization: GraphVisualization,
    filterScanId: String?,
    onNodeSelected: (nodeId: String, nodeType: String, label: String) -> Unit,
    onLinkSelected: (sourceId: String, targetId: String, relation: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val slice = remember(visualization, filterScanId) { GraphTimelineFilter.slice(visualization, filterScanId) }
    val geoNodes = remember(slice) { slice.nodes.filter { it.lat != null && it.lon != null } }

    DisposableEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
        onDispose { }
    }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(cartoDarkTiles)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            controller.setZoom(15.0)
        }
    }

    LaunchedEffect(geoNodes) {
        if (geoNodes.isEmpty()) return@LaunchedEffect
        val lats = geoNodes.mapNotNull { it.lat }
        val lons = geoNodes.mapNotNull { it.lon }
        val center =
            GeoPoint(
                lats.average(),
                lons.average(),
            )
        mapView.controller.setCenter(center)
        val span = maxOf(lats.max() - lats.min(), lons.max() - lons.min(), 0.002)
        val zoom =
            when {
                span > 0.5 -> 10.0
                span > 0.1 -> 12.0
                span > 0.02 -> 14.0
                else -> 16.0
            }
        mapView.controller.setZoom(zoom)
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { mapView },
        update = { map ->
            map.overlays.clear()
            val nodeById = slice.nodes.associateBy { it.id }
            val focused = filterScanId != null

            slice.links.forEach { link ->
                drawLink(map, link, nodeById, visualization, focused, onLinkSelected)
            }

            geoNodes.forEach { node ->
                val alpha =
                    GraphColorPalette.alphaForEpoch(
                        node.epochMs,
                        visualization.timeMinMs,
                        visualization.timeMaxMs,
                        focused || filterScanId == node.linkedScanId,
                    )
                val marker = Marker(map)
                marker.position = GeoPoint(node.lat!!, node.lon!!)
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                marker.icon =
                    circleDrawable(
                        context,
                        node.color.copy(alpha = alpha).toArgb(),
                        radiusDp(node.type),
                    )
                marker.title = node.label
                marker.subDescription = node.timeLabel ?: node.rawLabel
                marker.setOnMarkerClickListener { _, _ ->
                    onNodeSelected(node.id, node.type, node.label)
                    true
                }
                map.overlays.add(marker)
            }
            map.invalidate()
        },
    )
}

private fun drawLink(
    map: MapView,
    link: GraphVisLink,
    nodeById: Map<String, GraphVisNode>,
    visualization: GraphVisualization,
    focused: Boolean,
    onLinkSelected: (sourceId: String, targetId: String, relation: String) -> Unit,
) {
    val a = nodeById[link.sourceId] ?: return
    val b = nodeById[link.targetId] ?: return
    if (a.lat == null || a.lon == null || b.lat == null || b.lon == null) return
    val line = Polyline(map)
    line.setPoints(
        listOf(
            GeoPoint(a.lat, a.lon),
            GeoPoint(b.lat, b.lon),
        ),
    )
    val color = GraphColorPalette.relationColor(link.relation)
    val alpha =
        GraphColorPalette.alphaForEpoch(
            link.epochMs,
            visualization.timeMinMs,
            visualization.timeMaxMs,
            focused,
        )
    line.outlinePaint.color = color.copy(alpha = alpha * 0.9f).toArgb()
    line.outlinePaint.strokeWidth = if (link.relation == KnowledgeGraphBuilder.REL_AT_PLACE) 8f else 5f
    line.relatedObject = link.key()
    line.setOnClickListener { _, _, _ ->
        onLinkSelected(link.sourceId, link.targetId, link.relation)
        true
    }
    map.overlays.add(line)
}

private fun radiusDp(type: String): Int =
    when (type) {
        KnowledgeGraphBuilder.NODE_SCAN -> 22
        KnowledgeGraphBuilder.NODE_PLACE -> 26
        KnowledgeGraphBuilder.NODE_SIGNAL -> 14
        else -> 16
    }

private fun circleDrawable(context: android.content.Context, colorArgb: Int, radiusDp: Int): android.graphics.drawable.BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val r = (radiusDp * density / 2f).toInt().coerceAtLeast(8)
    val size = r * 2 + 4
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorArgb }
    canvas.drawCircle(size / 2f, size / 2f, r.toFloat(), paint)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    canvas.drawCircle(size / 2f, size / 2f, r.toFloat(), stroke)
    return android.graphics.drawable.BitmapDrawable(context.resources, bitmap)
}
