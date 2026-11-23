package com.example.ui.map

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.example.data.local.CartonEntity
import com.example.data.local.PalletEntity
import com.example.data.local.RowReportItem
import com.example.data.local.TicketDetailItem
import com.example.data.local.WarehouseSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WarehouseMapGeometryTest {

    @Test
    fun naturalPersianSortingOrdersNumericRowsAndPalletsCorrectly() {
        val raw = listOf("ردیف ۱۰", "ردیف ۲", "ردیف ۱", "ردیف ۱۱", "ردیف ۳")
        val sorted = raw.sortedWith(WarehouseMapBuilder::compareNaturalPersian)
        assertEquals(listOf("ردیف ۱", "ردیف ۲", "ردیف ۳", "ردیف ۱۰", "ردیف ۱۱"), sorted)
    }

    @Test
    fun builderAggregatesRegisteredHallRowsPalletsStatusesAndTiers() {
        val warehouse = WarehouseSummary(
            id = 1L,
            name = "سوله مرکزی",
            createdAt = 1000L,
            rowCount = 2,
            palletCount = 3,
            totalCartons = 20,
            totalItems = 250,
            photoCount = 1,
        )
        val rows = listOf(
            RowReportItem(1L, "سوله مرکزی", 10L, "ردیف ۲", 1, 0, 0, 0, 0, 0, 0),
            RowReportItem(1L, "سوله مرکزی", 11L, "ردیف ۱", 2, 20, 1, 250, 1, 0, 1),
        )
        val pallets = listOf(
            PalletEntity(id = 100L, rowId = 11L, name = "پالت ۲", cartonCount = 6, itemCount = 50),
            PalletEntity(id = 101L, rowId = 11L, name = "پالت ۱", cartonCount = 18, itemCount = 200),
            PalletEntity(id = 102L, rowId = 10L, name = "پالت ۱", cartonCount = 0, itemCount = 0),
        )
        val cartons = listOf(
            CartonEntity(id = 200L, palletId = 101L, name = "کارتن ۱", itemCount = 20),
        )
        val tickets = listOf(
            TicketDetailItem(
                id = 300L,
                palletId = 101L,
                cartonId = null,
                warehouseId = 1L,
                warehouseName = "سوله مرکزی",
                rowId = 11L,
                rowName = "ردیف ۱",
                palletName = "پالت ۱",
                cartonName = null,
                palletCartonCount = 18,
                palletItemCount = 200,
                cartonItemCount = null,
                mediaUri = "file:///tmp/t1.jpg",
                relativeFolderPath = "تیکت_انبار/سوله مرکزی/ردیف ۱/پالت ۱/تیکت_پالت",
                fileName = "t1.jpg",
                capturedAt = 2000L,
                jalaliDateTime = "۱۴۰۵/۰۹/۰۱ - ۱۰:۰۰",
                note = "",
            ),
        )

        val maps = WarehouseMapBuilder.buildAllWarehousesMapData(
            warehouses = listOf(warehouse),
            rowReportItems = rows,
            allPallets = pallets,
            allCartons = cartons,
            allTickets = tickets,
        )

        assertEquals(1, maps.size)
        val hall = maps.first()
        assertEquals(2, hall.rows.size)
        // Natural sort: ردیف ۱ before ردیف ۲
        assertEquals("ردیف ۱", hall.rows[0].name)
        assertEquals("ردیف ۲", hall.rows[1].name)

        // Row 1 pallets sorted naturally: پالت ۱ then پالت ۲
        val r1Pallets = hall.rows[0].pallets
        assertEquals("پالت ۱", r1Pallets[0].name)
        assertEquals(PalletMapStatus.TICKETED, r1Pallets[0].status)
        assertEquals(3, r1Pallets[0].stackTiers)

        assertEquals("پالت ۲", r1Pallets[1].name)
        assertEquals(PalletMapStatus.COUNTED_NO_TICKET, r1Pallets[1].status)
        assertEquals(2, r1Pallets[1].stackTiers)

        // Row 2 empty pallet
        val r2Pallet = hall.rows[1].pallets.first()
        assertEquals(PalletMapStatus.EMPTY, r2Pallet.status)
        assertEquals(0, r2Pallet.stackTiers)
    }

    @Test
    fun topDownAndIsometricHitTestsSelectProjectedPalletAccurately() {
        val pallet = MapPalletNode(
            id = 55L,
            rowId = 5L,
            rowName = "ردیف ۱",
            warehouseId = 1L,
            warehouseName = "سوله الف",
            name = "پالت ۱",
            effectiveCartonCount = 10,
            effectiveItemCount = 80,
            registeredCartonCount = 2,
            ticketCount = 1,
            status = PalletMapStatus.TICKETED,
            stackTiers = 2,
            rowIndex = 0,
            slotIndex = 0,
        )
        val hall = WarehouseHallMapData(
            warehouseId = 1L,
            warehouseName = "سوله الف",
            rows = listOf(MapRowNode(5L, 1L, "ردیف ۱", 0, listOf(pallet), 10, 80, 1)),
            totalPallets = 1,
            totalCartons = 10,
            totalItems = 80,
            totalTickets = 1,
            maxPalletsInRow = 1,
        )

        val layout = WarehouseMapGeometry.computeHallLayout(hall)
        val pb = layout.palletRects.first()

        // 1. Verify 2D Top-Down hit test
        val tdBounds = Rect(-0.85f, -0.95f, layout.hallWidth + 0.85f, layout.hallDepth + 0.95f)
        val tdTransform = WarehouseMapGeometry.computeViewportTransform(
            rawBounds = tdBounds,
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            paddingPx = 40f,
            userZoom = 1f,
            userPan = Offset.Zero,
        )
        val tdCenterScreen = tdTransform.rawToScreen(Offset(pb.centerX, pb.centerY))
        val tdHit = WarehouseMapGeometry.hitTestTopDown(tdCenterScreen, layout, tdTransform)
        assertTrue(tdHit is MapSelection.Pallet && tdHit.pallet.id == 55L)

        // 2. Verify Isometric 3D hit test on top face of the 3D pallet prism
        val isoBounds = WarehouseMapGeometry.computeIsoRawBounds(layout)
        val isoTransform = WarehouseMapGeometry.computeViewportTransform(
            rawBounds = isoBounds,
            canvasWidth = 1000f,
            canvasHeight = 1000f,
            paddingPx = 40f,
            userZoom = 1f,
            userPan = Offset.Zero,
        )
        val isoTopCenter = isoTransform.rawToScreen(
            WarehouseMapGeometry.projectIsoRaw(pb.centerX, pb.centerY, pb.totalHeightZ),
        )
        val isoHit = WarehouseMapGeometry.hitTestIsometric(isoTopCenter, layout, isoTransform)
        assertTrue(isoHit is MapSelection.Pallet && isoHit.pallet.id == 55L)
    }
}
