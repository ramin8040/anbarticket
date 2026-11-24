package com.example.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.OperationResult
import com.example.data.local.AppDatabase
import com.example.data.local.CartonEntity
import com.example.data.local.PalletEntity
import com.example.data.local.RowEntity
import com.example.data.local.TicketPhotoEntity
import com.example.data.local.WarehouseEntity
import com.example.util.InternalImageFolderManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalBackupZipManagerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var folderManager: InternalImageFolderManager
    private lateinit var zipManager: LocalBackupZipManager
    private lateinit var tempBackupZipFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        folderManager = InternalImageFolderManager(context)
        folderManager.rootDir.deleteRecursively()
        folderManager.rootDir.mkdirs()

        zipManager = LocalBackupZipManager(
            context = context,
            database = database,
            folderManager = folderManager,
        )
        tempBackupZipFile = File(context.cacheDir, "test_full_backup.zip")
        tempBackupZipFile.delete()
    }

    @After
    fun tearDown() {
        database.close()
        folderManager.rootDir.deleteRecursively()
        tempBackupZipFile.delete()
    }

    @Test
    fun `export inspect and REPLACE restore preserves all database records and physical photos`() = runBlocking {
        val dao = database.warehouseDao()
        val whId = dao.insertWarehouse(WarehouseEntity(name = "انبار مرکزی", createdAt = 1000L))
        val rowId = dao.insertRow(RowEntity(warehouseId = whId, name = "ردیف ۱", createdAt = 1100L))
        val palletId = dao.insertPallet(
            PalletEntity(rowId = rowId, name = "پالت A", cartonCount = 14, itemCount = 140, createdAt = 1200L),
        )
        val cartonId = dao.insertCarton(
            CartonEntity(palletId = palletId, name = "کارتن ۱", itemCount = 35, createdAt = 1300L),
        )

        val palletPhotoBytes = byteArrayOf(0x11, 0x22, 0x33, 0x44, 0x55)
        val savedPalletImg = folderManager.saveImageBytes(
            bytes = palletPhotoBytes,
            warehouseName = "انبار مرکزی",
            rowName = "ردیف ۱",
            palletName = "پالت A",
            cartonName = null,
            fileName = "Ticket_Pallet_A.jpg",
        )
        dao.insertTicketPhoto(
            TicketPhotoEntity(
                palletId = palletId,
                cartonId = null,
                mediaUri = savedPalletImg.fileUri,
                relativeFolderPath = savedPalletImg.relativeFolderPath,
                fileName = "Ticket_Pallet_A.jpg",
                capturedAt = 1500L,
                jalaliDateTime = "۱۴۰۴/۰۱/۲۰ - ۰۹:۱۵:۰۰",
                note = "تیکت پالت اصلی",
            ),
        )

        val cartonPhotoBytes = byteArrayOf(0x66, 0x77, 0x12, 0x34, 0x56, 0x78)
        val savedCartonImg = folderManager.saveImageBytes(
            bytes = cartonPhotoBytes,
            warehouseName = "انبار مرکزی",
            rowName = "ردیف ۱",
            palletName = "پالت A",
            cartonName = "کارتن ۱",
            fileName = "Ticket_Carton_1.jpg",
        )
        dao.insertTicketPhoto(
            TicketPhotoEntity(
                palletId = palletId,
                cartonId = cartonId,
                mediaUri = savedCartonImg.fileUri,
                relativeFolderPath = savedCartonImg.relativeFolderPath,
                fileName = "Ticket_Carton_1.jpg",
                capturedAt = 1600L,
                jalaliDateTime = "۱۴۰۴/۰۱/۲۰ - ۰۹:۲۰:۰۰",
                note = "تیکت کارتن اول",
            ),
        )

        // 1. Export full ZIP backup
        val exportResult = zipManager.exportBackupToFile(tempBackupZipFile)
        assertTrue(exportResult is OperationResult.Success)
        val exportSummary = (exportResult as OperationResult.Success).data
        assertEquals(1, exportSummary.exportedWarehouses)
        assertEquals(1, exportSummary.exportedPallets)
        assertEquals(1, exportSummary.exportedCartons)
        assertEquals(2, exportSummary.exportedTickets)
        assertEquals(2, exportSummary.exportedImages)
        assertTrue(tempBackupZipFile.exists() && tempBackupZipFile.length() > 0L)

        // 2. Inspect ZIP backup
        val zipUri = Uri.fromFile(tempBackupZipFile)
        val inspectResult = zipManager.inspectBackupZip(zipUri)
        assertTrue(inspectResult is OperationResult.Success)
        val preview = (inspectResult as OperationResult.Success).data
        assertEquals(1, preview.warehouseCount)
        assertEquals(1, preview.rowCount)
        assertEquals(1, preview.palletCount)
        assertEquals(1, preview.cartonCount)
        assertEquals(2, preview.ticketCount)
        assertEquals(2, preview.imageEntryCount)

        // 3. Wipe DB and physical folders, add dummy data that REPLACE should overwrite
        dao.clearAllTicketPhotos()
        dao.clearAllCartons()
        dao.clearAllPallets()
        dao.clearAllRows()
        dao.clearAllWarehouses()
        folderManager.rootDir.deleteRecursively()
        folderManager.rootDir.mkdirs()

        dao.insertWarehouse(WarehouseEntity(name = "انبار موقت که باید حذف شود", createdAt = 9999L))

        // 4. Restore in REPLACE mode
        val restoreResult = zipManager.restoreFromBackupZip(
            sourceUri = zipUri,
            mode = LocalRestoreConflictMode.REPLACE,
        )
        assertTrue(restoreResult is OperationResult.Success)
        val restoreSummary = (restoreResult as OperationResult.Success).data
        assertEquals(LocalRestoreConflictMode.REPLACE, restoreSummary.mode)
        assertEquals(1, restoreSummary.restoredWarehouses)
        assertEquals(2, restoreSummary.restoredTickets)
        assertEquals(2, restoreSummary.restoredImages)

        val restoredWarehouses = dao.getAllWarehousesOnce()
        assertEquals(1, restoredWarehouses.size)
        assertEquals("انبار مرکزی", restoredWarehouses.first().name)

        val restoredTickets = dao.getAllTicketPhotosOnce()
        assertEquals(2, restoredTickets.size)
        for (ticket in restoredTickets) {
            val file = Uri.parse(ticket.mediaUri).path?.let { File(it) }
            assertNotNull(file)
            assertTrue("Physical photo must exist at ${file?.absolutePath}", file!!.exists())
            if (ticket.fileName == "Ticket_Pallet_A.jpg") {
                assertArrayEquals(palletPhotoBytes, file.readBytes())
            } else {
                assertArrayEquals(cartonPhotoBytes, file.readBytes())
            }
        }
    }

    @Test
    fun `MERGE restore keeps existing data and merges backup hierarchy and photos without loss`() = runBlocking {
        val dao = database.warehouseDao()

        // Create initial dataset for Backup ZIP: "انبار مرکزی -> ردیف ۱ -> پالت A" + 1 photo
        val wh1Id = dao.insertWarehouse(WarehouseEntity(name = "انبار مرکزی", createdAt = 1000L))
        val row1Id = dao.insertRow(RowEntity(warehouseId = wh1Id, name = "ردیف ۱", createdAt = 1100L))
        val pal1Id = dao.insertPallet(
            PalletEntity(rowId = row1Id, name = "پالت A", cartonCount = 20, itemCount = 200, createdAt = 1200L),
        )
        val backupPhotoBytes = byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D)
        val savedBackupImg = folderManager.saveImageBytes(
            bytes = backupPhotoBytes,
            warehouseName = "انبار مرکزی",
            rowName = "ردیف ۱",
            palletName = "پالت A",
            cartonName = null,
            fileName = "Ticket_From_Backup.jpg",
        )
        dao.insertTicketPhoto(
            TicketPhotoEntity(
                palletId = pal1Id,
                cartonId = null,
                mediaUri = savedBackupImg.fileUri,
                relativeFolderPath = savedBackupImg.relativeFolderPath,
                fileName = "Ticket_From_Backup.jpg",
                capturedAt = 1500L,
                jalaliDateTime = "۱۴۰۴/۰۱/۲۱ - ۱۰:۰۰:۰۰",
                note = "عکس داخل بکاپ",
            ),
        )

        assertTrue(zipManager.exportBackupToFile(tempBackupZipFile) is OperationResult.Success)

        // Now reset current app state to simulate a phone that already has:
        // 1) "انبار مرکزی -> ردیف ۱ -> پالت A" (with lower cartonCount=10 and a DIFFERENT local photo)
        // 2) "انبار دوم -> ردیف ۲ -> پالت B" (which doesn't exist in the backup at all)
        dao.clearAllTicketPhotos()
        dao.clearAllCartons()
        dao.clearAllPallets()
        dao.clearAllRows()
        dao.clearAllWarehouses()
        folderManager.rootDir.deleteRecursively()
        folderManager.rootDir.mkdirs()

        val liveWh1Id = dao.insertWarehouse(WarehouseEntity(name = "انبار مرکزی", createdAt = 2000L))
        val liveRow1Id = dao.insertRow(RowEntity(warehouseId = liveWh1Id, name = "ردیف ۱", createdAt = 2100L))
        val livePal1Id = dao.insertPallet(
            PalletEntity(rowId = liveRow1Id, name = "پالت A", cartonCount = 10, itemCount = 100, createdAt = 2200L),
        )
        val existingLocalBytes = byteArrayOf(0x55, 0x66, 0x77)
        val savedExistingImg = folderManager.saveImageBytes(
            bytes = existingLocalBytes,
            warehouseName = "انبار مرکزی",
            rowName = "ردیف ۱",
            palletName = "پالت A",
            cartonName = null,
            fileName = "Ticket_Already_On_Phone.jpg",
        )
        dao.insertTicketPhoto(
            TicketPhotoEntity(
                palletId = livePal1Id,
                cartonId = null,
                mediaUri = savedExistingImg.fileUri,
                relativeFolderPath = savedExistingImg.relativeFolderPath,
                fileName = "Ticket_Already_On_Phone.jpg",
                capturedAt = 2500L,
                jalaliDateTime = "۱۴۰۴/۰۱/۲۲ - ۱۱:۰۰:۰۰",
                note = "عکس فعلی روی گوشی",
            ),
        )

        val liveWh2Id = dao.insertWarehouse(WarehouseEntity(name = "انبار دوم", createdAt = 3000L))
        val liveRow2Id = dao.insertRow(RowEntity(warehouseId = liveWh2Id, name = "ردیف ۲", createdAt = 3100L))
        dao.insertPallet(
            PalletEntity(rowId = liveRow2Id, name = "پالت B", cartonCount = 5, itemCount = 50, createdAt = 3200L),
        )

        // Execute MERGE restore
        val mergeResult = zipManager.restoreFromBackupZip(
            sourceUri = Uri.fromFile(tempBackupZipFile),
            mode = LocalRestoreConflictMode.MERGE,
        )
        assertTrue(mergeResult is OperationResult.Success)

        // Verify both warehouses exist ("انبار مرکزی" and "انبار دوم")
        val allWarehouses = dao.getAllWarehousesOnce()
        assertEquals(2, allWarehouses.size)

        // Verify "پالت A" took max counts (20 cartons, 200 items)
        val mergedPalletA = dao.getAllPalletsOnce().first { it.name == "پالت A" }
        assertEquals(20, mergedPalletA.cartonCount)
        assertEquals(200, mergedPalletA.itemCount)

        // Verify both photos ("Ticket_Already_On_Phone.jpg" AND "Ticket_From_Backup.jpg") exist under پالت A
        val allPhotos = dao.getAllTicketPhotosOnce()
        assertEquals(2, allPhotos.size)
        val fileNames = allPhotos.map { it.fileName }.toSet()
        assertTrue(fileNames.contains("Ticket_Already_On_Phone.jpg"))
        assertTrue(fileNames.contains("Ticket_From_Backup.jpg"))

        allPhotos.forEach { photo ->
            val f = Uri.parse(photo.mediaUri).path?.let { File(it) }
            assertNotNull(f)
            assertTrue(f!!.exists())
        }
    }

    @Test
    fun `normalizeZipEntryName blocks path traversal`() {
        assertNull(LocalBackupZipManager.normalizeZipEntryName("../evil.jpg"))
        assertNull(LocalBackupZipManager.normalizeZipEntryName("images/../../evil.jpg"))
        assertEquals(
            "images/تیکت_انبار/انبار_مرکزی/ticket.jpg",
            LocalBackupZipManager.normalizeZipEntryName("/images/تیکت_انبار/انبار_مرکزی/ticket.jpg"),
        )
    }
}
