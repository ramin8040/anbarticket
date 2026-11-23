package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Warehouse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.local.CartonEntity
import com.example.data.local.PalletOptionItem
import com.example.data.local.TicketDetailItem
import com.example.data.local.TicketPhotoEntity
import com.example.util.PersianUtils
import com.example.util.PersianUtils.toPersianNumber

@Composable
fun ThemeToggleButton(
    isDarkTheme: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .testTag("theme_toggle_button"),
    ) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Filled.LightMode else Icons.Filled.DarkMode,
            contentDescription = if (isDarkTheme) "تغییر به حالت روشن" else "تغییر به حالت تیره",
            tint = MaterialTheme.colorScheme.secondary,
        )
    }
}

@Composable
fun HierarchyBreadcrumb(
    segments: List<String>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        segments.forEachIndexed { index, label ->
            val isLast = index == segments.lastIndex
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isLast) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (isLast) FontWeight.Bold else FontWeight.Medium,
                    color = if (isLast) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            if (!isLast) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
fun FolderPathBanner(
    folderPath: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(34.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.FolderOpen,
                        contentDescription = "مسیر پوشه روی حافظه",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "مسیر پوشه در حافظه گوشی",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(2.dp))
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                    Text(
                        text = folderPath,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
fun SearchBarField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "جست‌وجو",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

@Composable
fun StatBadge(
    label: String,
    value: Int,
    icon: ImageVector,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "${value.toPersianNumber()} $label",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun OverflowActionMenu(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    testTagPrefix: String,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { expanded = true },
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("${testTagPrefix}_menu_button"),
        ) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "گزینه‌های بیشتر",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DropdownMenuItem(
                text = { Text("ویرایش") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = null,
                    )
                },
                onClick = {
                    expanded = false
                    onEdit()
                },
                modifier = Modifier.testTag("${testTagPrefix}_edit_item"),
            )
            DropdownMenuItem(
                text = {
                    Text(
                        text = "حذف",
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                onClick = {
                    expanded = false
                    onDelete()
                },
                modifier = Modifier.testTag("${testTagPrefix}_delete_item"),
            )
        }
    }
}

@Composable
fun CountStepperField(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    testTagPrefix: String,
    modifier: Modifier = Modifier,
) {
    var textValue by remember(value) { mutableStateOf(if (value == 0) "" else value.toPersianNumber()) }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalIconButton(
                onClick = {
                    val next = (value - 1).coerceAtLeast(0)
                    onValueChange(next)
                    textValue = if (next == 0) "" else next.toPersianNumber()
                },
                enabled = value > 0,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTagPrefix}_decrement"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "کاهش $label",
                )
            }

            OutlinedTextField(
                value = textValue,
                onValueChange = { raw ->
                    val englishDigits = PersianUtils.toEnglishDigits(raw).filter { it.isDigit() }
                    val parsed = englishDigits.take(7).toIntOrNull() ?: 0
                    textValue = if (englishDigits.isEmpty()) "" else PersianUtils.toPersianDigits(englishDigits.take(7))
                    onValueChange(parsed)
                },
                placeholder = {
                    Text(
                        text = "۰",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("${testTagPrefix}_input"),
            )

            FilledTonalIconButton(
                onClick = {
                    val next = value + 1
                    onValueChange(next)
                    textValue = next.toPersianNumber()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
                modifier = Modifier
                    .size(48.dp)
                    .testTag("${testTagPrefix}_increment"),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "افزایش $label",
                )
            }
        }
    }
}

@Composable
fun NameInputDialog(
    title: String,
    label: String,
    placeholder: String,
    initialValue: String = "",
    confirmLabel: String = "ذخیره",
    onDismiss: () -> Unit,
    onConfirm: (String, (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text(label) },
                    placeholder = { Text(placeholder) },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg ->
                        { Text(text = msg, color = MaterialTheme.colorScheme.error) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_name_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name) { err ->
                        if (err != null) {
                            errorMessage = err
                        } else {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("dialog_confirm_button"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button"),
            ) {
                Text("انصراف")
            }
        },
    )
}

@Composable
fun PalletFormDialog(
    title: String,
    initialName: String = "",
    initialCartonCount: Int = 0,
    initialItemCount: Int = 0,
    confirmLabel: String = "ذخیره",
    onDismiss: () -> Unit,
    onConfirm: (name: String, cartonCount: Int, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var cartonCount by remember { mutableStateOf(initialCartonCount) }
    var itemCount by remember { mutableStateOf(initialItemCount) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("نام یا شماره پالت") },
                    placeholder = { Text("مثلاً: پالت ۰۱") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg ->
                        { Text(text = msg, color = MaterialTheme.colorScheme.error) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pallet_name_input"),
                )

                CountStepperField(
                    label = "تعداد کارتن",
                    value = cartonCount,
                    onValueChange = { cartonCount = it },
                    testTagPrefix = "pallet_carton_count",
                )

                CountStepperField(
                    label = "تعداد اقلام شمارش‌شده",
                    value = itemCount,
                    onValueChange = { itemCount = it },
                    testTagPrefix = "pallet_item_count",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, cartonCount, itemCount) { err ->
                        if (err != null) {
                            errorMessage = err
                        } else {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("pallet_dialog_confirm"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("pallet_dialog_cancel"),
            ) {
                Text("انصراف")
            }
        },
    )
}

@Composable
fun CartonFormDialog(
    title: String,
    initialName: String = "",
    initialItemCount: Int = 0,
    confirmLabel: String = "ذخیره",
    onDismiss: () -> Unit,
    onConfirm: (name: String, itemCount: Int, onResult: (String?) -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var itemCount by remember { mutableStateOf(initialItemCount) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    label = { Text("نام یا شماره کارتن") },
                    placeholder = { Text("مثلاً: کارتن ۱") },
                    singleLine = true,
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let { msg ->
                        { Text(text = msg, color = MaterialTheme.colorScheme.error) }
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("carton_name_input"),
                )

                CountStepperField(
                    label = "تعداد اقلام شمارش‌شده در کارتن",
                    value = itemCount,
                    onValueChange = { itemCount = it },
                    testTagPrefix = "carton_item_count",
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(name, itemCount) { err ->
                        if (err != null) {
                            errorMessage = err
                        } else {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("carton_dialog_confirm"),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("carton_dialog_cancel"),
            ) {
                Text("انصراف")
            }
        },
    )
}

@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.error,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.testTag("confirm_delete_button"),
            ) {
                Text("حذف شود")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_delete_button"),
            ) {
                Text("انصراف")
            }
        },
    )
}

@Composable
fun EmptyStatePanel(
    title: String,
    description: String,
    icon: ImageVector? = null,
    illustrationResId: Int? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    actionTestTag: String = "empty_state_action",
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 520.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (illustrationResId != null) {
                Image(
                    painter = painterResource(id = illustrationResId),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(175.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(modifier = Modifier.height(18.dp))
            } else if (icon != null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(64.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(18.dp))
                Button(
                    onClick = onAction,
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(actionTestTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun TicketPhotoTile(
    photo: TicketPhotoEntity,
    onClick: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(0.84f)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .testTag("ticket_photo_${photo.id}"),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = photo.mediaUri,
                contentDescription = "عکس تیکت ${photo.jalaliDateTime}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            if (onEdit != null || onDelete != null) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (onEdit != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC0F172A),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onEdit)
                                .testTag("tile_edit_ticket_${photo.id}"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "ویرایش تیکت",
                                    tint = Color(0xFFFDE68A),
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                    if (onDelete != null) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xCC7F1D1D),
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .clickable(onClick = onDelete)
                                .testTag("tile_delete_ticket_${photo.id}"),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "حذف تیکت",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xD90F172A)),
                        ),
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (photo.note.isNotBlank()) {
                        Text(
                            text = photo.note,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFDE68A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PhotoCamera,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = photo.jalaliDateTime,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

enum class TopLevelTab(val label: String, val testTag: String) {
    WAREHOUSES("انبارها", "nav_tab_warehouses"),
    MAP("نقشه سوله", "nav_tab_map"),
    SEARCH("جست‌وجو", "nav_tab_search"),
    REPORTS("گزارش‌گیری", "nav_tab_reports"),
}

@Composable
fun AppBottomNavigationBar(
    selectedTab: TopLevelTab,
    onSelectTab: (TopLevelTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
    ) {
        NavigationBarItem(
            selected = selectedTab == TopLevelTab.WAREHOUSES,
            onClick = { onSelectTab(TopLevelTab.WAREHOUSES) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == TopLevelTab.WAREHOUSES) Icons.Filled.Warehouse else Icons.Outlined.Warehouse,
                    contentDescription = TopLevelTab.WAREHOUSES.label,
                )
            },
            label = { Text(TopLevelTab.WAREHOUSES.label, maxLines = 1) },
            modifier = Modifier.testTag(TopLevelTab.WAREHOUSES.testTag),
        )
        NavigationBarItem(
            selected = selectedTab == TopLevelTab.MAP,
            onClick = { onSelectTab(TopLevelTab.MAP) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == TopLevelTab.MAP) Icons.Filled.Map else Icons.Outlined.Map,
                    contentDescription = TopLevelTab.MAP.label,
                )
            },
            label = { Text(TopLevelTab.MAP.label, maxLines = 1) },
            modifier = Modifier.testTag(TopLevelTab.MAP.testTag),
        )
        NavigationBarItem(
            selected = selectedTab == TopLevelTab.SEARCH,
            onClick = { onSelectTab(TopLevelTab.SEARCH) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == TopLevelTab.SEARCH) Icons.Filled.ManageSearch else Icons.Outlined.ManageSearch,
                    contentDescription = TopLevelTab.SEARCH.label,
                )
            },
            label = { Text(TopLevelTab.SEARCH.label, maxLines = 1) },
            modifier = Modifier.testTag(TopLevelTab.SEARCH.testTag),
        )
        NavigationBarItem(
            selected = selectedTab == TopLevelTab.REPORTS,
            onClick = { onSelectTab(TopLevelTab.REPORTS) },
            icon = {
                Icon(
                    imageVector = if (selectedTab == TopLevelTab.REPORTS) Icons.Filled.Assessment else Icons.Outlined.Assessment,
                    contentDescription = TopLevelTab.REPORTS.label,
                )
            },
            label = { Text(TopLevelTab.REPORTS.label, maxLines = 1) },
            modifier = Modifier.testTag(TopLevelTab.REPORTS.testTag),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTicketDialog(
    ticket: TicketDetailItem,
    palletOptions: List<PalletOptionItem>,
    allCartons: List<CartonEntity>,
    onDismiss: () -> Unit,
    onSave: (
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
) {
    val (initialDate, initialTime) = remember(ticket.jalaliDateTime) {
        PersianUtils.splitJalaliDateTime(ticket.jalaliDateTime)
    }

    var note by remember(ticket.id) { mutableStateOf(ticket.note) }
    var dateText by remember(ticket.id) { mutableStateOf(initialDate) }
    var timeText by remember(ticket.id) { mutableStateOf(initialTime) }

    var selectedPalletId by remember(ticket.id) { mutableStateOf(ticket.palletId) }
    var isCartonMode by remember(ticket.id) { mutableStateOf(ticket.cartonId != null) }
    var selectedCartonId by remember(ticket.id) { mutableStateOf(ticket.cartonId) }

    val availableCartons = remember(allCartons, selectedPalletId) {
        allCartons.filter { it.palletId == selectedPalletId }
    }

    val selectedPalletOption = remember(palletOptions, selectedPalletId) {
        palletOptions.find { it.palletId == selectedPalletId }
    }
    val selectedCartonEntity = remember(availableCartons, selectedCartonId) {
        availableCartons.find { it.id == selectedCartonId }
    }

    var palletCartonCount by remember(selectedPalletId) {
        mutableStateOf(
            if (selectedPalletId == ticket.palletId) ticket.palletCartonCount
            else selectedPalletOption?.cartonCount ?: 0,
        )
    }
    var palletItemCount by remember(selectedPalletId) {
        mutableStateOf(
            if (selectedPalletId == ticket.palletId) ticket.palletItemCount
            else selectedPalletOption?.itemCount ?: 0,
        )
    }
    var cartonItemCount by remember(selectedCartonId) {
        mutableStateOf(
            if (selectedCartonId == ticket.cartonId) (ticket.cartonItemCount ?: 0)
            else selectedCartonEntity?.itemCount ?: 0,
        )
    }

    var palletDropdownExpanded by remember { mutableStateOf(false) }
    var cartonDropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "ویرایش اطلاعات تیکت",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("برچسب یا یادداشت تیکت (اختیاری)") },
                    placeholder = { Text("مثلاً: تیکت شماره ۱۰۴ یا شمارش مجدد") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("edit_ticket_note_input"),
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = dateText,
                        onValueChange = {
                            dateText = it
                            errorMessage = null
                        },
                        label = { Text("تاریخ شمسی") },
                        placeholder = { Text("۱۴۰۵/۰۹/۰۱") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1.3f)
                            .testTag("edit_ticket_date_input"),
                    )
                    OutlinedTextField(
                        value = timeText,
                        onValueChange = {
                            timeText = it
                            errorMessage = null
                        },
                        label = { Text("ساعت") },
                        placeholder = { Text("۱۴:۳۰") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(0.9f)
                            .testTag("edit_ticket_time_input"),
                    )
                }

                // Pallet selector dropdown
                if (palletOptions.isNotEmpty()) {
                    val currentPalletLabel = selectedPalletOption?.let {
                        "${it.warehouseName} > ${it.rowName} > ${it.palletName}"
                    } ?: "${ticket.warehouseName} > ${ticket.rowName} > ${ticket.palletName}"

                    ExposedDropdownMenuBox(
                        expanded = palletDropdownExpanded,
                        onExpandedChange = { palletDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentPalletLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("مکان پالت") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = palletDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("edit_ticket_pallet_dropdown"),
                        )
                        ExposedDropdownMenu(
                            expanded = palletDropdownExpanded,
                            onDismissRequest = { palletDropdownExpanded = false },
                        ) {
                            palletOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = {
                                        Text("${opt.warehouseName} > ${opt.rowName} > ${opt.palletName}")
                                    },
                                    onClick = {
                                        selectedPalletId = opt.palletId
                                        palletDropdownExpanded = false
                                        val cartonsForNew = allCartons.filter { it.palletId == opt.palletId }
                                        if (cartonsForNew.none { it.id == selectedCartonId }) {
                                            selectedCartonId = cartonsForNew.firstOrNull()?.id
                                            if (selectedCartonId == null) isCartonMode = false
                                        }
                                    },
                                )
                            }
                        }
                    }
                }

                // Ticket Type Selector (Pallet vs Carton)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "نوع تیکت",
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !isCartonMode,
                            onClick = {
                                isCartonMode = false
                                errorMessage = null
                            },
                            label = { Text("تیکت پالت") },
                            modifier = Modifier.testTag("edit_ticket_type_pallet"),
                        )
                        FilterChip(
                            selected = isCartonMode,
                            enabled = availableCartons.isNotEmpty(),
                            onClick = {
                                isCartonMode = true
                                if (selectedCartonId == null) {
                                    selectedCartonId = availableCartons.firstOrNull()?.id
                                }
                                errorMessage = null
                            },
                            label = {
                                Text(
                                    if (availableCartons.isNotEmpty()) "تیکت کارتن" else "کارتنی ثبت نشده",
                                )
                            },
                            modifier = Modifier.testTag("edit_ticket_type_carton"),
                        )
                    }
                }

                if (isCartonMode && availableCartons.isNotEmpty()) {
                    val currentCartonLabel = selectedCartonEntity?.name ?: availableCartons.first().name
                    ExposedDropdownMenuBox(
                        expanded = cartonDropdownExpanded,
                        onExpandedChange = { cartonDropdownExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = currentCartonLabel,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("کارتن مقصد روی پالت") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = cartonDropdownExpanded)
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .testTag("edit_ticket_carton_dropdown"),
                        )
                        ExposedDropdownMenu(
                            expanded = cartonDropdownExpanded,
                            onDismissRequest = { cartonDropdownExpanded = false },
                        ) {
                            availableCartons.forEach { carton ->
                                DropdownMenuItem(
                                    text = { Text(carton.name) },
                                    onClick = {
                                        selectedCartonId = carton.id
                                        cartonItemCount = carton.itemCount
                                        cartonDropdownExpanded = false
                                    },
                                )
                            }
                        }
                    }

                    CountStepperField(
                        label = "تعداد اقلام شمارش‌شده کارتن",
                        value = cartonItemCount,
                        onValueChange = { cartonItemCount = it },
                        testTagPrefix = "edit_ticket_carton_items",
                    )
                } else {
                    CountStepperField(
                        label = "تعداد کارتن پالت",
                        value = palletCartonCount,
                        onValueChange = { palletCartonCount = it },
                        testTagPrefix = "edit_ticket_pallet_cartons",
                    )
                    CountStepperField(
                        label = "تعداد اقلام شمارش‌شده پالت",
                        value = palletItemCount,
                        onValueChange = { palletItemCount = it },
                        testTagPrefix = "edit_ticket_pallet_items",
                    )
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targetCartonId = if (isCartonMode) {
                        selectedCartonId ?: availableCartons.firstOrNull()?.id
                    } else {
                        null
                    }
                    onSave(
                        selectedPalletId,
                        targetCartonId,
                        dateText,
                        timeText,
                        note,
                        if (targetCartonId == null) palletCartonCount else null,
                        if (targetCartonId == null) palletItemCount else null,
                        if (targetCartonId != null) cartonItemCount else null,
                    ) { err ->
                        if (err != null) {
                            errorMessage = err
                        } else {
                            onDismiss()
                        }
                    }
                },
                modifier = Modifier.testTag("edit_ticket_confirm_button"),
            ) {
                Text("ذخیره تغییرات")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("edit_ticket_cancel_button"),
            ) {
                Text("انصراف")
            }
        },
    )
}
