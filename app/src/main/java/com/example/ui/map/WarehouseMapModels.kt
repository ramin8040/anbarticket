package com.example.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.data.local.CartonEntity
import com.example.data.local.PalletEntity
import com.example.data.local.RowReportItem
import com.example.data.local.TicketDetailItem
import com.example.data.local.WarehouseSummary
import com.example.util.PersianUtils
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

enum class MapViewMode(val label: String, val badgeLabel: String) {
    ISOMETRIC("نقشه ایزومتریک (۳D)", "نمای ایزومتریک ۳۰°"),
    TOP_DOWN_2D("نمای از بالا (۲D)", "پلان دو بعدی از بالا"),
}

enum class PalletMapStatus(val label: String) {
    TICKETED("تیکت ثبت‌شده"),
    COUNTED_NO_TICKET("دارای شمارش (بدون تیکت)"),
    EMPTY("خالی / ثبت اولیه"),
}

data class MapPalletNode(
    val id: Long,
    val rowId: Long,
    val rowName: String,
    val warehouseId: Long,
    val warehouseName: String,
    val name: String,
    val effectiveCartonCount: Int,
    val effectiveItemCount: Int,
    val registeredCartonCount: Int,
    val ticketCount: Int,
    val status: PalletMapStatus,
    val stackTiers: Int,
    val rowIndex: Int,
    val slotIndex: Int,
)

data class MapRowNode(
    val id: Long,
    val warehouseId: Long,
    val name: String,
    val rowIndex: Int,
    val pallets: List<MapPalletNode>,
    val totalCartons: Int,
    val totalItems: Int,
    val totalTickets: Int,
)

data class WarehouseHallMapData(
    val warehouseId: Long,
    val warehouseName: String,
    val rows: List<MapRowNode>,
    val totalPallets: Int,
    val totalCartons: Int,
    val totalItems: Int,
    val totalTickets: Int,
    val maxPalletsInRow: Int,
)

sealed interface MapSelection {
    data class Pallet(val pallet: MapPalletNode) : MapSelection
    data class Row(val row: MapRowNode) : MapSelection
}

/**
 * Builds structured, naturally sorted map data strictly from registered Room entities.
 */
object WarehouseMapBuilder {

    private val numberTokenRegex = Regex("(\\d+)")

    /**
     * Compares strings with embedded Persian/English numbers in natural numeric order
     * (e.g., "ردیف ۲" comes before "ردیف ۱۰", "پالت ۰۱" before "پالت ۲").
     */
    fun compareNaturalPersian(a: String, b: String): Int {
        val normA = PersianUtils.toEnglishDigits(a.trim())
        val normB = PersianUtils.toEnglishDigits(b.trim())
        val tokensA = tokenize(normA)
        val tokensB = tokenize(normB)
        val count = min(tokensA.size, tokensB.size)
        for (i in 0 until count) {
            val ta = tokensA[i]
            val tb = tokensB[i]
            val na = ta.toLongOrNull()
            val nb = tb.toLongOrNull()
            val cmp = if (na != null && nb != null) {
                na.compareTo(nb)
            } else {
                ta.compareTo(tb, ignoreCase = true)
            }
            if (cmp != 0) return cmp
        }
        return tokensA.size.compareTo(tokensB.size)
    }

    private fun tokenize(input: String): List<String> {
        val result = mutableListOf<String>()
        var lastIndex = 0
        for (match in numberTokenRegex.findAll(input)) {
            if (match.range.first > lastIndex) {
                result.add(input.substring(lastIndex, match.range.first))
            }
            result.add(match.value)
            lastIndex = match.range.last + 1
        }
        if (lastIndex < input.length) {
            result.add(input.substring(lastIndex))
        }
        return result
    }

    fun buildAllWarehousesMapData(
        warehouses: List<WarehouseSummary>,
        rowReportItems: List<RowReportItem>,
        allPallets: List<PalletEntity>,
        allCartons: List<CartonEntity>,
        allTickets: List<TicketDetailItem>,
    ): List<WarehouseHallMapData> {
        val cartonsByPallet = allCartons.groupBy { it.palletId }
        val ticketsByPallet = allTickets.groupBy { it.palletId }
        val palletsByRow = allPallets.groupBy { it.rowId }
        val rowsByWarehouse = rowReportItems.groupBy { it.warehouseId }

        return warehouses.map { warehouse ->
            val sortedRows = rowsByWarehouse[warehouse.id]
                .orEmpty()
                .sortedWith { r1, r2 -> compareNaturalPersian(r1.rowName, r2.rowName) }

            val rowNodes = sortedRows.mapIndexed { rowIdx, rowItem ->
                val sortedPallets = palletsByRow[rowItem.rowId]
                    .orEmpty()
                    .sortedWith { p1, p2 -> compareNaturalPersian(p1.name, p2.name) }

                val palletNodes = sortedPallets.mapIndexed { slotIdx, pallet ->
                    val palletCartons = cartonsByPallet[pallet.id].orEmpty()
                    val registeredCartonCount = palletCartons.size
                    val cartonsItemSum = palletCartons.sumOf { it.itemCount }
                    val effectiveCartons = max(pallet.cartonCount, registeredCartonCount)
                    val effectiveItems = max(pallet.itemCount, cartonsItemSum)
                    val ticketCount = ticketsByPallet[pallet.id]?.size ?: 0

                    val status = when {
                        ticketCount > 0 -> PalletMapStatus.TICKETED
                        effectiveCartons > 0 || effectiveItems > 0 -> PalletMapStatus.COUNTED_NO_TICKET
                        else -> PalletMapStatus.EMPTY
                    }

                    val stackTiers = when {
                        effectiveCartons == 0 && effectiveItems == 0 -> 0
                        effectiveCartons >= 16 || effectiveItems >= 150 -> 3
                        effectiveCartons >= 5 || effectiveItems >= 40 -> 2
                        else -> 1
                    }

                    MapPalletNode(
                        id = pallet.id,
                        rowId = rowItem.rowId,
                        rowName = rowItem.rowName,
                        warehouseId = warehouse.id,
                        warehouseName = warehouse.name,
                        name = pallet.name,
                        effectiveCartonCount = effectiveCartons,
                        effectiveItemCount = effectiveItems,
                        registeredCartonCount = registeredCartonCount,
                        ticketCount = ticketCount,
                        status = status,
                        stackTiers = stackTiers,
                        rowIndex = rowIdx,
                        slotIndex = slotIdx,
                    )
                }

                MapRowNode(
                    id = rowItem.rowId,
                    warehouseId = warehouse.id,
                    name = rowItem.rowName,
                    rowIndex = rowIdx,
                    pallets = palletNodes,
                    totalCartons = palletNodes.sumOf { it.effectiveCartonCount },
                    totalItems = palletNodes.sumOf { it.effectiveItemCount },
                    totalTickets = palletNodes.sumOf { it.ticketCount },
                )
            }

            WarehouseHallMapData(
                warehouseId = warehouse.id,
                warehouseName = warehouse.name,
                rows = rowNodes,
                totalPallets = rowNodes.sumOf { it.pallets.size },
                totalCartons = rowNodes.sumOf { it.totalCartons },
                totalItems = rowNodes.sumOf { it.totalItems },
                totalTickets = rowNodes.sumOf { it.totalTickets },
                maxPalletsInRow = rowNodes.maxOfOrNull { it.pallets.size } ?: 0,
            )
        }
    }
}

/**
 * Coordinate geometry for both 2D Top-Down and 30° Isometric warehouse projections.
 * World X runs across rows (left-to-right bays separated by forklift aisles).
 * World Y runs along a row's depth (back wall -> front loading dock).
 * World Z runs vertically upward (0 = floor, +Z = pallet/carton stack height & hall walls).
 */
object WarehouseMapGeometry {
    const val PALLET_WIDTH = 1.0f
    const val PALLET_DEPTH = 0.88f
    const val SLOT_GAP_Y = 0.24f
    const val ROW_BAY_WIDTH = 1.34f
    const val AISLE_WIDTH = 0.92f
    const val HALL_MARGIN_X = 1.15f
    const val HALL_MARGIN_BACK_Y = 1.25f
    const val HALL_MARGIN_FRONT_Y = 1.55f
    const val HALL_WALL_HEIGHT = 2.05f
    const val PALLET_BASE_HEIGHT = 0.14f
    const val TIER_HEIGHT = 0.26f

    private val ISO_ANGLE_RAD = Math.toRadians(30.0)
    val ISO_COS: Float = cos(ISO_ANGLE_RAD).toFloat()
    val ISO_SIN: Float = sin(ISO_ANGLE_RAD).toFloat()

    data class HallWorldLayout(
        val hallWidth: Float,
        val hallDepth: Float,
        val rowCount: Int,
        val maxSlots: Int,
        val rowRects: List<RowWorldBounds>,
        val palletRects: List<PalletWorldBounds>,
    )

    data class RowWorldBounds(
        val row: MapRowNode,
        val xStart: Float,
        val xEnd: Float,
        val yStart: Float,
        val yEnd: Float,
    ) {
        val centerX: Float get() = (xStart + xEnd) * 0.5f
    }

    data class PalletWorldBounds(
        val pallet: MapPalletNode,
        val xStart: Float,
        val xEnd: Float,
        val yStart: Float,
        val yEnd: Float,
        val totalHeightZ: Float,
    ) {
        val centerX: Float get() = (xStart + xEnd) * 0.5f
        val centerY: Float get() = (yStart + yEnd) * 0.5f
    }

    fun computeHallLayout(data: WarehouseHallMapData): HallWorldLayout {
        val effectiveRows = max(data.rows.size, 1)
        val effectiveSlots = max(data.maxPalletsInRow, 2)

        val rowPitchX = ROW_BAY_WIDTH + AISLE_WIDTH
        val slotPitchY = PALLET_DEPTH + SLOT_GAP_Y

        val contentWidth = if (data.rows.isEmpty()) {
            ROW_BAY_WIDTH * 2f + AISLE_WIDTH
        } else {
            effectiveRows * ROW_BAY_WIDTH + (effectiveRows - 1) * AISLE_WIDTH
        }
        val contentDepth = effectiveSlots * PALLET_DEPTH + (effectiveSlots - 1) * SLOT_GAP_Y

        val hallWidth = contentWidth + HALL_MARGIN_X * 2f
        val hallDepth = contentDepth + HALL_MARGIN_BACK_Y + HALL_MARGIN_FRONT_Y

        val rowRects = mutableListOf<RowWorldBounds>()
        val palletRects = mutableListOf<PalletWorldBounds>()

        data.rows.forEachIndexed { rIdx, row ->
            val bayXStart = HALL_MARGIN_X + rIdx * rowPitchX
            val bayXEnd = bayXStart + ROW_BAY_WIDTH
            val bayYStart = HALL_MARGIN_BACK_Y
            val bayYEnd = HALL_MARGIN_BACK_Y + contentDepth

            rowRects.add(
                RowWorldBounds(
                    row = row,
                    xStart = bayXStart,
                    xEnd = bayXEnd,
                    yStart = bayYStart,
                    yEnd = bayYEnd,
                ),
            )

            val palletOffsetX = (ROW_BAY_WIDTH - PALLET_WIDTH) * 0.5f
            row.pallets.forEachIndexed { sIdx, pallet ->
                val pxStart = bayXStart + palletOffsetX
                val pxEnd = pxStart + PALLET_WIDTH
                val pyStart = HALL_MARGIN_BACK_Y + sIdx * slotPitchY
                val pyEnd = pyStart + PALLET_DEPTH
                val heightZ = PALLET_BASE_HEIGHT + pallet.stackTiers * TIER_HEIGHT
                palletRects.add(
                    PalletWorldBounds(
                        pallet = pallet,
                        xStart = pxStart,
                        xEnd = pxEnd,
                        yStart = pyStart,
                        yEnd = pyEnd,
                        totalHeightZ = heightZ,
                    ),
                )
            }
        }

        return HallWorldLayout(
            hallWidth = hallWidth,
            hallDepth = hallDepth,
            rowCount = data.rows.size,
            maxSlots = data.maxPalletsInRow,
            rowRects = rowRects,
            palletRects = palletRects,
        )
    }

    /**
     * Projects 3D world coordinates (wx, wy, wz) to raw 2D isometric space.
     */
    fun projectIsoRaw(wx: Float, wy: Float, wz: Float = 0f): Offset {
        val ix = (wx - wy) * ISO_COS
        val iy = (wx + wy) * ISO_SIN - wz
        return Offset(ix, iy)
    }

    /**
     * Computes bounding box of the isometric hall (including walls and floor slab) in raw iso space.
     */
    fun computeIsoRawBounds(layout: HallWorldLayout): Rect {
        val corners = listOf(
            projectIsoRaw(0f, 0f, HALL_WALL_HEIGHT + 0.55f),
            projectIsoRaw(layout.hallWidth, 0f, HALL_WALL_HEIGHT + 0.35f),
            projectIsoRaw(0f, layout.hallDepth, HALL_WALL_HEIGHT + 0.35f),
            projectIsoRaw(layout.hallWidth, layout.hallDepth, -0.35f),
            projectIsoRaw(layout.hallWidth, 0f, -0.25f),
            projectIsoRaw(0f, layout.hallDepth, -0.25f),
            projectIsoRaw(0f, 0f, -0.25f),
        )
        var minX = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (p in corners) {
            minX = min(minX, p.x)
            maxX = max(maxX, p.x)
            minY = min(minY, p.y)
            maxY = max(maxY, p.y)
        }
        return Rect(minX, minY, maxX, maxY)
    }

    /**
     * Computes base scale and screen center offset to fit a world/iso bounding rect inside a canvas viewport.
     */
    fun computeViewportTransform(
        rawBounds: Rect,
        canvasWidth: Float,
        canvasHeight: Float,
        paddingPx: Float,
        userZoom: Float,
        userPan: Offset,
    ): ViewportTransform {
        val availW = (canvasWidth - paddingPx * 2f).coerceAtLeast(32f)
        val availH = (canvasHeight - paddingPx * 2f).coerceAtLeast(32f)
        val rawW = rawBounds.width.coerceAtLeast(0.1f)
        val rawH = rawBounds.height.coerceAtLeast(0.1f)
        val baseScale = min(availW / rawW, availH / rawH)
        val finalScale = baseScale * userZoom
        val rawCenterX = (rawBounds.left + rawBounds.right) * 0.5f
        val rawCenterY = (rawBounds.top + rawBounds.bottom) * 0.5f
        val originX = canvasWidth * 0.5f - rawCenterX * finalScale + userPan.x
        val originY = canvasHeight * 0.5f - rawCenterY * finalScale + userPan.y
        return ViewportTransform(
            scale = finalScale,
            origin = Offset(originX, originY),
        )
    }

    data class ViewportTransform(
        val scale: Float,
        val origin: Offset,
    ) {
        fun rawToScreen(raw: Offset): Offset = Offset(
            x = origin.x + raw.x * scale,
            y = origin.y + raw.y * scale,
        )

        fun screenToRaw(screen: Offset): Offset = Offset(
            x = (screen.x - origin.x) / scale,
            y = (screen.y - origin.y) / scale,
        )
    }

    /**
     * Performs exact tap hit-testing in 2D top-down mode.
     */
    fun hitTestTopDown(
        tapScreen: Offset,
        layout: HallWorldLayout,
        transform: ViewportTransform,
    ): MapSelection? {
        val worldPt = transform.screenToRaw(tapScreen)
        for (pb in layout.palletRects) {
            if (worldPt.x in pb.xStart..pb.xEnd && worldPt.y in pb.yStart..pb.yEnd) {
                return MapSelection.Pallet(pb.pallet)
            }
        }
        for (rb in layout.rowRects) {
            if (worldPt.x in rb.xStart..rb.xEnd && worldPt.y in rb.yStart..(rb.yEnd + 0.65f)) {
                return MapSelection.Row(rb.row)
            }
        }
        return null
    }

    /**
     * Performs exact tap hit-testing in 3D isometric mode by checking visible prism faces
     * in reverse Painter's order (front-most pallet first).
     */
    fun hitTestIsometric(
        tapScreen: Offset,
        layout: HallWorldLayout,
        transform: ViewportTransform,
    ): MapSelection? {
        val rawPt = transform.screenToRaw(tapScreen)

        // Reverse Painter's order: highest (rowIndex + slotIndex) is closest to viewer
        val frontToBack = layout.palletRects.sortedByDescending {
            it.pallet.rowIndex + it.pallet.slotIndex
        }

        for (pb in frontToBack) {
            val zTop = pb.totalHeightZ
            val topFace = listOf(
                projectIsoRaw(pb.xStart, pb.yStart, zTop),
                projectIsoRaw(pb.xEnd, pb.yStart, zTop),
                projectIsoRaw(pb.xEnd, pb.yEnd, zTop),
                projectIsoRaw(pb.xStart, pb.yEnd, zTop),
            )
            val leftFrontFace = listOf(
                projectIsoRaw(pb.xStart, pb.yEnd, zTop),
                projectIsoRaw(pb.xEnd, pb.yEnd, zTop),
                projectIsoRaw(pb.xEnd, pb.yEnd, 0f),
                projectIsoRaw(pb.xStart, pb.yEnd, 0f),
            )
            val rightFrontFace = listOf(
                projectIsoRaw(pb.xEnd, pb.yStart, zTop),
                projectIsoRaw(pb.xEnd, pb.yEnd, zTop),
                projectIsoRaw(pb.xEnd, pb.yEnd, 0f),
                projectIsoRaw(pb.xEnd, pb.yStart, 0f),
            )

            if (isPointInPolygon(rawPt, topFace) ||
                isPointInPolygon(rawPt, leftFrontFace) ||
                isPointInPolygon(rawPt, rightFrontFace)
            ) {
                return MapSelection.Pallet(pb.pallet)
            }
        }

        // Fallback: check floor row bays
        for (rb in layout.rowRects) {
            val bayPoly = listOf(
                projectIsoRaw(rb.xStart, rb.yStart, 0f),
                projectIsoRaw(rb.xEnd, rb.yStart, 0f),
                projectIsoRaw(rb.xEnd, rb.yEnd + 0.55f, 0f),
                projectIsoRaw(rb.xStart, rb.yEnd + 0.55f, 0f),
            )
            if (isPointInPolygon(rawPt, bayPoly)) {
                return MapSelection.Row(rb.row)
            }
        }

        return null
    }

    fun isPointInPolygon(point: Offset, polygon: List<Offset>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.lastIndex
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            val intersects = ((pi.y > point.y) != (pj.y > point.y)) &&
                (point.x < (pj.x - pi.x) * (point.y - pi.y) / ((pj.y - pi.y).takeIf { it != 0f } ?: 1e-6f) + pi.x)
            if (intersects) inside = !inside
            j = i
        }
        return inside
    }
}
