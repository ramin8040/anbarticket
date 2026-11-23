package com.example.data.cloud

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.CartonEntity
import com.example.data.local.PalletEntity
import com.example.data.local.RowEntity
import com.example.data.local.TicketPhotoEntity
import com.example.data.local.UserPreferences
import com.example.data.local.WarehouseEntity
import com.example.util.InternalImageFolderManager
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CloudBackupManagerTest {

    private lateinit var context: Context
    private lateinit var db: AppDatabase
    private lateinit var tempRoot: File
    private lateinit var folderManager: InternalImageFolderManager
    private lateinit var preferences: UserPreferences

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempRoot = File(context.cacheDir, "cloud_backup_test_${System.nanoTime()}").apply { mkdirs() }
        folderManager = InternalImageFolderManager(context, customRootDir = tempRoot)
        preferences = UserPreferences(context)
    }

    @After
    fun tearDown() {
        db.close()
        tempRoot.deleteRecursively()
    }

    @Test
    fun awsSigV4SignsDeterministicHeaderAndEncodesPersianPaths() {
        val encoded = AwsSigV4Signer.uriEncodePath("my-bucket/backup/images/انبار ۱/ردیف ۱/ticket.jpg")
        assertTrue(encoded.startsWith("/my-bucket/backup/images/"))
        assertFalse(encoded.contains(" "))
        assertTrue(encoded.endsWith("/ticket.jpg"))

        val headers = AwsSigV4Signer.signRequest(
            method = "PUT",
            canonicalUri = encoded,
            host = "s3.ir-thr-at1.arvanstorage.ir",
            region = "ir-thr-at1",
            accessKey = "TESTACCESSKEY",
            secretKey = "TESTSECRETKEY",
            payloadSha256 = AwsSigV4Signer.EMPTY_PAYLOAD_HASH,
            nowUtc = Date(1700000000000L),
        )
        assertTrue(headers.authorization.startsWith("AWS4-HMAC-SHA256 Credential=TESTACCESSKEY/"))
        assertTrue(headers.authorization.contains("/ir-thr-at1/s3/aws4_request"))
        assertTrue(headers.authorization.contains("SignedHeaders=host;x-amz-content-sha256;x-amz-date"))
        assertEquals(16, headers.amzDate.length)
    }

    @Test
    fun cloudStorageConfigValidatesHttpsAndRejectsPlaceholdersOrLocalhost() {
        val placeholderCfg = CloudStorageConfig(
            endpoint = "https://s3.ir-thr-at1.arvanstorage.ir",
            bucket = "YOUR_ARVAN_BUCKET_NAME",
            region = "ir-thr-at1",
            accessKey = "YOUR_ARVAN_ACCESS_KEY",
            secretKey = "YOUR_ARVAN_SECRET_KEY",
        )
        assertFalse(placeholderCfg.isConfigured)

        val httpCfg = CloudStorageConfig(
            endpoint = "http://s3.ir-thr-at1.arvanstorage.ir",
            bucket = "anbar-backup",
            region = "ir-thr-at1",
            accessKey = "ak-real-123",
            secretKey = "sk-real-456",
        )
        assertFalse(httpCfg.isConfigured)

        val localhostCfg = CloudStorageConfig(
            endpoint = "https://127.0.0.1:9000",
            bucket = "anbar-backup",
            region = "ir-thr-at1",
            accessKey = "ak-real-123",
            secretKey = "sk-real-456",
        )
        assertFalse(localhostCfg.isConfigured)

        val validCfg = CloudStorageConfig(
            endpoint = "https://s3.ir-thr-at1.arvanstorage.ir",
            bucket = "anbar-backup-01",
            region = "ir-thr-at1",
            accessKey = "ak-real-123",
            secretKey = "sk-real-456",
        )
        assertTrue(validCfg.isConfigured)
    }

    @Test
    fun backupIncrementalSyncAndFullDisasterRecoveryRestoreRoundtrip() = runTest {
        val fakeStore = InMemoryCloudObjectStore()
        val manager = CloudBackupManager(
            context = context,
            database = db,
            folderManager = folderManager,
            preferences = preferences,
            objectStore = fakeStore,
        )

        val dao = db.warehouseDao()
        val whId = dao.insertWarehouse(WarehouseEntity(name = "سوله مرکزی"))
        val rowId = dao.insertRow(RowEntity(warehouseId = whId, name = "ردیف ۱"))
        val palletId = dao.insertPallet(PalletEntity(rowId = rowId, name = "پالت ۰۱", cartonCount = 10, itemCount = 120))
        val cartonId = dao.insertCarton(CartonEntity(palletId = palletId, name = "کارتن ۱", itemCount = 24))

        val palletImgBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x11, 0x22, 0x33)
        val cartonImgBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0x44, 0x55, 0x66, 0x77)

        val savedPalletImg = folderManager.saveImageBytes(
            bytes = palletImgBytes,
            warehouseName = "سوله مرکزی",
            rowName = "ردیف ۱",
            palletName = "پالت ۰۱",
            cartonName = null,
            fileName = "ticket_pallet_1.jpg",
        )
        val savedCartonImg = folderManager.saveImageBytes(
            bytes = cartonImgBytes,
            warehouseName = "سوله مرکزی",
            rowName = "ردیف ۱",
            palletName = "پالت ۰۱",
            cartonName = "کارتن ۱",
            fileName = "ticket_carton_1.jpg",
        )

        dao.insertTicketPhoto(
            TicketPhotoEntity(
                palletId = palletId,
                cartonId = null,
                mediaUri = savedPalletImg.fileUri,
                relativeFolderPath = savedPalletImg.relativeFolderPath,
                fileName = savedPalletImg.fileName,
                capturedAt = 1700000100000L,
                jalaliDateTime = "۱۴۰۵/۰۹/۰۱ - ۱۰:۰۰",
                note = "تیکت اصلی پالت",
            ),
        )
        dao.insertTicketPhoto(
            TicketPhotoEntity(
                palletId = palletId,
                cartonId = cartonId,
                mediaUri = savedCartonImg.fileUri,
                relativeFolderPath = savedCartonImg.relativeFolderPath,
                fileName = savedCartonImg.fileName,
                capturedAt = 1700000200000L,
                jalaliDateTime = "۱۴۰۵/۰۹/۰۱ - ۱۰:۰۵",
                note = "تیکت کارتن اول",
            ),
        )

        // 1. First backup uploads both images + manifest.json
        val firstBackup = manager.backupNow()
        assertTrue(firstBackup is CloudResult.Success)
        val firstSummary = (firstBackup as CloudResult.Success).data
        assertEquals(2, firstSummary.uploadedImagesCount)
        assertEquals(0, firstSummary.skippedAlreadySyncedCount)
        assertNotNull(fakeStore.objects[CloudBackupManager.MANIFEST_OBJECT_KEY])
        assertEquals(3, fakeStore.objects.size) // 2 images + 1 manifest

        // 2. Second backup skips already-synced images incrementally
        val secondBackup = manager.backupNow()
        assertTrue(secondBackup is CloudResult.Success)
        val secondSummary = (secondBackup as CloudResult.Success).data
        assertEquals(0, secondSummary.uploadedImagesCount)
        assertEquals(2, secondSummary.skippedAlreadySyncedCount)

        // 3. Simulate total phone loss / wipe (delete local DB & all internal image folders)
        dao.clearAllSyncedObjects()
        dao.clearAllTicketPhotos()
        dao.clearAllCartons()
        dao.clearAllPallets()
        dao.clearAllRows()
        dao.clearAllWarehouses()
        tempRoot.deleteRecursively()
        tempRoot.mkdirs()
        assertEquals(0, dao.getAllWarehousesOnce().size)
        assertEquals(0, dao.getAllTicketPhotosOnce().size)

        // 4. Full Disaster-Recovery Restore from Cloud
        val restoreRes = manager.restoreFromCloud()
        assertTrue(restoreRes is CloudResult.Success)
        val restoreSummary = (restoreRes as CloudResult.Success).data
        assertEquals(1, restoreSummary.restoredWarehouses)
        assertEquals(1, restoreSummary.restoredRows)
        assertEquals(1, restoreSummary.restoredPallets)
        assertEquals(1, restoreSummary.restoredCartons)
        assertEquals(2, restoreSummary.restoredTickets)
        assertEquals(2, restoreSummary.downloadedImages)
        assertEquals(0, restoreSummary.missingImages)

        // Verify Room DB records restored
        assertEquals("سوله مرکزی", dao.getAllWarehousesOnce().first().name)
        assertEquals("پالت ۰۱", dao.getAllPalletsOnce().first().name)
        assertEquals(10, dao.getAllPalletsOnce().first().cartonCount)
        assertEquals(120, dao.getAllPalletsOnce().first().itemCount)

        // Verify real image bytes physically restored into the exact folder hierarchy
        val restoredPalletFiles = folderManager.listImages("سوله مرکزی", "ردیف ۱", "پالت ۰۱", null)
        val restoredCartonFiles = folderManager.listImages("سوله مرکزی", "ردیف ۱", "پالت ۰۱", "کارتن ۱")
        assertEquals(1, restoredPalletFiles.size)
        assertEquals(1, restoredCartonFiles.size)
        assertArrayEquals(palletImgBytes, restoredPalletFiles.first().readBytes())
        assertArrayEquals(cartonImgBytes, restoredCartonFiles.first().readBytes())
    }

    private class InMemoryCloudObjectStore : CloudObjectStore {
        override val config = CloudStorageConfig(
            endpoint = "https://s3.ir-thr-at1.arvanstorage.ir",
            bucket = "anbar-test-bucket",
            region = "ir-thr-at1",
            accessKey = "test-access-key",
            secretKey = "test-secret-key",
        )

        val objects = mutableMapOf<String, ByteArray>()

        override suspend fun testConnection(): CloudResult<Unit> = CloudResult.Success(Unit)

        override suspend fun uploadBytes(
            objectKey: String,
            bytes: ByteArray,
            contentType: String,
        ): CloudResult<Unit> {
            objects[objectKey] = bytes.copyOf()
            return CloudResult.Success(Unit)
        }

        override suspend fun uploadFile(
            objectKey: String,
            file: File,
            contentType: String,
        ): CloudResult<Unit> {
            objects[objectKey] = file.readBytes()
            return CloudResult.Success(Unit)
        }

        override suspend fun downloadBytes(objectKey: String): CloudResult<ByteArray> {
            val found = objects[objectKey]
                ?: return CloudResult.Error("Not found", httpCode = 404)
            return CloudResult.Success(found.copyOf())
        }
    }
}
