package com.example.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "warehouses")
data class WarehouseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "rows",
    foreignKeys = [
        ForeignKey(
            entity = WarehouseEntity::class,
            parentColumns = ["id"],
            childColumns = ["warehouseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("warehouseId")],
)
data class RowEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val warehouseId: Long,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "pallets",
    foreignKeys = [
        ForeignKey(
            entity = RowEntity::class,
            parentColumns = ["id"],
            childColumns = ["rowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("rowId")],
)
data class PalletEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val rowId: Long,
    val name: String,
    val cartonCount: Int = 0,
    val itemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "cartons",
    foreignKeys = [
        ForeignKey(
            entity = PalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["palletId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("palletId")],
)
data class CartonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val palletId: Long,
    val name: String,
    val itemCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "ticket_photos",
    foreignKeys = [
        ForeignKey(
            entity = PalletEntity::class,
            parentColumns = ["id"],
            childColumns = ["palletId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = CartonEntity::class,
            parentColumns = ["id"],
            childColumns = ["cartonId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("palletId"), Index("cartonId")],
)
data class TicketPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val palletId: Long,
    val cartonId: Long? = null,
    val mediaUri: String,
    val relativeFolderPath: String,
    val fileName: String,
    val capturedAt: Long = System.currentTimeMillis(),
    val jalaliDateTime: String,
    val note: String = "",
)

@Entity(
    tableName = "cloud_synced_objects",
    foreignKeys = [
        ForeignKey(
            entity = TicketPhotoEntity::class,
            parentColumns = ["id"],
            childColumns = ["photoId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("photoId")],
)
data class CloudSyncedObjectEntity(
    @PrimaryKey val photoId: Long,
    val objectKey: String,
    val fileSizeBytes: Long,
    val syncedAt: Long = System.currentTimeMillis(),
)

data class GlobalStats(
    val warehouseCount: Int = 0,
    val palletCount: Int = 0,
    val totalCartons: Int = 0,
    val totalItems: Int = 0,
    val totalPhotos: Int = 0,
)

data class WarehouseSummary(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val rowCount: Int,
    val palletCount: Int,
    val totalCartons: Int,
    val totalItems: Int,
    val photoCount: Int,
)

data class RowSummary(
    val id: Long,
    val warehouseId: Long,
    val name: String,
    val createdAt: Long,
    val palletCount: Int,
    val totalCartons: Int,
    val totalItems: Int,
    val photoCount: Int,
)

data class PalletSummary(
    val id: Long,
    val rowId: Long,
    val name: String,
    val cartonCount: Int,
    val itemCount: Int,
    val createdAt: Long,
    val registeredCartonCount: Int,
    val cartonsTotalItems: Int,
    val palletPhotoCount: Int,
    val totalPhotoCount: Int,
)

data class CartonSummary(
    val id: Long,
    val palletId: Long,
    val name: String,
    val itemCount: Int,
    val createdAt: Long,
    val photoCount: Int,
)

data class PalletPathInfo(
    val warehouseId: Long,
    val warehouseName: String,
    val rowId: Long,
    val rowName: String,
    val palletId: Long,
    val palletName: String,
    val cartonCount: Int,
    val itemCount: Int,
)

data class CartonPathInfo(
    val warehouseId: Long,
    val warehouseName: String,
    val rowId: Long,
    val rowName: String,
    val palletId: Long,
    val palletName: String,
    val cartonId: Long,
    val cartonName: String,
    val itemCount: Int,
)

data class RowPathInfo(
    val warehouseId: Long,
    val warehouseName: String,
    val rowId: Long,
    val rowName: String,
)

data class TicketDetailItem(
    val id: Long,
    val palletId: Long,
    val cartonId: Long?,
    val warehouseId: Long,
    val warehouseName: String,
    val rowId: Long,
    val rowName: String,
    val palletName: String,
    val cartonName: String?,
    val palletCartonCount: Int,
    val palletItemCount: Int,
    val cartonItemCount: Int?,
    val mediaUri: String,
    val relativeFolderPath: String,
    val fileName: String,
    val capturedAt: Long,
    val jalaliDateTime: String,
    val note: String,
) {
    val isCartonTicket: Boolean get() = cartonId != null
}

data class RowReportItem(
    val warehouseId: Long,
    val warehouseName: String,
    val rowId: Long,
    val rowName: String,
    val palletCount: Int,
    val totalCartons: Int,
    val registeredCartonFolders: Int,
    val totalItems: Int,
    val palletTicketCount: Int,
    val cartonTicketCount: Int,
    val totalTicketCount: Int,
)

data class PalletOptionItem(
    val warehouseId: Long,
    val warehouseName: String,
    val rowId: Long,
    val rowName: String,
    val palletId: Long,
    val palletName: String,
    val cartonCount: Int,
    val itemCount: Int,
)
