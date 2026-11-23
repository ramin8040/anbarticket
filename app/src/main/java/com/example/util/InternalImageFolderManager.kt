package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Metadata for an image file stored inside the app's internal directory tree.
 */
data class StoredImageFile(
    val file: File,
    val fileUri: String,
    val relativeFolderPath: String,
    val fileName: String,
    val sizeBytes: Long,
)

/**
 * Snapshot node representing an actual physical directory in internal storage.
 */
data class StorageFolderNode(
    val name: String,
    val relativePath: String,
    val absolutePath: String,
    val imageFiles: List<File>,
    val children: List<StorageFolderNode>,
    val totalImageCount: Int,
    val totalSizeBytes: Long,
)

/**
 * High-level storage statistics calculated from the physical internal directory tree.
 */
data class InternalStorageSummary(
    val rootAbsolutePath: String,
    val warehouseFolderCount: Int,
    val totalFolderCount: Int,
    val totalImageCount: Int,
    val totalSizeBytes: Long,
)

/**
 * Manages physical image file storage in Android internal storage (`context.filesDir`)
 * using a real hierarchical directory tree:
 *
 *   <filesDir>/تیکت_انبار/
 *     └── <نام_انبار>/
 *         └── <نام_ردیف>/
 *             └── <نام_پالت>/
 *                 ├── تیکت_پالت/
 *                 │   └── ticket_pallet_...jpg
 *                 └── کارتن‌ها/
 *                     └── <نام_کارتن>/
 *                         └── ticket_carton_...jpg
 *
 * All path segments are sanitized and canonical-path checked against the root directory
 * to prevent path traversal, and all operations require zero runtime storage permissions.
 */
class InternalImageFolderManager(
    private val context: Context,
    customRootDir: File? = null,
) {
    val rootDir: File = (customRootDir ?: File(context.filesDir, PersianUtils.ROOT_FOLDER_NAME)).apply {
        mkdirs()
    }

    // --- Directory Creation (Warehouse > Row > Pallet [> Carton]) ---

    suspend fun ensureWarehouseDirectory(warehouseName: String): File = withContext(Dispatchers.IO) {
        resolveWarehouseDir(warehouseName).apply { mkdirs() }
    }

    suspend fun ensureRowDirectory(
        warehouseName: String,
        rowName: String,
    ): File = withContext(Dispatchers.IO) {
        resolveRowDir(warehouseName, rowName).apply { mkdirs() }
    }

    suspend fun ensurePalletDirectory(
        warehouseName: String,
        rowName: String,
        palletName: String,
    ): File = withContext(Dispatchers.IO) {
        val palletDir = resolvePalletDir(warehouseName, rowName, palletName).apply { mkdirs() }
        resolveSafeChild(palletDir, PersianUtils.PALLET_TICKET_SUBFOLDER).mkdirs()
        palletDir
    }

    suspend fun ensureCartonDirectory(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String,
    ): File = withContext(Dispatchers.IO) {
        resolveCartonDir(warehouseName, rowName, palletName, cartonName).apply { mkdirs() }
    }

    suspend fun ensureTargetTicketDirectory(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String? = null,
    ): File = withContext(Dispatchers.IO) {
        resolveTargetTicketDir(warehouseName, rowName, palletName, cartonName).apply { mkdirs() }
    }

    // --- Saving, Moving, Reading & Deleting Image Files ---

    suspend fun saveBitmapImage(
        bitmap: Bitmap,
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String? = null,
        fileName: String,
        jpegQuality: Int = 90,
    ): StoredImageFile = withContext(Dispatchers.IO) {
        val safeFileName = sanitizeFileName(fileName)
        val targetDir = resolveTargetTicketDir(warehouseName, rowName, palletName, cartonName).apply { mkdirs() }
        val outFile = resolveSafeChild(targetDir, safeFileName)
        FileOutputStream(outFile).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality.coerceIn(50, 100), stream)) {
                throw IllegalStateException("خطا در نوشتن فایل تصویر در حافظه داخلی")
            }
            stream.flush()
        }
        toStoredImageFile(
            file = outFile,
            warehouseName = warehouseName,
            rowName = rowName,
            palletName = palletName,
            cartonName = cartonName,
        )
    }

    suspend fun saveImageBytes(
        bytes: ByteArray,
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String? = null,
        fileName: String,
    ): StoredImageFile = withContext(Dispatchers.IO) {
        val safeFileName = sanitizeFileName(fileName)
        val targetDir = resolveTargetTicketDir(warehouseName, rowName, palletName, cartonName).apply { mkdirs() }
        val outFile = resolveSafeChild(targetDir, safeFileName)
        FileOutputStream(outFile).use { stream ->
            stream.write(bytes)
            stream.flush()
        }
        toStoredImageFile(
            file = outFile,
            warehouseName = warehouseName,
            rowName = rowName,
            palletName = palletName,
            cartonName = cartonName,
        )
    }

    suspend fun moveImageFile(
        sourceUriOrPath: String,
        targetWarehouseName: String,
        targetRowName: String,
        targetPalletName: String,
        targetCartonName: String? = null,
        fileName: String,
    ): StoredImageFile? = withContext(Dispatchers.IO) {
        runCatching {
            val safeFileName = sanitizeFileName(fileName)
            val targetDir = resolveTargetTicketDir(
                targetWarehouseName,
                targetRowName,
                targetPalletName,
                targetCartonName,
            ).apply { mkdirs() }
            val destFile = resolveSafeChild(targetDir, safeFileName)

            val parsedUri = Uri.parse(sourceUriOrPath)
            val sourceFile = when (parsedUri.scheme) {
                "file" -> parsedUri.path?.let { File(it) }
                null -> File(sourceUriOrPath)
                else -> null
            }

            if (sourceFile != null && sourceFile.exists()) {
                if (sourceFile.canonicalPath == destFile.canonicalPath) {
                    return@runCatching toStoredImageFile(
                        file = destFile,
                        warehouseName = targetWarehouseName,
                        rowName = targetRowName,
                        palletName = targetPalletName,
                        cartonName = targetCartonName,
                    )
                }
                if (!sourceFile.renameTo(destFile)) {
                    sourceFile.copyTo(destFile, overwrite = true)
                    sourceFile.delete()
                }
                return@runCatching toStoredImageFile(
                    file = destFile,
                    warehouseName = targetWarehouseName,
                    rowName = targetRowName,
                    palletName = targetPalletName,
                    cartonName = targetCartonName,
                )
            }

            if (parsedUri.scheme == "content") {
                val bytes = context.contentResolver.openInputStream(parsedUri)?.use { it.readBytes() }
                    ?: return@runCatching null
                FileOutputStream(destFile).use { stream ->
                    stream.write(bytes)
                    stream.flush()
                }
                runCatching { context.contentResolver.delete(parsedUri, null, null) }
                return@runCatching toStoredImageFile(
                    file = destFile,
                    warehouseName = targetWarehouseName,
                    rowName = targetRowName,
                    palletName = targetPalletName,
                    cartonName = targetCartonName,
                )
            }

            null
        }.getOrNull()
    }

    suspend fun deleteImageFile(uriOrPath: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(uriOrPath)
            when (uri.scheme) {
                "content" -> context.contentResolver.delete(uri, null, null) > 0
                "file" -> uri.path?.let { deleteFileSafely(File(it)) } ?: false
                else -> deleteFileSafely(File(uriOrPath))
            }
        }.getOrDefault(false)
    }

    suspend fun listImages(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String? = null,
    ): List<File> = withContext(Dispatchers.IO) {
        val dir = resolveTargetTicketDir(warehouseName, rowName, palletName, cartonName)
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()
        dir.listFiles()
            ?.filter { it.isFile && isImageFile(it) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    fun resolveStoredFileUri(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String? = null,
        fileName: String,
    ): String {
        val dir = resolveTargetTicketDir(warehouseName, rowName, palletName, cartonName)
        val file = resolveSafeChild(dir, sanitizeFileName(fileName))
        return Uri.fromFile(file).toString()
    }

    // --- Renaming Physical Folders ---

    suspend fun renameWarehouseDirectory(oldName: String, newName: String): Boolean = withContext(Dispatchers.IO) {
        val oldDir = resolveWarehouseDir(oldName)
        val newDir = resolveWarehouseDir(newName)
        renameOrMergeDirectory(oldDir, newDir)
    }

    suspend fun renameRowDirectory(
        warehouseName: String,
        oldRowName: String,
        newRowName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val oldDir = resolveRowDir(warehouseName, oldRowName)
        val newDir = resolveRowDir(warehouseName, newRowName)
        renameOrMergeDirectory(oldDir, newDir)
    }

    suspend fun renamePalletDirectory(
        warehouseName: String,
        rowName: String,
        oldPalletName: String,
        newPalletName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val oldDir = resolvePalletDir(warehouseName, rowName, oldPalletName)
        val newDir = resolvePalletDir(warehouseName, rowName, newPalletName)
        renameOrMergeDirectory(oldDir, newDir)
    }

    suspend fun renameCartonDirectory(
        warehouseName: String,
        rowName: String,
        palletName: String,
        oldCartonName: String,
        newCartonName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        val oldDir = resolveCartonDir(warehouseName, rowName, palletName, oldCartonName)
        val newDir = resolveCartonDir(warehouseName, rowName, palletName, newCartonName)
        renameOrMergeDirectory(oldDir, newDir)
    }

    // --- Recursive Physical Folder Deletion ---

    suspend fun deleteWarehouseDirectory(warehouseName: String): Boolean = withContext(Dispatchers.IO) {
        deleteDirectorySafely(resolveWarehouseDir(warehouseName))
    }

    suspend fun deleteRowDirectory(warehouseName: String, rowName: String): Boolean = withContext(Dispatchers.IO) {
        deleteDirectorySafely(resolveRowDir(warehouseName, rowName))
    }

    suspend fun deletePalletDirectory(
        warehouseName: String,
        rowName: String,
        palletName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        deleteDirectorySafely(resolvePalletDir(warehouseName, rowName, palletName))
    }

    suspend fun deleteCartonDirectory(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        deleteDirectorySafely(resolveCartonDir(warehouseName, rowName, palletName, cartonName))
    }

    // --- Real Directory Tree Inspection ---

    suspend fun getDirectoryTree(): StorageFolderNode = withContext(Dispatchers.IO) {
        rootDir.mkdirs()
        buildFolderNode(rootDir, PersianUtils.ROOT_FOLDER_NAME)
    }

    suspend fun getStorageSummary(): InternalStorageSummary = withContext(Dispatchers.IO) {
        rootDir.mkdirs()
        val tree = buildFolderNode(rootDir, PersianUtils.ROOT_FOLDER_NAME)
        val warehouseFolders = tree.children.size
        val totalFolders = countFolders(tree) - 1 // exclude root itself
        InternalStorageSummary(
            rootAbsolutePath = rootDir.absolutePath,
            warehouseFolderCount = warehouseFolders,
            totalFolderCount = totalFolders.coerceAtLeast(0),
            totalImageCount = tree.totalImageCount,
            totalSizeBytes = tree.totalSizeBytes,
        )
    }

    // --- Internal Path Resolution & Safety Helpers ---

    private fun resolveWarehouseDir(warehouseName: String): File {
        return resolveSafeChild(rootDir, PersianUtils.sanitizeFolderSegment(warehouseName))
    }

    private fun resolveRowDir(warehouseName: String, rowName: String): File {
        return resolveSafeChild(resolveWarehouseDir(warehouseName), PersianUtils.sanitizeFolderSegment(rowName))
    }

    private fun resolvePalletDir(warehouseName: String, rowName: String, palletName: String): File {
        return resolveSafeChild(resolveRowDir(warehouseName, rowName), PersianUtils.sanitizeFolderSegment(palletName))
    }

    private fun resolveCartonDir(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String,
    ): File {
        val cartonsParent = resolveSafeChild(
            resolvePalletDir(warehouseName, rowName, palletName),
            PersianUtils.CARTONS_PARENT_SUBFOLDER,
        )
        return resolveSafeChild(cartonsParent, PersianUtils.sanitizeFolderSegment(cartonName))
    }

    private fun resolveTargetTicketDir(
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String?,
    ): File {
        return if (cartonName.isNullOrBlank()) {
            resolveSafeChild(
                resolvePalletDir(warehouseName, rowName, palletName),
                PersianUtils.PALLET_TICKET_SUBFOLDER,
            )
        } else {
            resolveCartonDir(warehouseName, rowName, palletName, cartonName)
        }
    }

    private fun resolveSafeChild(parent: File, segment: String): File {
        val candidate = File(parent, segment)
        val rootCanonical = rootDir.canonicalPath
        val candidateCanonical = candidate.canonicalPath
        require(candidateCanonical == rootCanonical || candidateCanonical.startsWith("$rootCanonical${File.separator}")) {
            "مسیر فایل خارج از پوشه مجاز حافظه داخلی است."
        }
        return candidate
    }

    private fun sanitizeFileName(rawFileName: String): String {
        val cleaned = rawFileName
            .trim()
            .replace(Regex("[/\\\\:*?\"<>|\\r\\n\\t]+"), "_")
            .trim('.', ' ')
        val base = cleaned.ifEmpty { "ticket_${System.currentTimeMillis()}.jpg" }
        return if (base.endsWith(".jpg", ignoreCase = true) || base.endsWith(".jpeg", ignoreCase = true)) {
            base
        } else {
            "$base.jpg"
        }
    }

    private fun toStoredImageFile(
        file: File,
        warehouseName: String,
        rowName: String,
        palletName: String,
        cartonName: String?,
    ): StoredImageFile {
        return StoredImageFile(
            file = file,
            fileUri = Uri.fromFile(file).toString(),
            relativeFolderPath = PersianUtils.buildTargetTicketFolderPath(
                warehouseName = warehouseName,
                rowName = rowName,
                palletName = palletName,
                cartonName = cartonName,
            ),
            fileName = file.name,
            sizeBytes = file.length(),
        )
    }

    private fun renameOrMergeDirectory(sourceDir: File, targetDir: File): Boolean {
        if (sourceDir.canonicalPath == targetDir.canonicalPath) {
            targetDir.mkdirs()
            return true
        }
        if (!sourceDir.exists()) {
            return targetDir.mkdirs() || targetDir.exists()
        }
        targetDir.parentFile?.mkdirs()
        if (!targetDir.exists()) {
            if (sourceDir.renameTo(targetDir)) {
                return true
            }
        }
        // Fallback: copy recursively into targetDir (merging safely if target exists) then delete sourceDir
        return runCatching {
            sourceDir.copyRecursively(targetDir, overwrite = true)
            sourceDir.deleteRecursively()
        }.getOrDefault(false)
    }

    private fun deleteDirectorySafely(dir: File): Boolean {
        if (!dir.exists()) return true
        val rootCanonical = rootDir.canonicalPath
        val dirCanonical = dir.canonicalPath
        if (dirCanonical == rootCanonical || !dirCanonical.startsWith("$rootCanonical${File.separator}")) {
            return false
        }
        return dir.deleteRecursively()
    }

    private fun deleteFileSafely(file: File): Boolean {
        if (!file.exists()) return false
        return file.delete()
    }

    private fun isImageFile(file: File): Boolean {
        val ext = file.extension.lowercase()
        return ext == "jpg" || ext == "jpeg" || ext == "png" || ext == "webp"
    }

    private fun buildFolderNode(dir: File, relativePath: String): StorageFolderNode {
        val entries = dir.listFiles()?.toList().orEmpty()
        val images = entries
            .filter { it.isFile && isImageFile(it) }
            .sortedByDescending { it.lastModified() }
        val childDirs = entries
            .filter { it.isDirectory }
            .sortedBy { it.name }
            .map { sub -> buildFolderNode(sub, "$relativePath/${sub.name}") }

        val ownSize = images.sumOf { it.length() }
        val totalImages = images.size + childDirs.sumOf { it.totalImageCount }
        val totalBytes = ownSize + childDirs.sumOf { it.totalSizeBytes }

        return StorageFolderNode(
            name = dir.name,
            relativePath = relativePath,
            absolutePath = dir.absolutePath,
            imageFiles = images,
            children = childDirs,
            totalImageCount = totalImages,
            totalSizeBytes = totalBytes,
        )
    }

    private fun countFolders(node: StorageFolderNode): Int {
        return 1 + node.children.sumOf { countFolders(it) }
    }
}
