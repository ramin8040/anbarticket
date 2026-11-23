package com.example.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InternalImageFolderManagerTest {

    private lateinit var context: Context
    private lateinit var testRootDir: File
    private lateinit var manager: InternalImageFolderManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        testRootDir = File(context.filesDir, "test_anbar_tree_${System.nanoTime()}")
        manager = InternalImageFolderManager(context, customRootDir = testRootDir)
    }

    @After
    fun tearDown() {
        testRootDir.deleteRecursively()
    }

    @Test
    fun createsPhysicalWarehouseRowPalletAndCartonDirectories() = runTest {
        val palletDir = manager.ensurePalletDirectory("انبار مرکزی", "ردیف ۱", "پالت ۰۵")
        val cartonDir = manager.ensureCartonDirectory("انبار مرکزی", "ردیف ۱", "پالت ۰۵", "کارتن ۲")

        assertTrue(palletDir.exists() && palletDir.isDirectory)
        assertTrue(File(palletDir, PersianUtils.PALLET_TICKET_SUBFOLDER).exists())
        assertTrue(cartonDir.exists() && cartonDir.isDirectory)
        assertEquals("پالت ۰۵", palletDir.name)
        assertEquals("ردیف ۱", palletDir.parentFile?.name)
        assertEquals("انبار مرکزی", palletDir.parentFile?.parentFile?.name)
    }

    @Test
    fun savesListsMovesAndDeletesImageFilesInFolderTree() = runTest {
        val dummyBytes = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val saved = manager.saveImageBytes(
            bytes = dummyBytes,
            warehouseName = "انبار الف",
            rowName = "ردیف ۳",
            palletName = "پالت ۱۰",
            cartonName = null,
            fileName = "ticket_pallet_14050901.jpg",
        )

        assertTrue(saved.file.exists())
        assertEquals(1, manager.listImages("انبار الف", "ردیف ۳", "پالت ۱۰", null).size)

        val moved = manager.moveImageFile(
            sourceUriOrPath = saved.fileUri,
            targetWarehouseName = "انبار الف",
            targetRowName = "ردیف ۳",
            targetPalletName = "پالت ۱۰",
            targetCartonName = "کارتن ۱",
            fileName = saved.fileName,
        )

        assertNotNull(moved)
        assertFalse(saved.file.exists())
        assertTrue(moved!!.file.exists())
        assertEquals(0, manager.listImages("انبار الف", "ردیف ۳", "پالت ۱۰", null).size)
        assertEquals(1, manager.listImages("انبار الف", "ردیف ۳", "پالت ۱۰", "کارتن ۱").size)

        assertTrue(manager.deleteImageFile(moved.fileUri))
        assertFalse(moved.file.exists())
    }

    @Test
    fun renamesAndDeletesFolderTreesRecursively() = runTest {
        val dummyBytes = byteArrayOf(1, 2, 3, 4)
        manager.saveImageBytes(
            bytes = dummyBytes,
            warehouseName = "انبار قدیم",
            rowName = "ردیف A",
            palletName = "پالت ۱",
            cartonName = null,
            fileName = "t1.jpg",
        )

        assertTrue(manager.renameWarehouseDirectory("انبار قدیم", "انبار جدید"))
        assertEquals(0, manager.listImages("انبار قدیم", "ردیف A", "پالت ۱", null).size)
        assertEquals(1, manager.listImages("انبار جدید", "ردیف A", "پالت ۱", null).size)

        val summary = manager.getStorageSummary()
        assertEquals(1, summary.warehouseFolderCount)
        assertEquals(1, summary.totalImageCount)

        assertTrue(manager.deleteWarehouseDirectory("انبار جدید"))
        assertEquals(0, manager.getStorageSummary().warehouseFolderCount)
    }

    @Test
    fun sanitizesUnsafePathTraversalSegments() = runTest {
        val dir = manager.ensurePalletDirectory("../انبار*بد", "ردیف/۲", "پالت:۱")
        assertTrue(dir.exists())
        assertTrue(dir.canonicalPath.startsWith(testRootDir.canonicalPath))
    }
}
