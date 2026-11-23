package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CartonEntity
import com.example.data.local.PalletOptionItem
import com.example.data.local.TicketDetailItem
import com.example.ui.components.ConfirmDeleteDialog
import com.example.ui.components.EditTicketDialog
import com.example.util.PersianUtils.toPersianNumber

@Composable
fun PhotoViewerScreen(
    tickets: List<TicketDetailItem>,
    initialIndex: Int,
    palletOptions: List<PalletOptionItem>,
    allCartons: List<CartonEntity>,
    onBack: () -> Unit,
    onUpdateTicket: (
        photoId: Long,
        newPalletId: Long,
        newCartonId: Long?,
        jalaliDateStr: String,
        jalaliTimeStr: String,
        note: String,
        updatedPalletCartonCount: Int?,
        updatedPalletItemCount: Int?,
        updatedCartonItemCount: Int?,
        onResult: (String?) -> Unit,
    ) -> Unit,
    onDeletePhoto: (Long) -> Unit,
) {
    LaunchedEffect(tickets.size) {
        if (tickets.isEmpty()) {
            onBack()
        }
    }

    if (tickets.isEmpty()) return

    val safeInitial = initialIndex.coerceIn(0, (tickets.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(initialPage = safeInitial, pageCount = { tickets.size })
    var editingTicket by remember { mutableStateOf<TicketDetailItem?>(null) }
    var deletingTicket by remember { mutableStateOf<TicketDetailItem?>(null) }

    val currentTicket = tickets.getOrNull(pagerState.currentPage) ?: tickets.first()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val ticket = tickets[page]
            ZoomableTicketImage(
                mediaUri = ticket.mediaUri,
                contentDescription = "عکس تیکت ${ticket.jalaliDateTime}",
            )
        }

        // Top bar with Back, Counter, Edit, and Delete
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xD9000000), Color.Transparent),
                    ),
                )
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("photo_viewer_back"),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "بازگشت",
                    tint = Color.White,
                )
            }

            Text(
                text = "${(pagerState.currentPage + 1).toPersianNumber()} از ${tickets.size.toPersianNumber()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { editingTicket = currentTicket },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("photo_viewer_edit"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "ویرایش اطلاعات تیکت",
                        tint = Color(0xFFFDE68A),
                    )
                }

                IconButton(
                    onClick = { deletingTicket = currentTicket },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("photo_viewer_delete"),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "حذف عکس تیکت",
                        tint = Color(0xFFF87171),
                    )
                }
            }
        }

        // Bottom info overlay
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xCC0F172A), Color(0xF20F172A)),
                    ),
                )
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (currentTicket.isCartonTicket) Color(0xFF065F46) else Color(0xFF92400E),
                ) {
                    Text(
                        text = if (currentTicket.isCartonTicket) "تیکت کارتن" else "تیکت پالت",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = buildString {
                        append("${currentTicket.warehouseName} ← ${currentTicket.rowName} ← ${currentTicket.palletName}")
                        if (!currentTicket.cartonName.isNullOrBlank()) {
                            append(" ← ${currentTicket.cartonName}")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (currentTicket.note.isNotBlank()) {
                Text(
                    text = "یادداشت: ${currentTicket.note}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFDE68A),
                )
            }

            Text(
                text = "زمان ثبت: ${currentTicket.jalaliDateTime}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFDE68A),
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.FolderOpen,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp),
                )
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = "${currentTicket.relativeFolderPath}/${currentTicket.fileName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFE2E8F0),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "برای بزرگ‌نمایی عکس از دو انگشت استفاده کنید",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF94A3B8),
            )
        }
    }

    editingTicket?.let { target ->
        EditTicketDialog(
            ticket = target,
            palletOptions = palletOptions,
            allCartons = allCartons,
            onDismiss = { editingTicket = null },
            onSave = { newPalletId, newCartonId, dateStr, timeStr, note, pCartons, pItems, cItems, cb ->
                onUpdateTicket(
                    target.id,
                    newPalletId,
                    newCartonId,
                    dateStr,
                    timeStr,
                    note,
                    pCartons,
                    pItems,
                    cItems,
                    cb,
                )
            },
        )
    }

    deletingTicket?.let { target ->
        ConfirmDeleteDialog(
            title = "حذف عکس تیکت",
            message = "آیا از حذف کامل این تیکت و پاک‌شدن عکس آن از پوشه حافظه گوشی مطمئن هستید؟",
            onDismiss = { deletingTicket = null },
            onConfirm = { onDeletePhoto(target.id) },
        )
    }
}

@Composable
private fun ZoomableTicketImage(
    mediaUri: String,
    contentDescription: String,
) {
    var scale by remember(mediaUri) { mutableFloatStateOf(1f) }
    var offset by remember(mediaUri) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(mediaUri) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val nextScale = (scale * zoom).coerceIn(1f, 4f)
                    scale = nextScale
                    offset = if (nextScale > 1f) {
                        offset + pan
                    } else {
                        Offset.Zero
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = mediaUri,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y,
                ),
        )
    }
}
