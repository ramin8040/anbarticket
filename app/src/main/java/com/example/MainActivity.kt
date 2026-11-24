package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.TopLevelTab
import com.example.ui.screens.AdvancedSearchScreen
import com.example.ui.screens.CartonDetailScreen
import com.example.ui.screens.CloudBackupScreen
import com.example.ui.screens.PalletDetailScreen
import com.example.ui.screens.PalletListScreen
import com.example.ui.screens.PhotoViewerScreen
import com.example.ui.screens.RapidCameraScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.screens.RowListScreen
import com.example.ui.screens.WarehouseListScreen
import com.example.ui.screens.WarehouseMapScreen
import com.example.ui.theme.AnbarTicketTheme
import com.example.ui.viewmodel.AnbarViewModel
import com.example.util.PersianUtils

private object Routes {
    const val WAREHOUSES = "warehouses"
    const val CLOUD_BACKUP = "cloud_backup"
    const val MAP = "map"
    const val MAP_WAREHOUSE = "map/{warehouseId}"
    const val SEARCH = "search"
    const val REPORTS = "reports"
    const val ROWS = "rows/{warehouseId}"
    const val PALLETS = "pallets/{rowId}"
    const val PALLET_DETAIL = "pallet_detail/{palletId}"
    const val CARTON_DETAIL = "carton_detail/{palletId}/{cartonId}"
    const val CAMERA_PALLET = "camera_pallet/{palletId}"
    const val CAMERA_CARTON = "camera_carton/{palletId}/{cartonId}"
    const val VIEWER_PALLET = "viewer_pallet/{palletId}/{initialIndex}"
    const val VIEWER_CARTON = "viewer_carton/{cartonId}/{initialIndex}"
    const val VIEWER_SINGLE = "viewer_single/{photoId}"

    fun mapWarehouse(warehouseId: Long) = "map/$warehouseId"
    fun rows(warehouseId: Long) = "rows/$warehouseId"
    fun pallets(rowId: Long) = "pallets/$rowId"
    fun palletDetail(palletId: Long) = "pallet_detail/$palletId"
    fun cartonDetail(palletId: Long, cartonId: Long) = "carton_detail/$palletId/$cartonId"
    fun cameraPallet(palletId: Long) = "camera_pallet/$palletId"
    fun cameraCarton(palletId: Long, cartonId: Long) = "camera_carton/$palletId/$cartonId"
    fun viewerPallet(palletId: Long, initialIndex: Int) = "viewer_pallet/$palletId/$initialIndex"
    fun viewerCarton(cartonId: Long, initialIndex: Int) = "viewer_carton/$cartonId/$initialIndex"
    fun viewerSingle(photoId: Long) = "viewer_single/$photoId"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: AnbarViewModel = viewModel()
            val isDarkTheme by viewModel.isDarkTheme.collectAsStateWithLifecycle()

            LaunchedEffect(isDarkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                    navigationBarStyle = if (isDarkTheme) {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    },
                )
            }

            AnbarTicketTheme(darkTheme = isDarkTheme) {
                AnbarTicketNavApp(
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                )
            }
        }
    }
}

@Composable
private fun AnbarTicketNavApp(
    viewModel: AnbarViewModel,
    isDarkTheme: Boolean,
) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val globalStats by viewModel.globalStats.collectAsStateWithLifecycle()
    val warehouses by viewModel.warehouses.collectAsStateWithLifecycle()
    val allTicketDetails by viewModel.allTicketDetails.collectAsStateWithLifecycle()
    val rowReportItems by viewModel.rowReportItems.collectAsStateWithLifecycle()
    val allPallets by viewModel.allPallets.collectAsStateWithLifecycle()
    val allCartons by viewModel.allCartons.collectAsStateWithLifecycle()
    val allPalletOptions by viewModel.allPalletOptions.collectAsStateWithLifecycle()
    val watermarkEnabled by viewModel.watermarkEnabled.collectAsStateWithLifecycle()
    val cloudBackupPrefs by viewModel.cloudBackupPreferences.collectAsStateWithLifecycle()
    val syncedPhotoCount by viewModel.syncedPhotoCount.collectAsStateWithLifecycle()
    val cloudTransferProgress by viewModel.cloudTransferProgress.collectAsStateWithLifecycle()
    val localZipProgress by viewModel.localZipProgress.collectAsStateWithLifecycle()
    val pendingRestorePreview by viewModel.pendingRestorePreview.collectAsStateWithLifecycle()
    val isTestingCloudConnection by viewModel.isTestingCloudConnection.collectAsStateWithLifecycle()
    val isImportingGallery by viewModel.isImportingGallery.collectAsStateWithLifecycle()
    val cameraSession by viewModel.cameraSession.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.snackbarMessages.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    val navigateToTopTab: (TopLevelTab) -> Unit = { tab ->
        val destination = when (tab) {
            TopLevelTab.WAREHOUSES -> Routes.WAREHOUSES
            TopLevelTab.MAP -> Routes.MAP
            TopLevelTab.SEARCH -> Routes.SEARCH
            TopLevelTab.REPORTS -> Routes.REPORTS
        }
        navController.navigate(destination) {
            popUpTo(Routes.WAREHOUSES) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = Routes.WAREHOUSES,
    ) {
        composable(Routes.WAREHOUSES) {
            WarehouseListScreen(
                warehouses = warehouses,
                globalStats = globalStats,
                isCloudConfigured = viewModel.cloudStorageConfig.isConfigured,
                cloudBackupPrefs = cloudBackupPrefs,
                syncedPhotoCount = syncedPhotoCount,
                isCloudSyncRunning = cloudTransferProgress.isRunning || localZipProgress.isRunning,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onToggleTheme = viewModel::toggleTheme,
                onOpenCloudBackup = { navController.navigate(Routes.CLOUD_BACKUP) },
                onSelectTab = navigateToTopTab,
                onOpenWarehouse = { warehouseId ->
                    navController.navigate(Routes.rows(warehouseId))
                },
                onAddWarehouse = viewModel::addWarehouse,
                onRenameWarehouse = viewModel::renameWarehouse,
                onDeleteWarehouse = viewModel::deleteWarehouse,
            )
        }

        composable(Routes.CLOUD_BACKUP) {
            CloudBackupScreen(
                config = viewModel.cloudStorageConfig,
                backupPrefs = cloudBackupPrefs,
                globalStats = globalStats,
                syncedPhotoCount = syncedPhotoCount,
                transferProgress = cloudTransferProgress,
                localZipProgress = localZipProgress,
                pendingRestorePreview = pendingRestorePreview,
                isTestingConnection = isTestingCloudConnection,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onToggleTheme = viewModel::toggleTheme,
                onSuggestedBackupFileName = viewModel::suggestedBackupFileName,
                onExportLocalBackupZip = viewModel::exportLocalBackupZip,
                onShareLocalBackupZip = viewModel::shareLocalBackupZip,
                onPickLocalBackupForRestore = viewModel::onBackupFilePickedForRestore,
                onDismissLocalRestorePreview = viewModel::dismissRestorePreview,
                onConfirmLocalRestore = viewModel::confirmLocalRestore,
                onSetAutoBackupEnabled = viewModel::setAutoBackupEnabled,
                onSetWifiOnly = viewModel::setBackupWifiOnly,
                onSetInterval = viewModel::setBackupInterval,
                onBackupNow = viewModel::backupNowToCloud,
                onTestConnection = viewModel::testCloudConnection,
                onRestoreFromCloud = viewModel::restoreFromCloud,
            )
        }

        composable(Routes.MAP) {
            WarehouseMapScreen(
                warehouses = warehouses,
                rowReportItems = rowReportItems,
                allPallets = allPallets,
                allCartons = allCartons,
                allTickets = allTicketDetails,
                initialWarehouseId = null,
                showBottomBar = true,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onBack = null,
                onToggleTheme = viewModel::toggleTheme,
                onSelectTab = navigateToTopTab,
                onOpenWarehouseRows = { warehouseId ->
                    navController.navigate(Routes.rows(warehouseId))
                },
                onOpenRow = { rowId ->
                    navController.navigate(Routes.pallets(rowId))
                },
                onOpenPallet = { palletId ->
                    navController.navigate(Routes.palletDetail(palletId))
                },
                onQuickCameraPallet = { palletId ->
                    viewModel.resetCameraSession()
                    navController.navigate(Routes.cameraPallet(palletId))
                },
                onGoToWarehouses = { navigateToTopTab(TopLevelTab.WAREHOUSES) },
            )
        }

        composable(
            route = Routes.MAP_WAREHOUSE,
            arguments = listOf(navArgument("warehouseId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val warehouseId = backStackEntry.arguments?.getLong("warehouseId")
            WarehouseMapScreen(
                warehouses = warehouses,
                rowReportItems = rowReportItems,
                allPallets = allPallets,
                allCartons = allCartons,
                allTickets = allTicketDetails,
                initialWarehouseId = warehouseId,
                showBottomBar = false,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onToggleTheme = viewModel::toggleTheme,
                onSelectTab = navigateToTopTab,
                onOpenWarehouseRows = { whId ->
                    navController.navigate(Routes.rows(whId))
                },
                onOpenRow = { rowId ->
                    navController.navigate(Routes.pallets(rowId))
                },
                onOpenPallet = { palletId ->
                    navController.navigate(Routes.palletDetail(palletId))
                },
                onQuickCameraPallet = { palletId ->
                    viewModel.resetCameraSession()
                    navController.navigate(Routes.cameraPallet(palletId))
                },
                onGoToWarehouses = { navigateToTopTab(TopLevelTab.WAREHOUSES) },
            )
        }

        composable(Routes.SEARCH) {
            AdvancedSearchScreen(
                allTickets = allTicketDetails,
                palletOptions = allPalletOptions,
                allCartons = allCartons,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onToggleTheme = viewModel::toggleTheme,
                onSelectTab = navigateToTopTab,
                onOpenTicketViewer = { photoId ->
                    navController.navigate(Routes.viewerSingle(photoId))
                },
                onUpdateTicket = viewModel::updateTicket,
                onDeleteTicket = viewModel::deletePhoto,
            )
        }

        composable(Routes.REPORTS) {
            ReportsScreen(
                warehouses = warehouses,
                rowReportItems = rowReportItems,
                allPallets = allPallets,
                allCartons = allCartons,
                allTickets = allTicketDetails,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onToggleTheme = viewModel::toggleTheme,
                onSelectTab = navigateToTopTab,
                onOpenRow = { rowId ->
                    navController.navigate(Routes.pallets(rowId))
                },
            )
        }

        composable(
            route = Routes.ROWS,
            arguments = listOf(navArgument("warehouseId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val warehouseId = backStackEntry.arguments?.getLong("warehouseId") ?: return@composable
            val warehouse by remember(warehouseId) { viewModel.observeWarehouse(warehouseId) }
                .collectAsStateWithLifecycle(initialValue = null)
            val rows by remember(warehouseId) { viewModel.observeRows(warehouseId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

            RowListScreen(
                warehouse = warehouse,
                rows = rows,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onToggleTheme = viewModel::toggleTheme,
                onOpenMap = { navController.navigate(Routes.mapWarehouse(warehouseId)) },
                onOpenRow = { rowId -> navController.navigate(Routes.pallets(rowId)) },
                onAddRow = { name, cb -> viewModel.addRow(warehouseId, name, cb) },
                onRenameRow = { rowId, name, cb -> viewModel.renameRow(warehouseId, rowId, name, cb) },
                onDeleteRow = viewModel::deleteRow,
            )
        }

        composable(
            route = Routes.PALLETS,
            arguments = listOf(navArgument("rowId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val rowId = backStackEntry.arguments?.getLong("rowId") ?: return@composable
            val rowInfo by remember(rowId) { viewModel.observeRowPathInfo(rowId) }
                .collectAsStateWithLifecycle(initialValue = null)
            val pallets by remember(rowId) { viewModel.observePallets(rowId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

            PalletListScreen(
                rowInfo = rowInfo,
                pallets = pallets,
                isDarkTheme = isDarkTheme,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onToggleTheme = viewModel::toggleTheme,
                onOpenPallet = { palletId -> navController.navigate(Routes.palletDetail(palletId)) },
                onQuickCameraPallet = { palletId ->
                    viewModel.resetCameraSession()
                    navController.navigate(Routes.cameraPallet(palletId))
                },
                onAddPallet = { name, cartons, items, cb ->
                    viewModel.addPallet(rowId, name, cartons, items, cb)
                },
                onUpdatePallet = { palletId, name, cartons, items, cb ->
                    viewModel.updatePallet(rowId, palletId, name, cartons, items, cb)
                },
                onDeletePallet = viewModel::deletePallet,
            )
        }

        composable(
            route = Routes.PALLET_DETAIL,
            arguments = listOf(navArgument("palletId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val palletId = backStackEntry.arguments?.getLong("palletId") ?: return@composable
            val palletInfo by remember(palletId) { viewModel.observePalletPathInfo(palletId) }
                .collectAsStateWithLifecycle(initialValue = null)
            val palletSummary by remember(palletId) { viewModel.observePalletSummary(palletId) }
                .collectAsStateWithLifecycle(initialValue = null)
            val palletTickets by remember(palletId) { viewModel.observePalletTickets(palletId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val cartons by remember(palletId) { viewModel.observeCartons(palletId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

            PalletDetailScreen(
                palletInfo = palletInfo,
                palletSummary = palletSummary,
                palletTickets = palletTickets,
                cartons = cartons,
                palletOptions = allPalletOptions,
                allCartons = allCartons,
                isDarkTheme = isDarkTheme,
                watermarkEnabled = watermarkEnabled,
                isImportingGallery = isImportingGallery,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onToggleTheme = viewModel::toggleTheme,
                onToggleWatermark = viewModel::toggleWatermark,
                onOpenCameraForPallet = {
                    viewModel.resetCameraSession()
                    navController.navigate(Routes.cameraPallet(palletId))
                },
                onImportGalleryForPallet = { uris ->
                    viewModel.importGalleryTickets(uris, palletId = palletId, cartonId = null)
                },
                onOpenPhotoViewer = { index ->
                    navController.navigate(Routes.viewerPallet(palletId, index))
                },
                onUpdateTicket = viewModel::updateTicket,
                onDeletePhoto = viewModel::deletePhoto,
                onUpdatePallet = { name, cartonCount, itemCount, cb ->
                    val rowId = palletInfo?.rowId ?: palletSummary?.rowId ?: return@PalletDetailScreen
                    viewModel.updatePallet(rowId, palletId, name, cartonCount, itemCount, cb)
                },
                onOpenCarton = { cartonId ->
                    navController.navigate(Routes.cartonDetail(palletId, cartonId))
                },
                onQuickCameraForCarton = { cartonId ->
                    viewModel.resetCameraSession()
                    navController.navigate(Routes.cameraCarton(palletId, cartonId))
                },
                onAddCarton = { name, itemCount, cb ->
                    viewModel.addCarton(palletId, name, itemCount, cb)
                },
                onUpdateCarton = { cartonId, name, itemCount, cb ->
                    viewModel.updateCarton(palletId, cartonId, name, itemCount, cb)
                },
                onDeleteCarton = viewModel::deleteCarton,
            )
        }

        composable(
            route = Routes.CARTON_DETAIL,
            arguments = listOf(
                navArgument("palletId") { type = NavType.LongType },
                navArgument("cartonId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val palletId = backStackEntry.arguments?.getLong("palletId") ?: return@composable
            val cartonId = backStackEntry.arguments?.getLong("cartonId") ?: return@composable
            val cartonInfo by remember(cartonId) { viewModel.observeCartonPathInfo(cartonId) }
                .collectAsStateWithLifecycle(initialValue = null)
            val cartonTickets by remember(cartonId) { viewModel.observeCartonTickets(cartonId) }
                .collectAsStateWithLifecycle(initialValue = emptyList())

            CartonDetailScreen(
                cartonInfo = cartonInfo,
                cartonTickets = cartonTickets,
                palletOptions = allPalletOptions,
                allCartons = allCartons,
                isDarkTheme = isDarkTheme,
                watermarkEnabled = watermarkEnabled,
                isImportingGallery = isImportingGallery,
                snackbarHostState = snackbarHostState,
                onBack = { navController.popBackStack() },
                onToggleTheme = viewModel::toggleTheme,
                onToggleWatermark = viewModel::toggleWatermark,
                onOpenCameraForCarton = {
                    viewModel.resetCameraSession()
                    navController.navigate(Routes.cameraCarton(palletId, cartonId))
                },
                onImportGalleryForCarton = { uris ->
                    viewModel.importGalleryTickets(uris, palletId = palletId, cartonId = cartonId)
                },
                onOpenPhotoViewer = { index ->
                    navController.navigate(Routes.viewerCarton(cartonId, index))
                },
                onUpdateTicket = viewModel::updateTicket,
                onDeletePhoto = viewModel::deletePhoto,
                onUpdateCarton = { name, itemCount, cb ->
                    viewModel.updateCarton(palletId, cartonId, name, itemCount, cb)
                },
            )
        }

        composable(
            route = Routes.CAMERA_PALLET,
            arguments = listOf(navArgument("palletId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val palletId = backStackEntry.arguments?.getLong("palletId") ?: return@composable
            val palletInfo by remember(palletId) { viewModel.observePalletPathInfo(palletId) }
                .collectAsStateWithLifecycle(initialValue = null)

            val title = palletInfo?.let { "${it.warehouseName} > ${it.rowName} > ${it.palletName}" }
                ?: "تیکت پالت"
            val folderSubtitle = palletInfo?.let {
                "پوشه: ${it.palletName}/${PersianUtils.PALLET_TICKET_SUBFOLDER}"
            } ?: "ذخیره در پوشه تیکت پالت"

            RapidCameraScreen(
                locationBannerTitle = title,
                folderSubtitle = folderSubtitle,
                watermarkEnabled = watermarkEnabled,
                sessionState = cameraSession,
                onToggleWatermark = viewModel::toggleWatermark,
                onImageCapturedTempFile = { file ->
                    viewModel.saveCapturedTicketFile(file, palletId = palletId, cartonId = null)
                },
                onGalleryImagesPicked = { uris ->
                    viewModel.importGalleryTickets(
                        uris = uris,
                        palletId = palletId,
                        cartonId = null,
                        updateCameraSession = true,
                    )
                },
                onClearError = viewModel::clearCameraError,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CAMERA_CARTON,
            arguments = listOf(
                navArgument("palletId") { type = NavType.LongType },
                navArgument("cartonId") { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val palletId = backStackEntry.arguments?.getLong("palletId") ?: return@composable
            val cartonId = backStackEntry.arguments?.getLong("cartonId") ?: return@composable
            val cartonInfo by remember(cartonId) { viewModel.observeCartonPathInfo(cartonId) }
                .collectAsStateWithLifecycle(initialValue = null)

            val title = cartonInfo?.let {
                "${it.warehouseName} > ${it.rowName} > ${it.palletName} > ${it.cartonName}"
            } ?: "تیکت کارتن"
            val folderSubtitle = cartonInfo?.let {
                "پوشه: ${PersianUtils.CARTONS_PARENT_SUBFOLDER}/${it.cartonName}"
            } ?: "ذخیره در پوشه اختصاصی کارتن"

            RapidCameraScreen(
                locationBannerTitle = title,
                folderSubtitle = folderSubtitle,
                watermarkEnabled = watermarkEnabled,
                sessionState = cameraSession,
                onToggleWatermark = viewModel::toggleWatermark,
                onImageCapturedTempFile = { file ->
                    viewModel.saveCapturedTicketFile(file, palletId = palletId, cartonId = cartonId)
                },
                onGalleryImagesPicked = { uris ->
                    viewModel.importGalleryTickets(
                        uris = uris,
                        palletId = palletId,
                        cartonId = cartonId,
                        updateCameraSession = true,
                    )
                },
                onClearError = viewModel::clearCameraError,
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.VIEWER_PALLET,
            arguments = listOf(
                navArgument("palletId") { type = NavType.LongType },
                navArgument("initialIndex") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val palletId = backStackEntry.arguments?.getLong("palletId") ?: return@composable
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            val tickets = remember(allTicketDetails, palletId) {
                allTicketDetails.filter { it.palletId == palletId && it.cartonId == null }
            }

            PhotoViewerScreen(
                tickets = tickets,
                initialIndex = initialIndex,
                palletOptions = allPalletOptions,
                allCartons = allCartons,
                onBack = { navController.popBackStack() },
                onUpdateTicket = viewModel::updateTicket,
                onDeletePhoto = viewModel::deletePhoto,
            )
        }

        composable(
            route = Routes.VIEWER_CARTON,
            arguments = listOf(
                navArgument("cartonId") { type = NavType.LongType },
                navArgument("initialIndex") { type = NavType.IntType },
            ),
        ) { backStackEntry ->
            val cartonId = backStackEntry.arguments?.getLong("cartonId") ?: return@composable
            val initialIndex = backStackEntry.arguments?.getInt("initialIndex") ?: 0
            val tickets = remember(allTicketDetails, cartonId) {
                allTicketDetails.filter { it.cartonId == cartonId }
            }

            PhotoViewerScreen(
                tickets = tickets,
                initialIndex = initialIndex,
                palletOptions = allPalletOptions,
                allCartons = allCartons,
                onBack = { navController.popBackStack() },
                onUpdateTicket = viewModel::updateTicket,
                onDeletePhoto = viewModel::deletePhoto,
            )
        }

        composable(
            route = Routes.VIEWER_SINGLE,
            arguments = listOf(navArgument("photoId") { type = NavType.LongType }),
        ) { backStackEntry ->
            val photoId = backStackEntry.arguments?.getLong("photoId") ?: return@composable
            val ticket by remember(photoId) { viewModel.observeTicketDetailById(photoId) }
                .collectAsStateWithLifecycle(initialValue = null)
            val list = remember(ticket) { listOfNotNull(ticket) }

            if (list.isNotEmpty()) {
                PhotoViewerScreen(
                    tickets = list,
                    initialIndex = 0,
                    palletOptions = allPalletOptions,
                    allCartons = allCartons,
                    onBack = { navController.popBackStack() },
                    onUpdateTicket = viewModel::updateTicket,
                    onDeletePhoto = { id ->
                        viewModel.deletePhoto(id)
                        navController.popBackStack()
                    },
                )
            }
        }
    }
}
