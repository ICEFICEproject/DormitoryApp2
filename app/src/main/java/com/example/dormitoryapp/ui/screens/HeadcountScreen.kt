package com.example.dormitoryapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.foundation.BorderStroke
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.zIndex
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.size
import com.example.dormitoryapp.data.RoomSortMode
import com.example.dormitoryapp.ui.components.RoomSortBar
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.data.DormRoom
import com.example.dormitoryapp.data.HeadcountRecord

/**
 * Экран 2: Вечерний обход численности.
 * - Строго не дает выставить количество ночующих выше штатной нормы комнаты (от 0 до room.capacity).
 * - Позволяет вручную с клавиатуры ввести количество проживающих в комнате (карандаш ✏️).
 * - Блокирует редактирование чужих этажей, если сотрудник закреплен за другим этажом.
 */
@Composable
fun HeadcountScreen(
    selectedFloor: Int,
    assignedFloor: Int,
    canEditThisFloor: Boolean,
    onSelectFloor: (Int) -> Unit,
    allRooms: List<DormRoom>,
    headcountRecords: List<HeadcountRecord>,
    roomSortMode: RoomSortMode,
    onSetRoomSortMode: (RoomSortMode) -> Unit,
    getSortedRooms: (List<DormRoom>, RoomSortMode) -> List<DormRoom>,
    onSaveCustomOrder: (Int, List<Int>) -> Unit,
    onResetCustomOrder: (Int) -> Unit,
    customOrderVersion: Int,
    onSaveRoomHeadcount: (DormRoom, Int) -> Unit,
    onUpdateRoomCapacity: (DormRoom, Int) -> Unit,
    onFillUncheckedWithNorm: (Int) -> Unit
) {
    var wingFilter by remember { mutableIntStateOf(0) }
    var roomToEditNorm by remember { mutableStateOf<DormRoom?>(null) }
    var manualNormInput by remember { mutableStateOf("") }

    val floorRooms = allRooms.filter { it.floor == selectedFloor }
    val filteredRooms = floorRooms.filter { room ->
        val numOnFloor = room.roomNumber % 100
        when (wingFilter) {
            1 -> numOnFloor <= 15
            2 -> numOnFloor >= 16
            else -> true
        }
    }

    val currentRoomsList = remember(filteredRooms, roomSortMode, customOrderVersion) {
        mutableStateListOf<DormRoom>().apply {
            addAll(getSortedRooms(filteredRooms, roomSortMode))
        }
    }

    val density = LocalDensity.current
    val defaultCardHeightPx = remember(density) { with(density) { 96.dp.toPx() } }
    var draggingRoomNumber by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember(density) { mutableFloatStateOf(defaultCardHeightPx) }

    fun buildFullFloorCustomOrder(): List<Int> {
        if (wingFilter == 0) {
            return currentRoomsList.map { it.roomNumber }
        }
        val fullFloorSorted = getSortedRooms(floorRooms, roomSortMode).map { it.roomNumber }
        val currentWingSet = currentRoomsList.map { it.roomNumber }.toSet()
        val result = mutableListOf<Int>()
        var currentWingIdx = 0
        for (r in fullFloorSorted) {
            if (r in currentWingSet) {
                if (currentWingIdx < currentRoomsList.size) {
                    result.add(currentRoomsList[currentWingIdx].roomNumber)
                    currentWingIdx++
                }
            } else {
                result.add(r)
            }
        }
        return result
    }

    fun moveRoom(fromIdx: Int, toIdx: Int, commit: Boolean = true) {
        if (fromIdx == toIdx || fromIdx !in currentRoomsList.indices || toIdx !in currentRoomsList.indices) return
        val item = currentRoomsList.removeAt(fromIdx)
        currentRoomsList.add(toIdx, item)
        if (commit) {
            onSaveCustomOrder(selectedFloor, buildFullFloorCustomOrder())
        }
    }

    val recordMap = headcountRecords.associateBy { it.roomNumber }

    val floorTotalNorm = floorRooms.sumOf { it.capacity }
    val floorPresentSum = floorRooms.sumOf { recordMap[it.roomNumber]?.presentCount ?: 0 }
    val checkedFloorRooms = floorRooms.filter { recordMap.containsKey(it.roomNumber) }
    val checkedNormSum = checkedFloorRooms.sumOf { it.capacity }
    val floorAbsent = (checkedNormSum - floorPresentSum).coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Переключатель этажей (2, 3, 4, 5)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (floor in 2..5) {
                val isMyFloor = floor == assignedFloor
                FilterChip(
                    selected = selectedFloor == floor,
                    onClick = { onSelectFloor(floor) },
                    label = {
                        Text(
                            text = if (isMyFloor) "$floor эт. ★" else "$floor эт.",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Предупреждение о блокировке (чужой этаж или прошлая дата)
        if (!canEditThisFloor) {
            val isOtherFloor = selectedFloor != assignedFloor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color(0xFFC62828)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOtherFloor) "Только просмотр. Ваш рабочий этаж — $assignedFloor-й."
                            else "История (только просмотр). Прошлые даты может редактировать только командир этажа.",
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    if (isOtherFloor) {
                        TextButton(onClick = { onSelectFloor(assignedFloor) }) {
                            Text("На $assignedFloor эт.", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Фильтр стороны: Все (35) | 00–15 | 16–34
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = wingFilter == 0,
                onClick = { wingFilter = 0 },
                label = { Text("Все 35 комнат", fontSize = 12.sp) }
            )
            FilterChip(
                selected = wingFilter == 1,
                onClick = { wingFilter = 1 },
                label = { Text("00–15 (по 4)", fontSize = 12.sp) }
            )
            FilterChip(
                selected = wingFilter == 2,
                onClick = { wingFilter = 2 },
                label = { Text("16–34 (по 6)", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Панель выбора порядка комнат (Обычный, Шахматный, Свой)
        RoomSortBar(
            currentMode = roomSortMode,
            onModeSelected = onSetRoomSortMode,
            onResetCustomOrder = { onResetCustomOrder(selectedFloor) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        // Плашка с итогами этажа
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Итог $selectedFloor этажа (${checkedFloorRooms.size}/${floorRooms.size} комн.):",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    if (canEditThisFloor) {
                        TextButton(onClick = { onFillUncheckedWithNorm(selectedFloor) }) {
                            Icon(Icons.Default.DoneAll, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Заполнить по норме", fontSize = 12.sp)
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Штат: $floorTotalNorm", fontSize = 13.sp)
                    Text(
                        "Ночует (факт): $floorPresentSum",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Отсутствует: $floorAbsent",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (floorAbsent > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Список комнат выбранного этажа
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(currentRoomsList, key = { _, room -> room.roomNumber }) { index, room ->
                val record = recordMap[room.roomNumber]
                val currentCount = (record?.presentCount ?: room.capacity).coerceIn(0, room.capacity)
                val isChecked = record != null
                val absentInRoom = if (isChecked) (room.capacity - currentCount).coerceAtLeast(0) else 0
                val isDragging = draggingRoomNumber == room.roomNumber

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 15f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.04f else 1f
                            scaleY = if (isDragging) 1.04f else 1f
                        }
                        .animateItem()
                        .onGloballyPositioned { coords ->
                            if (coords.size.height > 50) {
                                cardHeightPx = coords.size.height.toFloat()
                            }
                        }
                        .then(
                            if (roomSortMode == RoomSortMode.CUSTOM) {
                                Modifier.pointerInput(room.roomNumber, currentRoomsList.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggingRoomNumber = room.roomNumber
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val step = if (cardHeightPx > 50f) cardHeightPx else defaultCardHeightPx
                                            val currentIdx = currentRoomsList.indexOfFirst { it.roomNumber == room.roomNumber }
                                            if (currentIdx != -1) {
                                                if (dragOffsetY > step * 0.7f && currentIdx < currentRoomsList.lastIndex) {
                                                    moveRoom(currentIdx, currentIdx + 1, commit = false)
                                                    dragOffsetY -= step
                                                } else if (dragOffsetY < -step * 0.7f && currentIdx > 0) {
                                                    moveRoom(currentIdx, currentIdx - 1, commit = false)
                                                    dragOffsetY += step
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggingRoomNumber = null
                                            dragOffsetY = 0f
                                            onSaveCustomOrder(selectedFloor, buildFullFloorCustomOrder())
                                        },
                                        onDragCancel = {
                                            draggingRoomNumber = null
                                            dragOffsetY = 0f
                                            onSaveCustomOrder(selectedFloor, buildFullFloorCustomOrder())
                                        }
                                    )
                                }
                            } else Modifier
                        ),
                    shape = RoundedCornerShape(12.dp),
                    border = if (isDragging) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 14.dp else 2.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isDragging -> Color(0xFFE3F2FD)
                            isChecked -> Color(0xFFE6F4EA)
                            else -> Color.White
                        },
                        contentColor = Color(0xFF181C20)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (roomSortMode == RoomSortMode.CUSTOM) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                IconButton(
                                    onClick = { moveRoom(index, index - 1) },
                                    enabled = index > 0,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Выше",
                                        tint = if (index > 0) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Icon(
                                    Icons.Default.DragHandle,
                                    contentDescription = "Перетащить",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                                IconButton(
                                    onClick = { moveRoom(index, index + 1) },
                                    enabled = index < currentRoomsList.lastIndex,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowDownward,
                                        contentDescription = "Ниже",
                                        tint = if (index < currentRoomsList.lastIndex) MaterialTheme.colorScheme.primary else Color.LightGray,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                        // Левая часть: Номер комнаты и штатная норма (с ручным вводом по карандашу)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Комната ${room.roomNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 17.sp,
                                    color = Color(0xFF181C20)
                                )
                                if (isChecked) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "Проверено",
                                        tint = Color(0xFF2E7D32)
                                    )
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Проживает (норма): ${room.capacity} чел.",
                                    fontSize = 13.sp,
                                    color = Color.DarkGray
                                )
                                if (canEditThisFloor) {
                                    IconButton(
                                        onClick = {
                                            roomToEditNorm = room
                                            manualNormInput = room.capacity.toString()
                                        },
                                        modifier = Modifier.height(26.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Изменить количество живущих",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            if (isChecked) {
                                Text(
                                    text = if (absentInRoom == 0) "Все на месте ($currentCount из ${room.capacity})"
                                    else "Ночует: $currentCount из ${room.capacity} | Отс.: $absentInRoom чел.",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (absentInRoom > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                                )
                            } else {
                                Text(
                                    text = "Ожидает обхода (макс. ${room.capacity})",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                            }
                        }

                        // Правая часть: Кнопки (- / число / + / ОК), строго ограниченные нормой room.capacity
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilledIconButton(
                                onClick = {
                                    val next = (currentCount - 1).coerceAtLeast(0)
                                    onSaveRoomHeadcount(room, next)
                                },
                                enabled = canEditThisFloor && currentCount > 0
                            ) {
                                Icon(Icons.Default.Remove, contentDescription = "Меньше")
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = if (isChecked) "$currentCount/${room.capacity}" else "${room.capacity}?",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                )
                            }

                            FilledIconButton(
                                onClick = {
                                    val next = (currentCount + 1).coerceAtMost(room.capacity)
                                    onSaveRoomHeadcount(room, next)
                                },
                                enabled = canEditThisFloor && currentCount < room.capacity
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Больше")
                            }

                            if (!isChecked && canEditThisFloor) {
                                OutlinedButton(
                                    onClick = { onSaveRoomHeadcount(room, room.capacity) }
                                ) {
                                    Text("ОК", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Диалог РУЧНОГО ввода количества живущих в комнате
    roomToEditNorm?.let { room ->
        AlertDialog(
            onDismissRequest = { roomToEditNorm = null },
            title = { Text("Сколько человек живет в комн. ${room.roomNumber}?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Введите фактическое количество проживающих по списку (норма комнаты). " +
                            "При обходе нельзя будет указать число ночующих выше этого значения:",
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = manualNormInput,
                        onValueChange = { input ->
                            manualNormInput = input.filter { it.isDigit() }.take(2)
                        },
                        label = { Text("Количество проживающих (чел.)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val newCap = manualNormInput.toIntOrNull() ?: room.capacity
                        onUpdateRoomCapacity(room, newCap)
                        roomToEditNorm = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { roomToEditNorm = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
