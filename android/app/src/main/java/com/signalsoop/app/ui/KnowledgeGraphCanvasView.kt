package com.signalsoop.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.signalsoop.app.history.GraphColorPalette
import com.signalsoop.app.history.GraphTimelineFilter
import com.signalsoop.app.history.GraphVisLink
import com.signalsoop.app.history.key
import com.signalsoop.app.history.GraphVisNode
import com.signalsoop.app.history.GraphVisualization
import com.signalsoop.app.history.KnowledgeGraphBuilder
import com.signalsoop.app.ui.theme.ScoopMuted
import kotlin.math.max
import kotlin.math.sqrt

@Composable
fun KnowledgeGraphCanvasView(
    visualization: GraphVisualization?,
    onNodeSelected: (nodeId: String, nodeType: String, label: String) -> Unit,
    onLinkSelected: (sourceId: String, targetId: String, relation: String) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "Save a scan on the Scan tab to build your knowledge graph.",
    filterScanId: String? = null,
) {
    if (visualization == null || visualization.nodes.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                emptyMessage,
                color = ScoopMuted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }

    val slice = remember(visualization, filterScanId) { GraphTimelineFilter.slice(visualization, filterScanId) }
    val nodes = slice.nodes
    val links = slice.links
    val nodeById = remember(nodes) { nodes.associateBy { it.id } }
    val focused = filterScanId != null
    val highlightedScanNodeId = filterScanId?.let { KnowledgeGraphBuilder.scanNodeId(it) }

    var pan by remember { mutableStateOf(Offset.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var selectedLinkKey by remember { mutableStateOf<String?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    LaunchedEffect(visualization, filterScanId, canvasSize, nodes) {
        selectedLinkKey = null
        selectedId = highlightedScanNodeId
        zoom = fitZoomForNodes(nodes, canvasSize).coerceIn(0.5f, 2.5f)
        if (canvasSize.width == 0 || canvasSize.height == 0) {
            pan = Offset.Zero
            return@LaunchedEffect
        }
        val focusNode =
            highlightedScanNodeId?.let { scanNodeId -> nodes.find { it.id == scanNodeId } }
                ?: nodes.singleOrNull()
        if (focusNode != null && filterScanId != null) {
            val scale = minOf(canvasSize.width, canvasSize.height) * 0.38f * zoom
            pan = Offset(-focusNode.layoutX * scale, -focusNode.layoutY * scale)
        } else {
            pan = Offset.Zero
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = it },
    ) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .pointerInput(nodes, links) {
                        detectTransformGestures { _, panChange, zoomChange, _ ->
                            pan += panChange
                            zoom = (zoom * zoomChange).coerceIn(0.35f, 4f)
                        }
                    }
                    .pointerInput(nodes, pan, zoom) {
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                pan += dragAmount
                            },
                        )
                    }
                    .pointerInput(nodes, links, pan, zoom) {
                        detectTapGestures { offset ->
                            val linkHit =
                                hitTestLink(offset, links, nodeById, canvasSize, pan, zoom)
                            if (linkHit != null) {
                                selectedLinkKey = linkHit.key()
                                selectedId = null
                                onLinkSelected(linkHit.sourceId, linkHit.targetId, linkHit.relation)
                                return@detectTapGestures
                            }
                            val nodeHit =
                                hitTestNode(offset, nodes, canvasSize, pan, zoom) ?: return@detectTapGestures
                            selectedId = nodeHit.id
                            selectedLinkKey = null
                            onNodeSelected(nodeHit.id, nodeHit.type, nodeHit.label)
                        }
                    },
        ) {
            if (size.width < 2f || size.height < 2f) return@Canvas

            drawRect(
                brush =
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF121A24), Color(0xFF0A0A0F)),
                        center = Offset(size.width * 0.5f, size.height * 0.35f),
                        radius = size.maxDimension * 0.85f,
                    ),
            )
            val gridStep = 56f
            var gx = 0f
            while (gx < size.width) {
                drawLine(Color(0xFF28374B), Offset(gx, 0f), Offset(gx, size.height), strokeWidth = 1f)
                gx += gridStep
            }
            var gy = 0f
            while (gy < size.height) {
                drawLine(Color(0xFF28374B), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
                gy += gridStep
            }

            val scale = minOf(size.width, size.height) * 0.38f * zoom
            fun toScreen(node: GraphVisNode): Offset =
                Offset(
                    x = size.width / 2f + node.layoutX * scale + pan.x,
                    y = size.height / 2f + node.layoutY * scale + pan.y,
                )

            links.forEach { link ->
                val a = nodeById[link.sourceId] ?: return@forEach
                val b = nodeById[link.targetId] ?: return@forEach
                val pa = toScreen(a)
                val pb = toScreen(b)
                val alpha =
                    GraphColorPalette.alphaForEpoch(
                        link.epochMs,
                        visualization.timeMinMs,
                        visualization.timeMaxMs,
                        focused,
                    )
                val base = GraphColorPalette.relationColor(link.relation)
                val sel = link.key() == selectedLinkKey
                drawLine(
                    color = base.copy(alpha = if (sel) 1f else alpha * 0.88f),
                    start = pa,
                    end = pb,
                    strokeWidth = when {
                        sel -> 4f
                        link.relation == KnowledgeGraphBuilder.REL_AT_PLACE -> 3f
                        else -> 2f
                    },
                )
            }

            nodes.forEach { node ->
                val p = toScreen(node)
                val r = nodeRadius(node.type, node.signalCategory)
                val selected = node.id == selectedId || node.id == highlightedScanNodeId
                val alpha =
                    GraphColorPalette.alphaForEpoch(
                        node.epochMs,
                        visualization.timeMinMs,
                        visualization.timeMaxMs,
                        focused ||
                            (filterScanId != null &&
                                (filterScanId == node.linkedScanId || filterScanId in node.observedInScanIds)),
                    )
                val fill = node.color.copy(alpha = alpha)
                drawCircle(color = fill.copy(alpha = alpha * 0.35f), radius = r + 8f, center = p)
                drawCircle(color = fill, radius = r, center = p)
                drawCircle(
                    color = if (selected) Color(0xFF39FF14) else Color(0xFFE8ECF4).copy(alpha = alpha),
                    radius = r,
                    center = p,
                    style = Stroke(width = if (selected) 3f else 2f),
                )
            }
        }

    }
}

private fun fitZoomForNodes(nodes: List<GraphVisNode>, size: IntSize): Float {
    if (nodes.isEmpty() || size.width == 0 || size.height == 0) return 1f
    var minX = nodes.minOf { it.layoutX }
    var maxX = nodes.maxOf { it.layoutX }
    var minY = nodes.minOf { it.layoutY }
    var maxY = nodes.maxOf { it.layoutY }
    val extent = max(maxX - minX, maxY - minY).coerceAtLeast(0.15f)
    val target = minOf(size.width, size.height) * 0.72f
    val baseScale = minOf(size.width, size.height) * 0.38f
    return (target / (extent * baseScale)).coerceIn(0.55f, 2.2f)
}

private fun hitTestLink(
    offset: Offset,
    links: List<GraphVisLink>,
    nodeById: Map<String, GraphVisNode>,
    size: IntSize,
    pan: Offset,
    zoom: Float,
): GraphVisLink? {
    if (size.width == 0 || size.height == 0) return null
    val scale = minOf(size.width, size.height) * 0.38f * zoom
    fun toScreen(node: GraphVisNode): Offset =
        Offset(
            size.width / 2f + node.layoutX * scale + pan.x,
            size.height / 2f + node.layoutY * scale + pan.y,
        )
    var best: GraphVisLink? = null
    var bestDist = 28f
    links.forEach { link ->
        val a = nodeById[link.sourceId] ?: return@forEach
        val b = nodeById[link.targetId] ?: return@forEach
        val d = distanceToSegment(offset, toScreen(a), toScreen(b))
        if (d < bestDist) {
            bestDist = d
            best = link
        }
    }
    return best
}

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val apx = p.x - a.x
    val apy = p.y - a.y
    val ab2 = abx * abx + aby * aby
    if (ab2 < 0.001f) return sqrt(apx * apx + apy * apy)
    val t = ((apx * abx + apy * aby) / ab2).coerceIn(0f, 1f)
    val cx = a.x + t * abx
    val cy = a.y + t * aby
    val dx = p.x - cx
    val dy = p.y - cy
    return sqrt(dx * dx + dy * dy)
}

private fun hitTestNode(
    offset: Offset,
    nodes: List<GraphVisNode>,
    size: IntSize,
    pan: Offset,
    zoom: Float,
): GraphVisNode? {
    if (size.width == 0 || size.height == 0) return null
    val scale = minOf(size.width, size.height) * 0.38f * zoom
    var best: GraphVisNode? = null
    var bestDist = Float.MAX_VALUE
    nodes.forEach { node ->
        val p =
            Offset(
                size.width / 2f + node.layoutX * scale + pan.x,
                size.height / 2f + node.layoutY * scale + pan.y,
            )
        val r = nodeRadius(node.type, node.signalCategory) + 14f
        val dx = offset.x - p.x
        val dy = offset.y - p.y
        val d = sqrt(dx * dx + dy * dy)
        if (d <= r && d < bestDist) {
            bestDist = d
            best = node
        }
    }
    return best
}

private fun nodeRadius(type: String, signalCategory: String?): Float =
    when (type) {
        KnowledgeGraphBuilder.NODE_SCAN -> 12f
        KnowledgeGraphBuilder.NODE_PLACE -> 14f
        KnowledgeGraphBuilder.NODE_SIGNAL ->
            when (signalCategory?.uppercase()) {
                "SENSORS" -> 6f
                "NFC" -> 7f
                else -> 8f
            }
        "USER" -> 10f
        "DEVICE" -> 9f
        else -> 9f
    }
