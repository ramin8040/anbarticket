package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WarehouseDao {

    // --- Global Stats ---
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM warehouses) AS warehouseCount,
            (SELECT COUNT(*) FROM pallets) AS palletCount,
            COALESCE((
                SELECT SUM(
                    MAX(p.cartonCount, (SELECT COUNT(*) FROM cartons c WHERE c.palletId = p.id))
                ) FROM pallets p
            ), 0) AS totalCartons,
            COALESCE((
                SELECT SUM(
                    MAX(p.itemCount, COALESCE((SELECT SUM(c.itemCount) FROM cartons c WHERE c.palletId = p.id), 0))
                ) FROM pallets p
            ), 0) AS totalItems,
            (SELECT COUNT(*) FROM ticket_photos) AS totalPhotos
        """
    )
    fun observeGlobalStats(): Flow<GlobalStats>

    // --- Warehouses ---
    @Query(
        """
        SELECT
            w.id,
            w.name,
            w.createdAt,
            (SELECT COUNT(*) FROM rows r WHERE r.warehouseId = w.id) AS rowCount,
            (SELECT COUNT(*) FROM pallets p INNER JOIN rows r ON p.rowId = r.id WHERE r.warehouseId = w.id) AS palletCount,
            COALESCE((
                SELECT SUM(
                    MAX(p.cartonCount, (SELECT COUNT(*) FROM cartons c WHERE c.palletId = p.id))
                ) FROM pallets p INNER JOIN rows r ON p.rowId = r.id WHERE r.warehouseId = w.id
            ), 0) AS totalCartons,
            COALESCE((
                SELECT SUM(
                    MAX(p.itemCount, COALESCE((SELECT SUM(c.itemCount) FROM cartons c WHERE c.palletId = p.id), 0))
                ) FROM pallets p INNER JOIN rows r ON p.rowId = r.id WHERE r.warehouseId = w.id
            ), 0) AS totalItems,
            (SELECT COUNT(*) FROM ticket_photos tp INNER JOIN pallets p ON tp.palletId = p.id INNER JOIN rows r ON p.rowId = r.id WHERE r.warehouseId = w.id) AS photoCount
        FROM warehouses w
        ORDER BY w.createdAt DESC
        """
    )
    fun observeWarehouseSummaries(): Flow<List<WarehouseSummary>>

    @Query("SELECT * FROM warehouses WHERE id = :warehouseId LIMIT 1")
    fun observeWarehouse(warehouseId: Long): Flow<WarehouseEntity?>

    @Query("SELECT COUNT(*) FROM warehouses WHERE TRIM(name) = TRIM(:name) AND id != :excludeId")
    suspend fun countWarehousesWithName(name: String, excludeId: Long = -1L): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertWarehouse(warehouse: WarehouseEntity): Long

    @Query("UPDATE warehouses SET name = :name WHERE id = :id")
    suspend fun updateWarehouseName(id: Long, name: String)

    @Query("DELETE FROM warehouses WHERE id = :id")
    suspend fun deleteWarehouseById(id: Long)

    // --- Rows ---
    @Query(
        """
        SELECT
            r.id,
            r.warehouseId,
            r.name,
            r.createdAt,
            (SELECT COUNT(*) FROM pallets p WHERE p.rowId = r.id) AS palletCount,
            COALESCE((
                SELECT SUM(
                    MAX(p.cartonCount, (SELECT COUNT(*) FROM cartons c WHERE c.palletId = p.id))
                ) FROM pallets p WHERE p.rowId = r.id
            ), 0) AS totalCartons,
            COALESCE((
                SELECT SUM(
                    MAX(p.itemCount, COALESCE((SELECT SUM(c.itemCount) FROM cartons c WHERE c.palletId = p.id), 0))
                ) FROM pallets p WHERE p.rowId = r.id
            ), 0) AS totalItems,
            (SELECT COUNT(*) FROM ticket_photos tp INNER JOIN pallets p ON tp.palletId = p.id WHERE p.rowId = r.id) AS photoCount
        FROM rows r
        WHERE r.warehouseId = :warehouseId
        ORDER BY r.createdAt DESC
        """
    )
    fun observeRowSummaries(warehouseId: Long): Flow<List<RowSummary>>

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName
        FROM rows r
        INNER JOIN warehouses w ON r.warehouseId = w.id
        WHERE r.id = :rowId
        LIMIT 1
        """
    )
    fun observeRowPathInfo(rowId: Long): Flow<RowPathInfo?>

    @Query("SELECT COUNT(*) FROM rows WHERE warehouseId = :warehouseId AND TRIM(name) = TRIM(:name) AND id != :excludeId")
    suspend fun countRowsWithName(warehouseId: Long, name: String, excludeId: Long = -1L): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRow(row: RowEntity): Long

    @Query("UPDATE rows SET name = :name WHERE id = :id")
    suspend fun updateRowName(id: Long, name: String)

    @Query("DELETE FROM rows WHERE id = :id")
    suspend fun deleteRowById(id: Long)

    // --- Pallets ---
    @Query(
        """
        SELECT
            p.id,
            p.rowId,
            p.name,
            p.cartonCount,
            p.itemCount,
            p.createdAt,
            (SELECT COUNT(*) FROM cartons c WHERE c.palletId = p.id) AS registeredCartonCount,
            COALESCE((SELECT SUM(c.itemCount) FROM cartons c WHERE c.palletId = p.id), 0) AS cartonsTotalItems,
            (SELECT COUNT(*) FROM ticket_photos tp WHERE tp.palletId = p.id AND tp.cartonId IS NULL) AS palletPhotoCount,
            (SELECT COUNT(*) FROM ticket_photos tp WHERE tp.palletId = p.id) AS totalPhotoCount
        FROM pallets p
        WHERE p.rowId = :rowId
        ORDER BY p.createdAt DESC
        """
    )
    fun observePalletSummaries(rowId: Long): Flow<List<PalletSummary>>

    @Query(
        """
        SELECT
            p.id,
            p.rowId,
            p.name,
            p.cartonCount,
            p.itemCount,
            p.createdAt,
            (SELECT COUNT(*) FROM cartons c WHERE c.palletId = p.id) AS registeredCartonCount,
            COALESCE((SELECT SUM(c.itemCount) FROM cartons c WHERE c.palletId = p.id), 0) AS cartonsTotalItems,
            (SELECT COUNT(*) FROM ticket_photos tp WHERE tp.palletId = p.id AND tp.cartonId IS NULL) AS palletPhotoCount,
            (SELECT COUNT(*) FROM ticket_photos tp WHERE tp.palletId = p.id) AS totalPhotoCount
        FROM pallets p
        WHERE p.id = :palletId
        LIMIT 1
        """
    )
    fun observePalletSummary(palletId: Long): Flow<PalletSummary?>

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.id AS palletId,
            p.name AS palletName,
            p.cartonCount AS cartonCount,
            p.itemCount AS itemCount
        FROM pallets p
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        WHERE p.id = :palletId
        LIMIT 1
        """
    )
    fun observePalletPathInfo(palletId: Long): Flow<PalletPathInfo?>

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.id AS palletId,
            p.name AS palletName,
            p.cartonCount AS cartonCount,
            p.itemCount AS itemCount
        FROM pallets p
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        WHERE p.id = :palletId
        LIMIT 1
        """
    )
    suspend fun getPalletPathInfoOnce(palletId: Long): PalletPathInfo?

    @Query("SELECT COUNT(*) FROM pallets WHERE rowId = :rowId AND TRIM(name) = TRIM(:name) AND id != :excludeId")
    suspend fun countPalletsWithName(rowId: Long, name: String, excludeId: Long = -1L): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPallet(pallet: PalletEntity): Long

    @Query("UPDATE pallets SET name = :name, cartonCount = :cartonCount, itemCount = :itemCount WHERE id = :id")
    suspend fun updatePallet(id: Long, name: String, cartonCount: Int, itemCount: Int)

    @Query("DELETE FROM pallets WHERE id = :id")
    suspend fun deletePalletById(id: Long)

    // --- Cartons ---
    @Query(
        """
        SELECT
            c.id,
            c.palletId,
            c.name,
            c.itemCount,
            c.createdAt,
            (SELECT COUNT(*) FROM ticket_photos tp WHERE tp.cartonId = c.id) AS photoCount
        FROM cartons c
        WHERE c.palletId = :palletId
        ORDER BY c.createdAt DESC
        """
    )
    fun observeCartonSummaries(palletId: Long): Flow<List<CartonSummary>>

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.id AS palletId,
            p.name AS palletName,
            c.id AS cartonId,
            c.name AS cartonName,
            c.itemCount AS itemCount
        FROM cartons c
        INNER JOIN pallets p ON c.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        WHERE c.id = :cartonId
        LIMIT 1
        """
    )
    fun observeCartonPathInfo(cartonId: Long): Flow<CartonPathInfo?>

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.id AS palletId,
            p.name AS palletName,
            c.id AS cartonId,
            c.name AS cartonName,
            c.itemCount AS itemCount
        FROM cartons c
        INNER JOIN pallets p ON c.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        WHERE c.id = :cartonId
        LIMIT 1
        """
    )
    suspend fun getCartonPathInfoOnce(cartonId: Long): CartonPathInfo?

    @Query("SELECT COUNT(*) FROM cartons WHERE palletId = :palletId AND TRIM(name) = TRIM(:name) AND id != :excludeId")
    suspend fun countCartonsWithName(palletId: Long, name: String, excludeId: Long = -1L): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCarton(carton: CartonEntity): Long

    @Query("UPDATE cartons SET name = :name, itemCount = :itemCount WHERE id = :id")
    suspend fun updateCarton(id: Long, name: String, itemCount: Int)

    @Query("DELETE FROM cartons WHERE id = :id")
    suspend fun deleteCartonById(id: Long)

    // --- Ticket Photos ---
    @Query(
        """
        SELECT * FROM ticket_photos
        WHERE palletId = :palletId AND cartonId IS NULL
        ORDER BY capturedAt DESC
        """
    )
    fun observePalletTickets(palletId: Long): Flow<List<TicketPhotoEntity>>

    @Query(
        """
        SELECT * FROM ticket_photos
        WHERE cartonId = :cartonId
        ORDER BY capturedAt DESC
        """
    )
    fun observeCartonTickets(cartonId: Long): Flow<List<TicketPhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicketPhoto(photo: TicketPhotoEntity): Long

    @Query("SELECT * FROM ticket_photos WHERE id = :photoId LIMIT 1")
    suspend fun getTicketPhotoById(photoId: Long): TicketPhotoEntity?

    @Query("DELETE FROM ticket_photos WHERE id = :photoId")
    suspend fun deleteTicketPhotoById(photoId: Long)

    // --- Photo URI lookup helpers for real file cleanup before cascade delete ---
    @Query(
        """
        SELECT tp.mediaUri FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        WHERE r.warehouseId = :warehouseId
        """
    )
    suspend fun getPhotoUrisForWarehouse(warehouseId: Long): List<String>

    @Query(
        """
        SELECT tp.mediaUri FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        WHERE p.rowId = :rowId
        """
    )
    suspend fun getPhotoUrisForRow(rowId: Long): List<String>

    @Query("SELECT mediaUri FROM ticket_photos WHERE palletId = :palletId")
    suspend fun getPhotoUrisForPallet(palletId: Long): List<String>

    @Query("SELECT mediaUri FROM ticket_photos WHERE cartonId = :cartonId")
    suspend fun getPhotoUrisForCarton(cartonId: Long): List<String>

    // --- Advanced Ticket Search & Single Ticket Detail ---
    @Query(
        """
        SELECT
            tp.id AS id,
            tp.palletId AS palletId,
            tp.cartonId AS cartonId,
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.name AS palletName,
            c.name AS cartonName,
            p.cartonCount AS palletCartonCount,
            p.itemCount AS palletItemCount,
            c.itemCount AS cartonItemCount,
            tp.mediaUri AS mediaUri,
            tp.relativeFolderPath AS relativeFolderPath,
            tp.fileName AS fileName,
            tp.capturedAt AS capturedAt,
            tp.jalaliDateTime AS jalaliDateTime,
            tp.note AS note
        FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        LEFT JOIN cartons c ON tp.cartonId = c.id
        ORDER BY tp.capturedAt DESC
        """
    )
    fun observeAllTicketDetails(): Flow<List<TicketDetailItem>>

    @Query(
        """
        SELECT
            tp.id AS id,
            tp.palletId AS palletId,
            tp.cartonId AS cartonId,
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.name AS palletName,
            c.name AS cartonName,
            p.cartonCount AS palletCartonCount,
            p.itemCount AS palletItemCount,
            c.itemCount AS cartonItemCount,
            tp.mediaUri AS mediaUri,
            tp.relativeFolderPath AS relativeFolderPath,
            tp.fileName AS fileName,
            tp.capturedAt AS capturedAt,
            tp.jalaliDateTime AS jalaliDateTime,
            tp.note AS note
        FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        LEFT JOIN cartons c ON tp.cartonId = c.id
        WHERE tp.id = :photoId
        LIMIT 1
        """
    )
    fun observeTicketDetailById(photoId: Long): Flow<TicketDetailItem?>

    @Query(
        """
        UPDATE ticket_photos
        SET palletId = :palletId,
            cartonId = :cartonId,
            mediaUri = :mediaUri,
            relativeFolderPath = :relativeFolderPath,
            capturedAt = :capturedAt,
            jalaliDateTime = :jalaliDateTime,
            note = :note
        WHERE id = :photoId
        """
    )
    suspend fun updateTicketPhoto(
        photoId: Long,
        palletId: Long,
        cartonId: Long?,
        mediaUri: String,
        relativeFolderPath: String,
        capturedAt: Long,
        jalaliDateTime: String,
        note: String,
    )

    // --- Reporting Queries ---
    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            (SELECT COUNT(*) FROM pallets p WHERE p.rowId = r.id) AS palletCount,
            COALESCE((
                SELECT SUM(
                    MAX(p.cartonCount, (SELECT COUNT(*) FROM cartons c WHERE c.palletId = p.id))
                ) FROM pallets p WHERE p.rowId = r.id
            ), 0) AS totalCartons,
            (SELECT COUNT(*) FROM cartons c INNER JOIN pallets p ON c.palletId = p.id WHERE p.rowId = r.id) AS registeredCartonFolders,
            COALESCE((
                SELECT SUM(
                    MAX(p.itemCount, COALESCE((SELECT SUM(c.itemCount) FROM cartons c WHERE c.palletId = p.id), 0))
                ) FROM pallets p WHERE p.rowId = r.id
            ), 0) AS totalItems,
            (SELECT COUNT(*) FROM ticket_photos tp INNER JOIN pallets p ON tp.palletId = p.id WHERE p.rowId = r.id AND tp.cartonId IS NULL) AS palletTicketCount,
            (SELECT COUNT(*) FROM ticket_photos tp INNER JOIN pallets p ON tp.palletId = p.id WHERE p.rowId = r.id AND tp.cartonId IS NOT NULL) AS cartonTicketCount,
            (SELECT COUNT(*) FROM ticket_photos tp INNER JOIN pallets p ON tp.palletId = p.id WHERE p.rowId = r.id) AS totalTicketCount
        FROM rows r
        INNER JOIN warehouses w ON r.warehouseId = w.id
        ORDER BY w.name ASC, r.name ASC
        """
    )
    fun observeRowReportItems(): Flow<List<RowReportItem>>

    @Query("SELECT * FROM pallets ORDER BY createdAt DESC")
    fun observeAllPallets(): Flow<List<PalletEntity>>

    @Query("SELECT * FROM cartons ORDER BY createdAt DESC")
    fun observeAllCartons(): Flow<List<CartonEntity>>

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.id AS palletId,
            p.name AS palletName,
            p.cartonCount AS cartonCount,
            p.itemCount AS itemCount
        FROM pallets p
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        ORDER BY w.name ASC, r.name ASC, p.name ASC
        """
    )
    fun observeAllPalletOptions(): Flow<List<PalletOptionItem>>

    // --- One-shot lookups & path synchronization helpers for InternalImageFolderManager ---
    @Query("SELECT * FROM warehouses WHERE id = :warehouseId LIMIT 1")
    suspend fun getWarehouseByIdOnce(warehouseId: Long): WarehouseEntity?

    @Query(
        """
        SELECT
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName
        FROM rows r
        INNER JOIN warehouses w ON r.warehouseId = w.id
        WHERE r.id = :rowId
        LIMIT 1
        """
    )
    suspend fun getRowPathInfoOnce(rowId: Long): RowPathInfo?

    @Query(
        """
        SELECT
            tp.id AS id,
            tp.palletId AS palletId,
            tp.cartonId AS cartonId,
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.name AS palletName,
            c.name AS cartonName,
            p.cartonCount AS palletCartonCount,
            p.itemCount AS palletItemCount,
            c.itemCount AS cartonItemCount,
            tp.mediaUri AS mediaUri,
            tp.relativeFolderPath AS relativeFolderPath,
            tp.fileName AS fileName,
            tp.capturedAt AS capturedAt,
            tp.jalaliDateTime AS jalaliDateTime,
            tp.note AS note
        FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        LEFT JOIN cartons c ON tp.cartonId = c.id
        WHERE w.id = :warehouseId
        """
    )
    suspend fun getTicketDetailsForWarehouse(warehouseId: Long): List<TicketDetailItem>

    @Query(
        """
        SELECT
            tp.id AS id,
            tp.palletId AS palletId,
            tp.cartonId AS cartonId,
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.name AS palletName,
            c.name AS cartonName,
            p.cartonCount AS palletCartonCount,
            p.itemCount AS palletItemCount,
            c.itemCount AS cartonItemCount,
            tp.mediaUri AS mediaUri,
            tp.relativeFolderPath AS relativeFolderPath,
            tp.fileName AS fileName,
            tp.capturedAt AS capturedAt,
            tp.jalaliDateTime AS jalaliDateTime,
            tp.note AS note
        FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        LEFT JOIN cartons c ON tp.cartonId = c.id
        WHERE r.id = :rowId
        """
    )
    suspend fun getTicketDetailsForRow(rowId: Long): List<TicketDetailItem>

    @Query(
        """
        SELECT
            tp.id AS id,
            tp.palletId AS palletId,
            tp.cartonId AS cartonId,
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.name AS palletName,
            c.name AS cartonName,
            p.cartonCount AS palletCartonCount,
            p.itemCount AS palletItemCount,
            c.itemCount AS cartonItemCount,
            tp.mediaUri AS mediaUri,
            tp.relativeFolderPath AS relativeFolderPath,
            tp.fileName AS fileName,
            tp.capturedAt AS capturedAt,
            tp.jalaliDateTime AS jalaliDateTime,
            tp.note AS note
        FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        LEFT JOIN cartons c ON tp.cartonId = c.id
        WHERE p.id = :palletId
        """
    )
    suspend fun getTicketDetailsForPallet(palletId: Long): List<TicketDetailItem>

    @Query(
        """
        SELECT
            tp.id AS id,
            tp.palletId AS palletId,
            tp.cartonId AS cartonId,
            w.id AS warehouseId,
            w.name AS warehouseName,
            r.id AS rowId,
            r.name AS rowName,
            p.name AS palletName,
            c.name AS cartonName,
            p.cartonCount AS palletCartonCount,
            p.itemCount AS palletItemCount,
            c.itemCount AS cartonItemCount,
            tp.mediaUri AS mediaUri,
            tp.relativeFolderPath AS relativeFolderPath,
            tp.fileName AS fileName,
            tp.capturedAt AS capturedAt,
            tp.jalaliDateTime AS jalaliDateTime,
            tp.note AS note
        FROM ticket_photos tp
        INNER JOIN pallets p ON tp.palletId = p.id
        INNER JOIN rows r ON p.rowId = r.id
        INNER JOIN warehouses w ON r.warehouseId = w.id
        LEFT JOIN cartons c ON tp.cartonId = c.id
        WHERE tp.cartonId = :cartonId
        """
    )
    suspend fun getTicketDetailsForCarton(cartonId: Long): List<TicketDetailItem>

    @Query("UPDATE ticket_photos SET mediaUri = :mediaUri, relativeFolderPath = :relativeFolderPath WHERE id = :photoId")
    suspend fun updateTicketPhotoPath(photoId: Long, mediaUri: String, relativeFolderPath: String)

    // --- Cloud Backup & Restore Queries ---
    @Query("SELECT * FROM warehouses ORDER BY id ASC")
    suspend fun getAllWarehousesOnce(): List<WarehouseEntity>

    @Query("SELECT * FROM rows ORDER BY id ASC")
    suspend fun getAllRowsOnce(): List<RowEntity>

    @Query("SELECT * FROM pallets ORDER BY id ASC")
    suspend fun getAllPalletsOnce(): List<PalletEntity>

    @Query("SELECT * FROM cartons ORDER BY id ASC")
    suspend fun getAllCartonsOnce(): List<CartonEntity>

    @Query("SELECT * FROM ticket_photos ORDER BY id ASC")
    suspend fun getAllTicketPhotosOnce(): List<TicketPhotoEntity>

    @Query("SELECT * FROM cloud_synced_objects")
    suspend fun getAllSyncedObjectsOnce(): List<CloudSyncedObjectEntity>

    @Query("SELECT COUNT(*) FROM cloud_synced_objects")
    fun observeSyncedPhotoCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncedObject(record: CloudSyncedObjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncedObjects(records: List<CloudSyncedObjectEntity>)

    @Query("DELETE FROM cloud_synced_objects")
    suspend fun clearAllSyncedObjects()

    @Query("DELETE FROM ticket_photos")
    suspend fun clearAllTicketPhotos()

    @Query("DELETE FROM cartons")
    suspend fun clearAllCartons()

    @Query("DELETE FROM pallets")
    suspend fun clearAllPallets()

    @Query("DELETE FROM rows")
    suspend fun clearAllRows()

    @Query("DELETE FROM warehouses")
    suspend fun clearAllWarehouses()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWarehousesBatch(items: List<WarehouseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRowsBatch(items: List<RowEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPalletsBatch(items: List<PalletEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartonsBatch(items: List<CartonEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicketPhotosBatch(items: List<TicketPhotoEntity>)
}
