package com.personaltracker.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableItemList(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    onDelete: (index: Int, item: T) -> Unit,
    onItemClick: (index: Int) -> Unit,
    isDragging: MutableState<Boolean>,
    itemKey: (T) -> Any,
    itemContent: @Composable RowScope.(T) -> Unit
) {
    val haptic = LocalHapticFeedback.current

    // Drag state
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeights = remember { mutableMapOf<Int, Int>() }

    // Context menu state
    var contextMenuIndex by remember { mutableIntStateOf(-1) }

    // Snapshot items for stable reference during drag
    val currentItems by rememberUpdatedState(items)

    items.forEachIndexed { index, item ->
        key(itemKey(item)) {
            val isBeingDragged = draggedIndex == index

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        itemHeights[index] = coordinates.size.height
                    }
                    .zIndex(if (isBeingDragged) 1f else 0f)
                    .graphicsLayer {
                        if (isBeingDragged) {
                            translationY = dragOffset
                            shadowElevation = 8f
                        }
                    }
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onItemClick(index) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                contextMenuIndex = index
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drag handle
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "Drag to reorder",
                            modifier = Modifier
                                .padding(start = 4.dp, top = 12.dp, bottom = 12.dp, end = 0.dp)
                                .pointerInput(Unit) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragOffset = 0f
                                            isDragging.value = true
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            draggedIndex = -1
                                            dragOffset = 0f
                                            isDragging.value = false
                                        },
                                        onDragCancel = {
                                            draggedIndex = -1
                                            dragOffset = 0f
                                            isDragging.value = false
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffset += dragAmount

                                            val currentDraggedIndex = draggedIndex
                                            if (currentDraggedIndex < 0) return@detectVerticalDragGestures

                                            val currentHeight = itemHeights[currentDraggedIndex] ?: return@detectVerticalDragGestures
                                            val threshold = currentHeight / 2

                                            if (dragOffset > threshold && currentDraggedIndex < currentItems.size - 1) {
                                                val newList = currentItems.toMutableList()
                                                val movedItem = newList.removeAt(currentDraggedIndex)
                                                newList.add(currentDraggedIndex + 1, movedItem)
                                                onReorder(newList)
                                                draggedIndex = currentDraggedIndex + 1
                                                dragOffset -= currentHeight
                                            } else if (dragOffset < -threshold && currentDraggedIndex > 0) {
                                                val newList = currentItems.toMutableList()
                                                val movedItem = newList.removeAt(currentDraggedIndex)
                                                newList.add(currentDraggedIndex - 1, movedItem)
                                                onReorder(newList)
                                                draggedIndex = currentDraggedIndex - 1
                                                dragOffset += currentHeight
                                            }
                                        }
                                    )
                                },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        itemContent(item)
                    }
                }

                // Context menu
                DropdownMenu(
                    expanded = contextMenuIndex == index,
                    onDismissRequest = { contextMenuIndex = -1 }
                ) {
                    DropdownMenuItem(
                        text = { Text("Move to Top") },
                        onClick = {
                            contextMenuIndex = -1
                            val newList = items.toMutableList()
                            val movedItem = newList.removeAt(index)
                            newList.add(0, movedItem)
                            onReorder(newList)
                        },
                        enabled = index > 0
                    )
                    DropdownMenuItem(
                        text = { Text("Move to Bottom") },
                        onClick = {
                            contextMenuIndex = -1
                            val newList = items.toMutableList()
                            val movedItem = newList.removeAt(index)
                            newList.add(movedItem)
                            onReorder(newList)
                        },
                        enabled = index < items.size - 1
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            contextMenuIndex = -1
                            onDelete(index, item)
                        }
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
        }
    }
}
