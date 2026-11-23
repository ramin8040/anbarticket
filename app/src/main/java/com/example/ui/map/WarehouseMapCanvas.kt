package com.example.ui.map

import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import com.example.R
import com.example.util.PersianUtils.toPersianNumber
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

private data class MapPalette(
    val canvasBg: Color,
    val gridLine: Color,
    val slabTop: Color,
    val slabEdgeLeft: Color,
    val slabEdgeRight: Color,
    val floorStroke: Color,
    val wallBackLeft: Color,
    val wallBackRight: Color,
    val wallRib: Color,
    val wallKickplate: Color,
    val columnSteel: Color,
    val roofTruss: Color,
    val rowBayFill: Color,
    val rowBayHighlightFill: Color,
    val rowBayBorder: Color,
    val aisleDash: Color,
    val dockAccent: Color,
    val dockDark: Color,
    val woodTop: Color,
    val woodLeft: Color,
    val woodRight: Color,
    val woodSlot: Color,
    val ticketedTop: Color,
    val ticketedLeft: Color,
    val ticketedRight: Color,
    val countedTop: Color,
    val countedLeft: Color,
    val countedRight: Color,
    val emptyBadge: Color,
    val selectionGlow: Color,
    val labelBg: Color,
    val labelText: Color,
    val rowTagBg: Color,
    val rowTagText: Color,
    val dimLine: Color,
    val dimText: Color,
    val tapeLine: Color,
)

private fun mapPalette(isDark: Boolean): MapPalette = if (isDark) {
    MapPalette(
        canvasBg = Color(0xFF0D131A),
        gridLine = Color(0xFF1A2532),
        slabTop = Color(0xFF1A2430),
        slabEdgeLeft = Color(0xFF131B24),
        slabEdgeRight = Color(0xFF0E141C),
        floorStroke = Color(0xFF334155),
        wallBackLeft = Color(0xFF212E3D),
        wallBackRight = Color(0xFF192330),
        wallRib = Color(0xFF2E3F52),
        wallKickplate = Color(0xFF2B394A),
        columnSteel = Color(0xFF475569),
        roofTruss = Color(0x5564748B),
        rowBayFill = Color(0xFF223040),
        rowBayHighlightFill = Color(0x44F59E0B),
        rowBayBorder = Color(0xFF384B61),
        aisleDash = Color(0x88F59E0B),
        dockAccent = Color(0xFFF59E0B),
        dockDark = Color(0xFF1E293B),
        woodTop = Color(0xFFC28E56),
        woodLeft = Color(0xFF96673A),
        woodRight = Color(0xFF744D29),
        woodSlot = Color(0xFF3D2714),
        ticketedTop = Color(0xFF34D399),
        ticketedLeft = Color(0xFF10B981),
        ticketedRight = Color(0xFF047857),
        countedTop = Color(0xFFFBBF24),
        countedLeft = Color(0xFFD97706),
        countedRight = Color(0xFF92400E),
        emptyBadge = Color(0xFF64748B),
        selectionGlow = Color(0xFF38BDF8),
        labelBg = Color(0xDD0F172A),
        labelText = Color(0xFFF8FAFC),
        rowTagBg = Color(0xFF273649),
        rowTagText = Color(0xFFE2E8F0),
        dimLine = Color(0xFF64748B),
        dimText = Color(0xFF94A3B8),
        tapeLine = Color(0x66FFFFFF),
    )
} else {
    MapPalette(
        canvasBg = Color(0xFFF1EFE8),
        gridLine = Color(0xFFE1DDD0),
        slabTop = Color(0xFFFCFBFA),
        slabEdgeLeft = Color(0xFFCBD5E1),
        slabEdgeRight = Color(0xFF94A3B8),
        floorStroke = Color(0xFF334155),
        wallBackLeft = Color(0xFFE2E8F0),
        wallBackRight = Color(0xFFCBD5E1),
        wallRib = Color(0xFF94A3B8),
        wallKickplate = Color(0xFF64748B),
        columnSteel = Color(0xFF334155),
        roofTruss = Color(0x55334155),
        rowBayFill = Color(0xFFECEFF4),
        rowBayHighlightFill = Color(0x40D97706),
        rowBayBorder = Color(0xFFCBD5E1),
        aisleDash = Color(0xAAD97706),
        dockAccent = Color(0xFFD97706),
        dockDark = Color(0xFF1E293B),
        woodTop = Color(0xFFD4A373),
        woodLeft = Color(0xFFB07D4F),
        woodRight = Color(0xFF8C5E35),
        woodSlot = Color(0xFF4A311B),
        ticketedTop = Color(0xFF10B981),
        ticketedLeft = Color(0xFF059669),
        ticketedRight = Color(0xFF065F46),
        countedTop = Color(0xFFF59E0B),
        countedLeft = Color(0xFFD97706),
        countedRight = Color(0xFFB45309),
        emptyBadge = Color(0xFF475569),
        selectionGlow = Color(0xFF0284C7),
        labelBg = Color(0xE61E293B),
        labelText = Color(0xFFFFFFFF),
        rowTagBg = Color(0xFF1E293B),
        rowTagText = Color(0xFFFFFFFF),
        dimLine = Color(0xFF475569),
        dimText = Color(0xFF334155),
        tapeLine = Color(0x88FFFFFF),
    )
}

@Composable
fun WarehouseMapCanvas(
    hallData: WarehouseHallMapData,
    viewMode: MapViewMode,
    isDarkTheme: Boolean,
    showLabels: Boolean,
    zoom: Float,
    pan: Offset,
    selection: MapSelection?,
    onTransformChange: (newZoom: Float, newPan: Offset) -> Unit,
    onSelect: (MapSelection?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val paddingPx = with(density) { 26.dp.toPx() }
    val palette = remember(isDarkTheme) { mapPalette(isDarkTheme) }

    val vazirTypeface = remember(context) {
        try {
            ResourcesCompat.getFont(context, R.font.vazirmatn) ?: Typeface.DEFAULT_BOLD
        } catch (_: Exception) {
            Typeface.DEFAULT_BOLD
        }
    }

    val layout = remember(hallData) {
        WarehouseMapGeometry.computeHallLayout(hallData)
    }

    val isoRawBounds = remember(layout) {
        WarehouseMapGeometry.computeIsoRawBounds(layout)
    }

    val topDownRawBounds = remember(layout) {
        Rect(
            left = -0.85f,
            top = -0.95f,
            right = layout.hallWidth + 0.85f,
            bottom = layout.hallDepth + 0.95f,
        )
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val latestZoom by rememberUpdatedState(zoom)
    val latestPan by rememberUpdatedState(pan)
    val latestViewMode by rememberUpdatedState(viewMode)
    val latestLayout by rememberUpdatedState(layout)
    val latestIsoBounds by rememberUpdatedState(isoRawBounds)
    val latestTopDownBounds by rememberUpdatedState(topDownRawBounds)
    val latestOnTransform by rememberUpdatedState(onTransformChange)
    val latestOnSelect by rememberUpdatedState(onSelect)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .testTag("warehouse_map_canvas")
            .onSizeChanged { canvasSize = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, panChange, zoomChange, _ ->
                    val nextZoom = (latestZoom * zoomChange).coerceIn(0.55f, 3.2f)
                    val maxPan = max(canvasSize.width, canvasSize.height).toFloat().coerceAtLeast(300f) * nextZoom
                    val nextPan = Offset(
                        x = (latestPan.x + panChange.x).coerceIn(-maxPan, maxPan),
                        y = (latestPan.y + panChange.y).coerceIn(-maxPan, maxPan),
                    )
                    latestOnTransform(nextZoom, nextPan)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { tapOffset ->
                    if (canvasSize.width == 0 || canvasSize.height == 0) return@detectTapGestures
                    val bounds = if (latestViewMode == MapViewMode.ISOMETRIC) {
                        latestIsoBounds
                    } else {
                        latestTopDownBounds
                    }
                    val transform = WarehouseMapGeometry.computeViewportTransform(
                        rawBounds = bounds,
                        canvasWidth = canvasSize.width.toFloat(),
                        canvasHeight = canvasSize.height.toFloat(),
                        paddingPx = paddingPx,
                        userZoom = latestZoom,
                        userPan = latestPan,
                    )
                    val hit = if (latestViewMode == MapViewMode.ISOMETRIC) {
                        WarehouseMapGeometry.hitTestIsometric(tapOffset, latestLayout, transform)
                    } else {
                        WarehouseMapGeometry.hitTestTopDown(tapOffset, latestLayout, transform)
                    }
                    latestOnSelect(hit)
                }
            },
    ) {
        drawRect(color = palette.canvasBg, size = size)
        drawBlueprintGrid(palette = palette)

        when (viewMode) {
            MapViewMode.ISOMETRIC -> {
                val transform = WarehouseMapGeometry.computeViewportTransform(
                    rawBounds = isoRawBounds,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    paddingPx = paddingPx,
                    userZoom = zoom,
                    userPan = pan,
                )
                drawIsometricWarehouseHall(
                    hallData = hallData,
                    layout = layout,
                    transform = transform,
                    palette = palette,
                    selection = selection,
                    showLabels = showLabels,
                    typeface = vazirTypeface,
                )
            }
            MapViewMode.TOP_DOWN_2D -> {
                val transform = WarehouseMapGeometry.computeViewportTransform(
                    rawBounds = topDownRawBounds,
                    canvasWidth = size.width,
                    canvasHeight = size.height,
                    paddingPx = paddingPx,
                    userZoom = zoom,
                    userPan = pan,
                )
                drawTopDownWarehousePlan(
                    hallData = hallData,
                    layout = layout,
                    transform = transform,
                    palette = palette,
                    selection = selection,
                    showLabels = showLabels,
                    typeface = vazirTypeface,
                )
            }
        }
    }
}

private fun DrawScope.drawBlueprintGrid(palette: MapPalette) {
    val step = 28.dp.toPx()
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = palette.gridLine,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f,
        )
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = palette.gridLine,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
        y += step
    }
}

// ============================================================================
// ISOMETRIC 3D WAREHOUSE HALL RENDERER
// ============================================================================

private fun DrawScope.drawIsometricWarehouseHall(
    hallData: WarehouseHallMapData,
    layout: WarehouseMapGeometry.HallWorldLayout,
    transform: WarehouseMapGeometry.ViewportTransform,
    palette: MapPalette,
    selection: MapSelection?,
    showLabels: Boolean,
    typeface: Typeface,
) {
    fun iso(wx: Float, wy: Float, wz: Float = 0f): Offset =
        transform.rawToScreen(WarehouseMapGeometry.projectIsoRaw(wx, wy, wz))

    val w = layout.hallWidth
    val d = layout.hallDepth
    val wallH = WarehouseMapGeometry.HALL_WALL_HEIGHT
    val slabThickness = 0.24f

    // 1. Raised 3D Concrete Hall Floor Slab (edges + top)
    drawQuad(
        pts = listOf(iso(0f, d, 0f), iso(w, d, 0f), iso(w, d, -slabThickness), iso(0f, d, -slabThickness)),
        fill = palette.slabEdgeLeft,
        stroke = palette.floorStroke,
        strokeWidth = 1.5f,
    )
    drawQuad(
        pts = listOf(iso(w, 0f, 0f), iso(w, d, 0f), iso(w, d, -slabThickness), iso(w, 0f, -slabThickness)),
        fill = palette.slabEdgeRight,
        stroke = palette.floorStroke,
        strokeWidth = 1.5f,
    )
    drawQuad(
        pts = listOf(iso(0f, 0f, 0f), iso(w, 0f, 0f), iso(w, d, 0f), iso(0f, d, 0f)),
        fill = palette.slabTop,
        stroke = palette.floorStroke,
        strokeWidth = 2f,
    )

    // Subtle floor concrete expansion joints
    val jointStep = 1.5f
    var jx = jointStep
    while (jx < w) {
        drawLine(palette.gridLine, iso(jx, 0f), iso(jx, d), strokeWidth = 1f)
        jx += jointStep
    }
    var jy = jointStep
    while (jy < d) {
        drawLine(palette.gridLine, iso(0f, jy), iso(w, jy), strokeWidth = 1f)
        jy += jointStep
    }

    // 2. Cutaway Industrial Hall Walls (Back-Left along y=0, Back-Right along x=0)
    // Back-Left Wall (x: 0..w, y=0)
    drawQuad(
        pts = listOf(iso(0f, 0f, 0f), iso(w, 0f, 0f), iso(w, 0f, wallH), iso(0f, 0f, wallH)),
        fill = palette.wallBackLeft,
        stroke = palette.floorStroke,
        strokeWidth = 1.8f,
    )
    // Concrete wainscot kickplate along Back-Left Wall
    drawQuad(
        pts = listOf(iso(0f, 0f, 0f), iso(w, 0f, 0f), iso(w, 0f, 0.35f), iso(0f, 0f, 0.35f)),
        fill = palette.wallKickplate,
    )
    // Back-Right Wall (x=0, y: 0..d)
    drawQuad(
        pts = listOf(iso(0f, 0f, 0f), iso(0f, d, 0f), iso(0f, d, wallH), iso(0f, 0f, wallH)),
        fill = palette.wallBackRight,
        stroke = palette.floorStroke,
        strokeWidth = 1.8f,
    )
    // Concrete kickplate along Back-Right Wall
    drawQuad(
        pts = listOf(iso(0f, 0f, 0f), iso(0f, d, 0f), iso(0f, d, 0.35f), iso(0f, 0f, 0.35f)),
        fill = palette.wallKickplate.copy(alpha = 0.85f),
    )

    // Vertical corrugated steel ribs + structural I-beam columns on back walls
    val ribStep = 0.65f
    var rx = ribStep
    while (rx < w) {
        drawLine(palette.wallRib.copy(alpha = 0.45f), iso(rx, 0f, 0.35f), iso(rx, 0f, wallH), strokeWidth = 1f)
        rx += ribStep
    }
    var ry = ribStep
    while (ry < d) {
        drawLine(palette.wallRib.copy(alpha = 0.45f), iso(0f, ry, 0.35f), iso(0f, ry, wallH), strokeWidth = 1f)
        ry += ribStep
    }

    // Steel structural columns & pitched warehouse roof truss hints
    val trussCount = max(2, (d / 2.2f).toInt())
    val ridgePeakZ = wallH + 0.55f
    for (i in 0..trussCount) {
        val ty = (d * i) / trussCount
        // Back-right wall steel column
        drawLine(palette.columnSteel, iso(0f, ty, 0f), iso(0f, ty, wallH), strokeWidth = 3.2f)
        // Subtle dashed steel roof truss arch (cutaway hint)
        if (i == 0 || i == trussCount) {
            drawLine(
                color = palette.roofTruss,
                start = iso(0f, ty, wallH),
                end = iso(w * 0.5f, ty, ridgePeakZ),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
            )
            drawLine(
                color = palette.roofTruss,
                start = iso(w * 0.5f, ty, ridgePeakZ),
                end = iso(w, ty, wallH),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 7f)),
            )
        }
    }
    // Roof ridge line
    drawLine(
        color = palette.roofTruss,
        start = iso(w * 0.5f, 0f, ridgePeakZ),
        end = iso(w * 0.5f, d, ridgePeakZ),
        strokeWidth = 1.6f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
    )
    // Corner front steel column stubs
    drawLine(palette.columnSteel, iso(w, 0f, 0f), iso(w, 0f, wallH), strokeWidth = 3.2f)
    drawLine(palette.columnSteel, iso(0f, d, 0f), iso(0f, d, wallH), strokeWidth = 3.2f)

    // 3. Loading Dock Entrance Strip at Front (y = d)
    val dockHalf = min(1.35f, w * 0.28f)
    val dockCenterX = w * 0.5f
    val dockX1 = dockCenterX - dockHalf
    val dockX2 = dockCenterX + dockHalf
    val dockDepth = 0.42f
    drawQuad(
        pts = listOf(iso(dockX1, d - dockDepth, 0.01f), iso(dockX2, d - dockDepth, 0.01f), iso(dockX2, d, 0.01f), iso(dockX1, d, 0.01f)),
        fill = palette.dockAccent.copy(alpha = 0.25f),
        stroke = palette.dockAccent,
        strokeWidth = 1.8f,
    )
    // Hazard stripes at entrance threshold
    val stripes = 6
    val stripeW = (dockX2 - dockX1) / stripes
    for (s in 0 until stripes step 2) {
        val sx1 = dockX1 + s * stripeW
        val sx2 = sx1 + stripeW
        drawQuad(
            pts = listOf(iso(sx1, d - 0.14f, 0.015f), iso(sx2, d - 0.14f, 0.015f), iso(sx2, d, 0.015f), iso(sx1, d, 0.015f)),
            fill = palette.dockAccent,
        )
    }

    // 4. Row Bays & Forklift Aisles on Floor
    val selectedRowId = when (selection) {
        is MapSelection.Row -> selection.row.id
        is MapSelection.Pallet -> selection.pallet.rowId
        null -> null
    }

    layout.rowRects.forEachIndexed { idx, rb ->
        val isRowHighlighted = selectedRowId == rb.row.id
        drawQuad(
            pts = listOf(iso(rb.xStart, rb.yStart, 0.01f), iso(rb.xEnd, rb.yStart, 0.01f), iso(rb.xEnd, rb.yEnd, 0.01f), iso(rb.xStart, rb.yEnd, 0.01f)),
            fill = if (isRowHighlighted) palette.rowBayHighlightFill else palette.rowBayFill,
            stroke = if (isRowHighlighted) palette.dockAccent else palette.rowBayBorder,
            strokeWidth = if (isRowHighlighted) 2.2f else 1.2f,
        )

        // Forklift aisle dashed centerline between this row and next row
        if (idx < layout.rowRects.lastIndex) {
            val aisleCenterX = rb.xEnd + WarehouseMapGeometry.AISLE_WIDTH * 0.5f
            drawLine(
                color = palette.aisleDash,
                start = iso(aisleCenterX, rb.yStart, 0.015f),
                end = iso(aisleCenterX, rb.yEnd + 0.4f, 0.015f),
                strokeWidth = 1.6f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
            )
        }
    }

    // 5. Draw 3D Pallets & Stacked Cartons in Painter's Order (Back-to-Front)
    val backToFront = layout.palletRects.sortedBy { it.pallet.rowIndex + it.pallet.slotIndex }
    val selectedPalletId = (selection as? MapSelection.Pallet)?.pallet?.id

    for (pb in backToFront) {
        val isSelected = selectedPalletId == pb.pallet.id
        drawIsoPallet3D(
            pb = pb,
            iso = ::iso,
            palette = palette,
            isSelected = isSelected,
        )
    }

    // 6. Crisp Persian Labels (Drawn on top of 3D geometry for total legibility)
    val fontScale = (transform.scale / 58f).coerceIn(0.75f, 1.35f)
    if (showLabels) {
        // Warehouse Hall Banner Plaque on back wall
        val hallTitlePos = iso(w * 0.5f, 0f, wallH * 0.62f)
        drawCanvasPillText(
            text = "سوله: ${hallData.warehouseName}",
            center = hallTitlePos,
            bgColor = palette.rowTagBg.copy(alpha = 0.92f),
            textColor = palette.rowTagText,
            textSizePx = 12.5f * density * fontScale,
            typeface = typeface,
            borderColor = palette.dockAccent,
        )

        // Entrance gate label
        val dockLabelPos = iso(dockCenterX, d + 0.34f, 0f)
        drawCanvasPillText(
            text = "ورودی سوله",
            center = dockLabelPos,
            bgColor = palette.dockDark,
            textColor = Color(0xFFFDE68A),
            textSizePx = 10.5f * density * fontScale,
            typeface = typeface,
            borderColor = palette.dockAccent,
        )

        // Row labels at front of each bay
        for (rb in layout.rowRects) {
            val tagPos = iso(rb.centerX, rb.yEnd + 0.36f, 0.02f)
            val isRowSelected = selectedRowId == rb.row.id
            drawCanvasPillText(
                text = rb.row.name.take(14),
                center = tagPos,
                bgColor = if (isRowSelected) palette.dockAccent else palette.rowTagBg,
                textColor = if (isRowSelected) Color.White else palette.rowTagText,
                textSizePx = 10.5f * density * fontScale,
                typeface = typeface,
            )
        }

        // Pallet labels floating neatly above each pallet top
        for (pb in backToFront) {
            val isSelected = selectedPalletId == pb.pallet.id
            if (isSelected || transform.scale >= 28f) {
                val topCenter = iso(pb.centerX, pb.centerY, pb.totalHeightZ + 0.14f)
                val shortLabel = pb.pallet.name.take(10)
                val label = if (pb.pallet.effectiveCartonCount > 0 && transform.scale >= 44f) {
                    "$shortLabel (${pb.pallet.effectiveCartonCount.toPersianNumber()})"
                } else {
                    shortLabel
                }
                drawCanvasPillText(
                    text = label,
                    center = topCenter,
                    bgColor = if (isSelected) palette.selectionGlow else palette.labelBg,
                    textColor = if (isSelected) Color.White else palette.labelText,
                    textSizePx = (if (isSelected) 11f else 9.5f) * density * fontScale,
                    typeface = typeface,
                    borderColor = if (isSelected) Color.White else null,
                )
            }
        }
    }
}

private fun DrawScope.drawIsoPallet3D(
    pb: WarehouseMapGeometry.PalletWorldBounds,
    iso: (Float, Float, Float) -> Offset,
    palette: MapPalette,
    isSelected: Boolean,
) {
    val x1 = pb.xStart
    val x2 = pb.xEnd
    val y1 = pb.yStart
    val y2 = pb.yEnd
    val baseH = WarehouseMapGeometry.PALLET_BASE_HEIGHT

    // 1. Soft floor shadow slightly offset forward-right
    drawQuad(
        pts = listOf(
            iso(x1 + 0.07f, y1 + 0.07f, 0.015f),
            iso(x2 + 0.09f, y1 + 0.07f, 0.015f),
            iso(x2 + 0.09f, y2 + 0.09f, 0.015f),
            iso(x1 + 0.07f, y2 + 0.09f, 0.015f),
        ),
        fill = Color(0x33000000),
    )

    // 2. Wooden Pallet Base Prism (0 -> baseH)
    drawIsoPrism(
        x1 = x1, x2 = x2, y1 = y1, y2 = y2, z1 = 0f, z2 = baseH,
        iso = iso,
        topColor = palette.woodTop,
        leftFrontColor = palette.woodLeft,
        rightFrontColor = palette.woodRight,
        strokeColor = palette.woodSlot.copy(alpha = 0.65f),
        strokeWidth = 1f,
    )

    // Forklift fork-entry cutouts on front-left edge (y = y2)
    val slotY = y2
    val wSpan = x2 - x1
    drawQuad(
        pts = listOf(
            iso(x1 + wSpan * 0.16f, slotY, 0.01f),
            iso(x1 + wSpan * 0.40f, slotY, 0.01f),
            iso(x1 + wSpan * 0.40f, slotY, baseH * 0.65f),
            iso(x1 + wSpan * 0.16f, slotY, baseH * 0.65f),
        ),
        fill = palette.woodSlot,
    )
    drawQuad(
        pts = listOf(
            iso(x1 + wSpan * 0.60f, slotY, 0.01f),
            iso(x1 + wSpan * 0.84f, slotY, 0.01f),
            iso(x1 + wSpan * 0.84f, slotY, baseH * 0.65f),
            iso(x1 + wSpan * 0.60f, slotY, baseH * 0.65f),
        ),
        fill = palette.woodSlot,
    )

    // 3. Stacked Carton Tiers (1..3) based on actual pallet carton/item count
    val tiers = pb.pallet.stackTiers
    if (tiers > 0) {
        val (topColor, leftColor, rightColor) = when (pb.pallet.status) {
            PalletMapStatus.TICKETED -> Triple(palette.ticketedTop, palette.ticketedLeft, palette.ticketedRight)
            PalletMapStatus.COUNTED_NO_TICKET -> Triple(palette.countedTop, palette.countedLeft, palette.countedRight)
            PalletMapStatus.EMPTY -> Triple(palette.woodTop, palette.woodLeft, palette.woodRight)
        }
        val inset = 0.065f
        val tierH = WarehouseMapGeometry.TIER_HEIGHT
        for (t in 0 until tiers) {
            val zBottom = baseH + t * tierH
            val zTop = zBottom + tierH - 0.018f
            drawIsoPrism(
                x1 = x1 + inset,
                x2 = x2 - inset,
                y1 = y1 + inset,
                y2 = y2 - inset,
                z1 = zBottom,
                z2 = zTop,
                iso = iso,
                topColor = topColor,
                leftFrontColor = leftColor,
                rightFrontColor = rightColor,
                strokeColor = Color(0x44000000),
                strokeWidth = 1.1f,
            )
        }

        // Packaging seam / tape stripe across top face of topmost tier
        val finalTopZ = baseH + tiers * tierH - 0.018f
        val midY = (y1 + y2) * 0.5f
        drawLine(
            color = palette.tapeLine,
            start = iso(x1 + inset + 0.04f, midY, finalTopZ),
            end = iso(x2 - inset - 0.04f, midY, finalTopZ),
            strokeWidth = 2.2f,
        )
        // Cross box divider line if multiple cartons
        if (pb.pallet.effectiveCartonCount >= 2) {
            val midX = (x1 + x2) * 0.5f
            drawLine(
                color = Color(0x38000000),
                start = iso(midX, y1 + inset, finalTopZ),
                end = iso(midX, y2 - inset, finalTopZ),
                strokeWidth = 1.2f,
            )
        }
    } else {
        // Draw wooden deck slats on empty pallet top
        val slatCount = 3
        for (s in 1..slatCount) {
            val sy = y1 + (y2 - y1) * s / (slatCount + 1)
            drawLine(
                color = palette.woodSlot.copy(alpha = 0.45f),
                start = iso(x1 + 0.03f, sy, baseH),
                end = iso(x2 - 0.03f, sy, baseH),
                strokeWidth = 1.2f,
            )
        }
    }

    // 4. Selection Highlight Outline Prism
    if (isSelected) {
        val zTop = pb.totalHeightZ
        val topOutline = Path().apply {
            val p0 = iso(x1 - 0.03f, y1 - 0.03f, zTop + 0.02f)
            val p1 = iso(x2 + 0.03f, y1 - 0.03f, zTop + 0.02f)
            val p2 = iso(x2 + 0.03f, y2 + 0.03f, zTop + 0.02f)
            val p3 = iso(x1 - 0.03f, y2 + 0.03f, zTop + 0.02f)
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
            lineTo(p3.x, p3.y)
            close()
        }
        drawPath(
            path = topOutline,
            color = palette.selectionGlow,
            style = Stroke(width = 3.2f, join = StrokeJoin.Round),
        )
        val baseOutline = Path().apply {
            val p0 = iso(x1 - 0.04f, y2 + 0.04f, 0.01f)
            val p1 = iso(x2 + 0.04f, y2 + 0.04f, 0.01f)
            val p2 = iso(x2 + 0.04f, y1 - 0.04f, 0.01f)
            moveTo(p0.x, p0.y)
            lineTo(p1.x, p1.y)
            lineTo(p2.x, p2.y)
        }
        drawPath(
            path = baseOutline,
            color = palette.selectionGlow.copy(alpha = 0.85f),
            style = Stroke(width = 2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.drawIsoPrism(
    x1: Float,
    x2: Float,
    y1: Float,
    y2: Float,
    z1: Float,
    z2: Float,
    iso: (Float, Float, Float) -> Offset,
    topColor: Color,
    leftFrontColor: Color,
    rightFrontColor: Color,
    strokeColor: Color,
    strokeWidth: Float,
) {
    // Left-Front face (y = y2)
    drawQuad(
        pts = listOf(iso(x1, y2, z2), iso(x2, y2, z2), iso(x2, y2, z1), iso(x1, y2, z1)),
        fill = leftFrontColor,
        stroke = strokeColor,
        strokeWidth = strokeWidth,
    )
    // Right-Front face (x = x2)
    drawQuad(
        pts = listOf(iso(x2, y1, z2), iso(x2, y2, z2), iso(x2, y2, z1), iso(x2, y1, z1)),
        fill = rightFrontColor,
        stroke = strokeColor,
        strokeWidth = strokeWidth,
    )
    // Top face (z = z2)
    drawQuad(
        pts = listOf(iso(x1, y1, z2), iso(x2, y1, z2), iso(x2, y2, z2), iso(x1, y2, z2)),
        fill = topColor,
        stroke = strokeColor,
        strokeWidth = strokeWidth,
    )
}

// ============================================================================
// 2D TOP-DOWN ARCHITECTURAL PLAN RENDERER
// ============================================================================

private fun DrawScope.drawTopDownWarehousePlan(
    hallData: WarehouseHallMapData,
    layout: WarehouseMapGeometry.HallWorldLayout,
    transform: WarehouseMapGeometry.ViewportTransform,
    palette: MapPalette,
    selection: MapSelection?,
    showLabels: Boolean,
    typeface: Typeface,
) {
    fun pt(wx: Float, wy: Float): Offset = transform.rawToScreen(Offset(wx, wy))
    fun sz(w: Float, h: Float): Size = Size(w * transform.scale, h * transform.scale)

    val w = layout.hallWidth
    val d = layout.hallDepth
    val hallTopLeft = pt(0f, 0f)
    val hallSize = sz(w, d)

    // 1. Hall Floor Interior & Architectural Outer Wall
    drawRoundRect(
        color = palette.slabTop,
        topLeft = hallTopLeft,
        size = hallSize,
        cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx()),
    )

    // Subtle interior bay grid lines
    val step = 1.25f
    var gx = step
    while (gx < w) {
        drawLine(palette.gridLine, pt(gx, 0f), pt(gx, d), strokeWidth = 1f)
        gx += step
    }
    var gy = step
    while (gy < d) {
        drawLine(palette.gridLine, pt(0f, gy), pt(w, gy), strokeWidth = 1f)
        gy += step
    }

    // Double architectural perimeter wall
    drawRect(
        color = palette.floorStroke,
        topLeft = hallTopLeft,
        size = hallSize,
        style = Stroke(width = 4.2f),
    )
    val insetWall = 0.08f
    drawRect(
        color = palette.floorStroke.copy(alpha = 0.45f),
        topLeft = pt(insetWall, insetWall),
        size = sz(w - insetWall * 2f, d - insetWall * 2f),
        style = Stroke(width = 1.2f),
    )

    // Structural steel hall columns along perimeter (■ squares)
    val colHalf = 0.11f
    val colPoints = mutableListOf(
        Offset(0f, 0f), Offset(w, 0f), Offset(0f, d), Offset(w, d),
    )
    val sideCols = max(1, (d / 2.2f).toInt())
    for (i in 1 until sideCols) {
        val cy = d * i / sideCols
        colPoints.add(Offset(0f, cy))
        colPoints.add(Offset(w, cy))
    }
    val topCols = max(1, (w / 2.6f).toInt())
    for (i in 1 until topCols) {
        val cx = w * i / topCols
        colPoints.add(Offset(cx, 0f))
    }
    for (cp in colPoints) {
        drawRect(
            color = palette.columnSteel,
            topLeft = pt(cp.x - colHalf, cp.y - colHalf),
            size = sz(colHalf * 2f, colHalf * 2f),
        )
    }

    // 2. Front Loading Dock Gate Opening (bottom wall y = d)
    val dockHalf = min(1.35f, w * 0.28f)
    val dockCenterX = w * 0.5f
    val dockX1 = dockCenterX - dockHalf
    val dockX2 = dockCenterX + dockHalf
    drawRect(
        color = palette.dockAccent.copy(alpha = 0.22f),
        topLeft = pt(dockX1, d - 0.36f),
        size = sz(dockX2 - dockX1, 0.36f),
    )
    drawLine(
        color = palette.dockAccent,
        start = pt(dockX1, d),
        end = pt(dockX2, d),
        strokeWidth = 6f,
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
    )

    // 3. Exterior Architectural Dimension Lines (Width: rows, Depth: slots)
    val dimOffset = 0.45f
    // Top dimension line
    drawLine(palette.dimLine, pt(0f, -dimOffset), pt(w, -dimOffset), strokeWidth = 1.4f)
    drawLine(palette.dimLine, pt(0f, -dimOffset - 0.12f), pt(0f, -dimOffset + 0.12f), strokeWidth = 1.4f)
    drawLine(palette.dimLine, pt(w, -dimOffset - 0.12f), pt(w, -dimOffset + 0.12f), strokeWidth = 1.4f)

    // Left dimension line
    drawLine(palette.dimLine, pt(-dimOffset, 0f), pt(-dimOffset, d), strokeWidth = 1.4f)
    drawLine(palette.dimLine, pt(-dimOffset - 0.12f, 0f), pt(-dimOffset + 0.12f, 0f), strokeWidth = 1.4f)
    drawLine(palette.dimLine, pt(-dimOffset - 0.12f, d), pt(-dimOffset + 0.12f, d), strokeWidth = 1.4f)

    // 4. Row Zones & Forklift Aisles
    val selectedRowId = when (selection) {
        is MapSelection.Row -> selection.row.id
        is MapSelection.Pallet -> selection.pallet.rowId
        null -> null
    }
    val selectedPalletId = (selection as? MapSelection.Pallet)?.pallet?.id

    layout.rowRects.forEachIndexed { idx, rb ->
        val isRowHighlighted = selectedRowId == rb.row.id
        val tl = pt(rb.xStart, rb.yStart)
        val baySize = sz(rb.xEnd - rb.xStart, rb.yEnd - rb.yStart)
        drawRoundRect(
            color = if (isRowHighlighted) palette.rowBayHighlightFill else palette.rowBayFill,
            topLeft = tl,
            size = baySize,
            cornerRadius = CornerRadius(6f, 6f),
        )
        drawRoundRect(
            color = if (isRowHighlighted) palette.dockAccent else palette.rowBayBorder,
            topLeft = tl,
            size = baySize,
            cornerRadius = CornerRadius(6f, 6f),
            style = Stroke(width = if (isRowHighlighted) 2.5f else 1.4f),
        )

        // Forklift aisle dashed centerline
        if (idx < layout.rowRects.lastIndex) {
            val aisleX = rb.xEnd + WarehouseMapGeometry.AISLE_WIDTH * 0.5f
            drawLine(
                color = palette.aisleDash,
                start = pt(aisleX, rb.yStart),
                end = pt(aisleX, rb.yEnd + 0.35f),
                strokeWidth = 1.8f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
            )
        }
    }

    // 5. Top-Down Detailed Pallet Footprints
    for (pb in layout.palletRects) {
        val isSelected = selectedPalletId == pb.pallet.id
        val pTopLeft = pt(pb.xStart, pb.yStart)
        val pW = (pb.xEnd - pb.xStart) * transform.scale
        val pH = (pb.yEnd - pb.yStart) * transform.scale

        // Wooden pallet base footprint
        drawRoundRect(
            color = palette.woodTop,
            topLeft = pTopLeft,
            size = Size(pW, pH),
            cornerRadius = CornerRadius(4f, 4f),
        )
        // Wooden pallet slat lines
        for (slat in 1..2) {
            val sy = pTopLeft.y + pH * slat / 3f
            drawLine(
                color = palette.woodSlot.copy(alpha = 0.4f),
                start = Offset(pTopLeft.x + 2f, sy),
                end = Offset(pTopLeft.x + pW - 2f, sy),
                strokeWidth = 1.2f,
            )
        }

        // Status-coded cargo top inset
        val insetPx = min(pW, pH) * 0.08f
        if (pb.pallet.stackTiers > 0) {
            val cargoFill = when (pb.pallet.status) {
                PalletMapStatus.TICKETED -> palette.ticketedTop
                PalletMapStatus.COUNTED_NO_TICKET -> palette.countedTop
                PalletMapStatus.EMPTY -> palette.woodTop
            }
            val cargoBorder = when (pb.pallet.status) {
                PalletMapStatus.TICKETED -> palette.ticketedRight
                PalletMapStatus.COUNTED_NO_TICKET -> palette.countedRight
                PalletMapStatus.EMPTY -> palette.woodRight
            }
            val cargoTL = Offset(pTopLeft.x + insetPx, pTopLeft.y + insetPx)
            val cargoW = (pW - insetPx * 2f).coerceAtLeast(4f)
            val cargoH = (pH - insetPx * 2f).coerceAtLeast(4f)
            drawRoundRect(
                color = cargoFill,
                topLeft = cargoTL,
                size = Size(cargoW, cargoH),
                cornerRadius = CornerRadius(3f, 3f),
            )
            drawRoundRect(
                color = cargoBorder,
                topLeft = cargoTL,
                size = Size(cargoW, cargoH),
                cornerRadius = CornerRadius(3f, 3f),
                style = Stroke(width = 1.4f),
            )

            // Miniature proportional carton grid subdivisions on pallet top
            val visualBoxes = pb.pallet.effectiveCartonCount.coerceIn(1, 9)
            if (visualBoxes > 1 && cargoW > 22f) {
                val cols = ceil(sqrt(visualBoxes.toDouble())).toInt().coerceAtLeast(2)
                val rows = ceil(visualBoxes.toDouble() / cols).toInt().coerceAtLeast(1)
                for (c in 1 until cols) {
                    val lx = cargoTL.x + cargoW * c / cols
                    drawLine(Color(0x44000000), Offset(lx, cargoTL.y), Offset(lx, cargoTL.y + cargoH), strokeWidth = 1f)
                }
                for (r in 1 until rows) {
                    val ly = cargoTL.y + cargoH * r / rows
                    drawLine(Color(0x44000000), Offset(cargoTL.x, ly), Offset(cargoTL.x + cargoW, ly), strokeWidth = 1f)
                }
            }
        } else {
            drawRoundRect(
                color = palette.woodRight,
                topLeft = pTopLeft,
                size = Size(pW, pH),
                cornerRadius = CornerRadius(4f, 4f),
                style = Stroke(width = 1.4f),
            )
        }

        // Ticket captured indicator dot in corner
        if (pb.pallet.ticketCount > 0) {
            drawCircle(
                color = Color.White,
                radius = (min(pW, pH) * 0.11f).coerceIn(3f, 7f),
                center = Offset(pTopLeft.x + pW - insetPx * 1.9f, pTopLeft.y + insetPx * 1.9f),
            )
        }

        // Selection ring
        if (isSelected) {
            drawRoundRect(
                color = palette.selectionGlow,
                topLeft = Offset(pTopLeft.x - 3.5f, pTopLeft.y - 3.5f),
                size = Size(pW + 7f, pH + 7f),
                cornerRadius = CornerRadius(6f, 6f),
                style = Stroke(width = 3.5f),
            )
        }
    }

    // 6. Crisp Persian Labels for 2D Blueprint
    val fontScale = (transform.scale / 64f).coerceIn(0.75f, 1.35f)
    if (showLabels) {
        // Blueprint header dimension summary
        val topHeader = "${hallData.warehouseName}  •  ${hallData.rows.size.toPersianNumber()} ردیف  •  ${hallData.totalPallets.toPersianNumber()} پالت"
        drawCanvasPillText(
            text = topHeader,
            center = pt(w * 0.5f, -dimOffset),
            bgColor = palette.rowTagBg,
            textColor = palette.rowTagText,
            textSizePx = 11f * density * fontScale,
            typeface = typeface,
            borderColor = palette.dockAccent,
        )

        // Entrance gate label
        drawCanvasPillText(
            text = "درب ورودی / بارگیری سوله",
            center = pt(dockCenterX, d + 0.38f),
            bgColor = palette.dockDark,
            textColor = Color(0xFFFDE68A),
            textSizePx = 10.5f * density * fontScale,
            typeface = typeface,
            borderColor = palette.dockAccent,
        )

        // Row labels at top and front of each row bay
        for (rb in layout.rowRects) {
            val isRowSelected = selectedRowId == rb.row.id
            drawCanvasPillText(
                text = rb.row.name.take(14),
                center = pt(rb.centerX, rb.yStart - 0.36f),
                bgColor = if (isRowSelected) palette.dockAccent else palette.rowTagBg,
                textColor = if (isRowSelected) Color.White else palette.rowTagText,
                textSizePx = 10.2f * density * fontScale,
                typeface = typeface,
            )
        }

        // Pallet labels inside/centered on each pallet
        for (pb in layout.palletRects) {
            val isSelected = selectedPalletId == pb.pallet.id
            if (isSelected || transform.scale >= 34f) {
                val center = pt(pb.centerX, pb.centerY)
                val shortName = pb.pallet.name.take(9)
                val label = if (pb.pallet.effectiveCartonCount > 0 && transform.scale >= 52f) {
                    "$shortName • ${pb.pallet.effectiveCartonCount.toPersianNumber()}ک"
                } else {
                    shortName
                }
                drawCanvasPillText(
                    text = label,
                    center = center,
                    bgColor = if (isSelected) palette.selectionGlow else palette.labelBg,
                    textColor = if (isSelected) Color.White else palette.labelText,
                    textSizePx = (if (isSelected) 10.5f else 9.2f) * density * fontScale,
                    typeface = typeface,
                    borderColor = if (isSelected) Color.White else null,
                )
            }
        }
    }
}

// ============================================================================
// DRAWING HELPERS
// ============================================================================

private fun DrawScope.drawQuad(
    pts: List<Offset>,
    fill: Color,
    stroke: Color? = null,
    strokeWidth: Float = 1f,
) {
    if (pts.size < 4) return
    val path = Path().apply {
        moveTo(pts[0].x, pts[0].y)
        lineTo(pts[1].x, pts[1].y)
        lineTo(pts[2].x, pts[2].y)
        lineTo(pts[3].x, pts[3].y)
        close()
    }
    drawPath(path = path, color = fill)
    if (stroke != null) {
        drawPath(
            path = path,
            color = stroke,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
        )
    }
}

private fun DrawScope.drawCanvasPillText(
    text: String,
    center: Offset,
    bgColor: Color,
    textColor: Color,
    textSizePx: Float,
    typeface: Typeface,
    borderColor: Color? = null,
) {
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = textSizePx.coerceIn(14f, 44f)
            textAlign = Paint.Align.CENTER
            this.typeface = typeface
        }
        val textWidth = textPaint.measureText(text)
        val fm = textPaint.fontMetrics
        val textHeight = fm.descent - fm.ascent
        val padH = textSizePx * 0.55f
        val padV = textSizePx * 0.25f

        val rect = RectF(
            center.x - textWidth * 0.5f - padH,
            center.y - textHeight * 0.5f - padV,
            center.x + textWidth * 0.5f + padH,
            center.y + textHeight * 0.5f + padV,
        )
        val corner = rect.height() * 0.36f

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor.toArgb()
            style = Paint.Style.FILL
        }
        native.drawRoundRect(rect, corner, corner, bgPaint)

        if (borderColor != null) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = borderColor.toArgb()
                style = Paint.Style.STROKE
                strokeWidth = 2.2f
            }
            native.drawRoundRect(rect, corner, corner, borderPaint)
        }

        val baseline = center.y - (fm.ascent + fm.descent) * 0.5f
        native.drawText(text, center.x, baseline, textPaint)
    }
}
