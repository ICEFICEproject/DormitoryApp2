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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
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
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.IconButton
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Surface
import com.example.dormitoryapp.data.CleanlinessRecord
import com.example.dormitoryapp.data.DormRoom
import com.example.dormitoryapp.data.KitchenDutyRecord
import com.example.dormitoryapp.data.PenaltyPeriod
import com.example.dormitoryapp.data.PenaltyUtils
import com.example.dormitoryapp.data.RoomPenaltyStats
import com.example.dormitoryapp.data.SilenceIncidentRecord

/**
 * 5 официальных оценок чистоты комнат.
 */
val CLEANLINESS_GRADES = listOf(
    "Чисто",
    "Немного грязно",
    "Умеренно грязно",
    "Грязно",
    "Очень грязно"
)

private val DarkTextColor = Color(0xFF333333)

/**
 * Насыщенный цвет выбранной кнопки оценки (на котором белый текст читается идеально).
 */
private fun gradeActiveColor(grade: String): Color = when (grade) {
    "Чисто" -> Color(0xFF2E7D32)           // Темно-зеленый
    "Немного грязно" -> Color(0xFFB26A00)  // Контрастный янтарно-коричневый (вместо бледно-желтого)
    "Умеренно грязно" -> Color(0xFFD84315) // Насыщенный оранжевый
    "Грязно" -> Color(0xFFC62828)          // Насыщенный красный
    "Очень грязно" -> Color(0xFF6A1B9A)    // Насыщенный темно-фиолетовый
    else -> Color(0xFF455A64)
}

/**
 * Мягкий светлый фон карточки комнаты (на котором всегда используется ТЕМНЫЙ текст).
 */
private fun gradeCardBackgroundColor(grade: String?): Color = when (grade) {
    "Чисто" -> Color(0xFFE6F4EA)
    "Немного грязно" -> Color(0xFFFFF8E1)
    "Умеренно грязно" -> Color(0xFFFFE8D6)
    "Грязно" -> Color(0xFFFCE8E6)
    "Очень грязно" -> Color(0xFFF3E5F5)
    else -> Color.White
}

@Composable
fun CleanlinessScreen(
    selectedDate: String = "",
    selectedFloor: Int,
    assignedFloor: Int,
    canEditFloor: (Int) -> Boolean,
    canModifyInspectorRecord: (String) -> Boolean = { true },
    onSelectFloor: (Int) -> Unit,
    allRooms: List<DormRoom>,
    cleanlinessRecords: List<CleanlinessRecord>,
    allCleanlinessRecords: List<CleanlinessRecord> = emptyList(),
    silenceIncidents: List<SilenceIncidentRecord> = emptyList(),
    kitchenDutyRecords: List<KitchenDutyRecord>,
    roomSortMode: RoomSortMode,
    onSetRoomSortMode: (RoomSortMode) -> Unit,
    getSortedRooms: (List<DormRoom>, RoomSortMode) -> List<DormRoom>,
    onSaveCustomOrder: (Int, List<Int>) -> Unit,
    onResetCustomOrder: (Int) -> Unit,
    customOrderVersion: Int,
    onSaveRoomCleanliness: (DormRoom, String, String) -> Unit,
    onResetRoomCleanliness: (DormRoom) -> Unit,
    onSaveKitchenDuty: (Int, String, Int, String, String) -> Unit,
    onDeleteKitchenDuty: ((Int, String) -> Unit)? = null,
    cleanlinessStartedAt: String? = null,
    cleanlinessScoresCount: Int = 0,
    cleanlinessSessionError: String? = null,
    onStartCleanlinessSession: (() -> Unit)? = null,
    onFinishCleanlinessSession: (() -> Unit)? = null,
    onCancelCleanlinessSession: (() -> Unit)? = null
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Чистота комнат (5 оценок)", fontWeight = FontWeight.SemiBold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Кухни (Северная / Южная)", fontWeight = FontWeight.SemiBold) }
            )
        }

        if (selectedTab == 0) {
            RoomCleanlinessSubScreen(
                selectedDate = selectedDate,
                selectedFloor = selectedFloor,
                assignedFloor = assignedFloor,
                canEditThisFloor = canEditFloor(selectedFloor),
                canModifyInspectorRecord = canModifyInspectorRecord,
                onSelectFloor = onSelectFloor,
                allRooms = allRooms,
                cleanlinessRecords = cleanlinessRecords,
                allCleanlinessRecords = allCleanlinessRecords,
                silenceIncidents = silenceIncidents,
                roomSortMode = roomSortMode,
                onSetRoomSortMode = onSetRoomSortMode,
                getSortedRooms = getSortedRooms,
                onSaveCustomOrder = onSaveCustomOrder,
                onResetCustomOrder = onResetCustomOrder,
                customOrderVersion = customOrderVersion,
                onSaveRoomCleanliness = onSaveRoomCleanliness,
                onResetRoomCleanliness = onResetRoomCleanliness,
                cleanlinessStartedAt = cleanlinessStartedAt,
                cleanlinessScoresCount = cleanlinessScoresCount,
                cleanlinessSessionError = cleanlinessSessionError,
                onStartCleanlinessSession = onStartCleanlinessSession,
                onFinishCleanlinessSession = onFinishCleanlinessSession,
                onCancelCleanlinessSession = onCancelCleanlinessSession
            )
        } else {
            KitchenDutiesSubScreen(
                selectedFloor = selectedFloor,
                assignedFloor = assignedFloor,
                canEditFloor = canEditFloor,
                onSelectFloor = onSelectFloor,
                kitchenDutyRecords = kitchenDutyRecords,
                onSaveKitchenDuty = onSaveKitchenDuty,
                onDeleteKitchenDuty = onDeleteKitchenDuty
            )
        }
    }
}

@Composable
private fun RoomCleanlinessSubScreen(
    selectedDate: String = "",
    selectedFloor: Int,
    assignedFloor: Int,
    canEditThisFloor: Boolean,
    canModifyInspectorRecord: (String) -> Boolean = { true },
    onSelectFloor: (Int) -> Unit,
    allRooms: List<DormRoom>,
    cleanlinessRecords: List<CleanlinessRecord>,
    allCleanlinessRecords: List<CleanlinessRecord> = emptyList(),
    silenceIncidents: List<SilenceIncidentRecord> = emptyList(),
    roomSortMode: RoomSortMode,
    onSetRoomSortMode: (RoomSortMode) -> Unit,
    getSortedRooms: (List<DormRoom>, RoomSortMode) -> List<DormRoom>,
    onSaveCustomOrder: (Int, List<Int>) -> Unit,
    onResetCustomOrder: (Int) -> Unit,
    customOrderVersion: Int,
    onSaveRoomCleanliness: (DormRoom, String, String) -> Unit,
    onResetRoomCleanliness: (DormRoom) -> Unit,
    cleanlinessStartedAt: String? = null,
    cleanlinessScoresCount: Int = 0,
    cleanlinessSessionError: String? = null,
    onStartCleanlinessSession: (() -> Unit)? = null,
    onFinishCleanlinessSession: (() -> Unit)? = null,
    onCancelCleanlinessSession: (() -> Unit)? = null
) {
    var penaltyPeriod by remember { mutableStateOf(PenaltyPeriod.ALL_TIME) }
    val floorRooms = allRooms.filter { it.floor == selectedFloor }
    val penaltyMap = remember(allRooms, allCleanlinessRecords, silenceIncidents, penaltyPeriod, selectedDate) {
        if (selectedDate.isNotBlank()) {
            PenaltyUtils.calculateStatsForPeriod(allRooms, allCleanlinessRecords, silenceIncidents, penaltyPeriod, selectedDate)
        } else {
            PenaltyUtils.calculateAllRoomsStats(allRooms, allCleanlinessRecords, silenceIncidents)
        }
    }
    val currentRoomsList = remember(floorRooms, roomSortMode, customOrderVersion) {
        mutableStateListOf<DormRoom>().apply {
            addAll(getSortedRooms(floorRooms, roomSortMode))
        }
    }

    val density = LocalDensity.current
    val defaultCardHeightPx = remember(density) { with(density) { 120.dp.toPx() } }
    var draggingRoomNumber by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember(density) { mutableFloatStateOf(defaultCardHeightPx) }

    fun moveRoom(fromIdx: Int, toIdx: Int, commit: Boolean = true) {
        if (fromIdx == toIdx || fromIdx !in currentRoomsList.indices || toIdx !in currentRoomsList.indices) return
        val item = currentRoomsList.removeAt(fromIdx)
        currentRoomsList.add(toIdx, item)
        if (commit) {
            onSaveCustomOrder(selectedFloor, currentRoomsList.map { it.roomNumber })
        }
    }

    val recordMap = cleanlinessRecords.associateBy { it.roomNumber }

    var roomForNote by remember { mutableStateOf<DormRoom?>(null) }
    var noteText by remember { mutableStateOf("") }
    var selectedStatusForNote by remember { mutableStateOf("Грязно") }
    val lastClickTimes = remember { mutableMapOf<Int, Long>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        // Выбор этажа (2–5)
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

        // Переключатель периода подсчета штрафов (за всё время / месяц / день)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Штрафы:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.DarkGray
            )
            for (p in PenaltyPeriod.values()) {
                FilterChip(
                    selected = penaltyPeriod == p,
                    onClick = { penaltyPeriod = p },
                    label = { Text(p.shortTitle, fontSize = 11.sp) }
                )
            }
        }

        if (!canEditThisFloor) {
            val isOtherFloor = selectedFloor != assignedFloor
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFCE8E6),
                    contentColor = Color(0xFFB71C1C)
                ),
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
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isOtherFloor) "Только просмотр. Вы закреплены за $assignedFloor этажом."
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

        // Блок управления официальной проверкой чистоты (ИС «Цифровое общежитие»)
        if (canEditThisFloor && onStartCleanlinessSession != null) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (cleanlinessStartedAt != null) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (cleanlinessStartedAt == null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Официальная проверка чистоты",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Зафиксирует точное время начала и конца для отчёта в ИС",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }
                            Button(
                                onClick = onStartCleanlinessSession,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Начать обход", fontSize = 12.sp)
                            }
                        }
                    } else {
                        val startTimeStr = cleanlinessStartedAt.substringAfter('T').take(5)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Обход начат в $startTimeStr • Оценено: $cleanlinessScoresCount",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF2E7D32)
                                )
                                Text(
                                    text = "Оценки сохраняются локально. Завершите обход для очереди отправки.",
                                    fontSize = 11.sp,
                                    color = Color.DarkGray
                                )
                            }
                        }
                        if (!cleanlinessSessionError.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "✕ $cleanlinessSessionError",
                                color = Color(0xFFC62828),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { onFinishCleanlinessSession?.invoke() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Завершить проверку", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = { onCancelCleanlinessSession?.invoke() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Сброс", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Панель маршрута обхода: Обычный / Шахматный / Свой
        RoomSortBar(
            currentMode = roomSortMode,
            onModeSelected = onSetRoomSortMode,
            onResetCustomOrder = { onResetCustomOrder(selectedFloor) }
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(currentRoomsList, key = { _, room -> room.roomNumber }) { index, room ->
                val rec = recordMap[room.roomNumber]
                val cardBg = gradeCardBackgroundColor(rec?.status)
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
                                            onSaveCustomOrder(selectedFloor, currentRoomsList.map { it.roomNumber })
                                        },
                                        onDragCancel = {
                                            draggingRoomNumber = null
                                            dragOffsetY = 0f
                                            onSaveCustomOrder(selectedFloor, currentRoomsList.map { it.roomNumber })
                                        }
                                    )
                                }
                            } else Modifier
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDragging) Color(0xFFE3F2FD) else cardBg,
                        contentColor = DarkTextColor
                    ),
                    border = if (isDragging) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (isDragging) 14.dp else 2.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val penaltyStats = penaltyMap[room.roomNumber] ?: RoomPenaltyStats(room.roomNumber, 0, 0, 0)
                        val canEditThisRoom = canEditThisFloor && (rec == null || canModifyInspectorRecord(rec.inspectorName))
                        val isLockedByDeputy = rec != null && !canModifyInspectorRecord(rec.inspectorName)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (roomSortMode == RoomSortMode.CUSTOM) {
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
                                    Spacer(modifier = Modifier.width(6.dp))
                                }
                                Text(
                                    text = "Комната ${room.roomNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = DarkTextColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = when {
                                        penaltyStats.totalPoints == 0 -> Color(0xFFE8F5E9)
                                        penaltyStats.totalPoints <= 2 -> Color(0xFFFFF3E0)
                                        else -> Color(0xFFFFEBEE)
                                    },
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (penaltyStats.totalPoints == 0) "0 штр. б." else "${penaltyStats.totalPoints} штр. б.",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            penaltyStats.totalPoints == 0 -> Color(0xFF2E7D32)
                                            penaltyStats.totalPoints <= 2 -> Color(0xFFE65100)
                                            else -> Color(0xFFC62828)
                                        },
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            val todayPoints = PenaltyUtils.getCleanlinessPoints(rec?.status)
                            val statusLabel = if (rec != null) "${rec.status} (${todayPoints} б.)" else "Не проверена"
                            Text(
                                text = statusLabel,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (rec != null) gradeActiveColor(rec.status) else Color(0xFF546E7A)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                        val periodLabel = when (penaltyPeriod) {
                            PenaltyPeriod.ALL_TIME -> "за всё время"
                            PenaltyPeriod.CURRENT_MONTH -> "за этот месяц"
                            PenaltyPeriod.SELECTED_DATE -> "за этот день"
                        }
                        if (penaltyStats.totalPoints > 0) {
                            Text(
                                text = "Штрафы ($periodLabel): ${penaltyStats.totalPoints} б. (чистота: ${penaltyStats.cleanlinessPoints} б., тишина: ${penaltyStats.silencePoints} б.)",
                                fontSize = 11.sp,
                                color = Color(0xFF616161),
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            Text(
                                text = "Штрафы ($periodLabel): 0 баллов (образцовый порядок)",
                                fontSize = 11.sp,
                                color = Color(0xFF2E7D32).copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Первый ряд из 3 оценок: Чисто | Немного грязно | Умеренно грязно
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            for (grade in CLEANLINESS_GRADES.take(3)) {
                                GradeChip(
                                    grade = grade,
                                    isSelected = rec?.status == grade,
                                    enabled = canEditThisRoom,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        val last = lastClickTimes[room.roomNumber] ?: 0L
                                        if (now - last < 350L) return@GradeChip
                                        lastClickTimes[room.roomNumber] = now

                                        if (rec?.status == grade) {
                                            onResetRoomCleanliness(room)
                                        } else {
                                            onSaveRoomCleanliness(room, grade, rec?.comment ?: "")
                                        }
                                    }
                                )
                            }
                        }

                        // Второй ряд: Грязно | Очень грязно | Кнопка примечания
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            for (grade in CLEANLINESS_GRADES.drop(3)) {
                                GradeChip(
                                    grade = grade,
                                    isSelected = rec?.status == grade,
                                    enabled = canEditThisRoom,
                                    onClick = {
                                        val now = System.currentTimeMillis()
                                        val last = lastClickTimes[room.roomNumber] ?: 0L
                                        if (now - last < 350L) return@GradeChip
                                        lastClickTimes[room.roomNumber] = now

                                        if (rec?.status == grade) {
                                            onResetRoomCleanliness(room)
                                        } else {
                                            onSaveRoomCleanliness(room, grade, rec?.comment ?: "")
                                        }
                                    }
                                )
                            }

                            if (canEditThisRoom) {
                                TextButton(
                                    onClick = {
                                        roomForNote = room
                                        selectedStatusForNote = rec?.status ?: "Немного грязно"
                                        noteText = rec?.comment ?: ""
                                    }
                                ) {
                                    Text(
                                        text = "💬 Заметка",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0D47A1)
                                    )
                                }
                            }
                        }

                        if (!rec?.comment.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Примечание: ${rec?.comment}",
                                fontSize = 12.sp,
                                color = Color(0xFFB71C1C),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isLockedByDeputy) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color(0xFF7B1FA2),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Зафиксировано зам. воспитателя (изменить может только он)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7B1FA2),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    roomForNote?.let { room ->
        val currentRec = recordMap[room.roomNumber]
        AlertDialog(
            onDismissRequest = { roomForNote = null },
            title = { Text("Заметка по чистоте — Комната ${room.roomNumber}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Текущая оценка: $selectedStatusForNote", fontWeight = FontWeight.Medium)
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Комментарий (мусор, пол, посуда и т.д.)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val statusToSave = if (selectedStatusForNote in CLEANLINESS_GRADES)
                            selectedStatusForNote else "Немного грязно"
                        onSaveRoomCleanliness(room, statusToSave, noteText)
                        roomForNote = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                Row {
                    if (currentRec != null) {
                        TextButton(
                            onClick = {
                                onResetRoomCleanliness(room)
                                roomForNote = null
                            }
                        ) {
                            Text("Сбросить", color = Color(0xFFC62828))
                        }
                    }
                    TextButton(onClick = { roomForNote = null }) {
                        Text("Отмена")
                    }
                }
            }
        )
    }
}

/**
 * Высококонтрастная кнопка оценки чистоты:
 * - Когда не выбрана: чисто белый фон и темный текст (отлично видна на любом бежевом/розовом фоне).
 * - Когда выбрана: насыщенный темный фон и белый жирный текст.
 */
@Composable
private fun GradeChip(
    grade: String,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val activeBg = gradeActiveColor(grade)
    FilterChip(
        selected = isSelected,
        enabled = enabled,
        onClick = onClick,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            labelColor = DarkTextColor,
            selectedContainerColor = activeBg,
            selectedLabelColor = Color.White,
            disabledContainerColor = Color(0xFFECEFF1),
            disabledLabelColor = Color(0xFF455A64),
            disabledSelectedContainerColor = activeBg.copy(alpha = 0.75f)
        ),
        label = {
            val pts = PenaltyUtils.getCleanlinessPoints(grade)
            Text(
                text = "$grade ($pts б.)",
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        }
    )
}

@Composable
private fun KitchenDutiesSubScreen(
    selectedFloor: Int,
    assignedFloor: Int,
    canEditFloor: (Int) -> Boolean,
    onSelectFloor: (Int) -> Unit,
    kitchenDutyRecords: List<KitchenDutyRecord>,
    onSaveKitchenDuty: (Int, String, Int, String, String) -> Unit,
    onDeleteKitchenDuty: ((Int, String) -> Unit)? = null
) {
    val kitchenSides = listOf("Северная", "Южная")
    val canEditThisFloor = canEditFloor(selectedFloor)

    var editingSide by remember { mutableStateOf<String?>(null) }

    var washRoomInput by remember { mutableStateOf("") }
    var washStatusInput by remember { mutableStateOf("Убрано") }
    var washCommentInput by remember { mutableStateOf("") }

    var trashRoomInput by remember { mutableStateOf("") }
    var trashStatusInput by remember { mutableStateOf("Убрано") }
    var trashCommentInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Выбор этажа (2–5)
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

        if (!canEditThisFloor) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFCE8E6),
                    contentColor = Color(0xFFB71C1C)
                ),
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
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFC62828))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Только просмотр. Вы закреплены за $assignedFloor этажом.",
                            fontSize = 12.sp,
                            color = Color(0xFFB71C1C),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    TextButton(onClick = { onSelectFloor(assignedFloor) }) {
                        Text("На $assignedFloor эт.", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text(
            text = "$selectedFloor этаж — Дежурство по кухням",
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.primary
        )

        for (side in kitchenSides) {
            val washRec = kitchenDutyRecords.find {
                it.floor == selectedFloor && (it.kitchenSide == "$side (мытьё)" || it.kitchenSide == side)
            }
            val trashRec = kitchenDutyRecords.find {
                it.floor == selectedFloor && it.kitchenSide == "$side (мусор)"
            }

            val hasAny = washRec != null || trashRec != null
            val isAnyDirty = washRec?.status == "Не убрано" || trashRec?.status == "Не убрано"
            val isAllClean = hasAny && (washRec == null || washRec.status == "Убрано") && (trashRec == null || trashRec.status == "Убрано")

            val bgColor = when {
                isAnyDirty -> Color(0xFFFCE8E6)
                isAllClean -> Color(0xFFE6F4EA)
                else -> Color.White
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = bgColor,
                    contentColor = DarkTextColor
                ),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Кухня: $side",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = DarkTextColor
                        )

                        if (canEditThisFloor) {
                            OutlinedButton(
                                onClick = {
                                    editingSide = side
                                    washRoomInput = washRec?.dutyRoomNumber?.toString()
                                        ?: if (side == "Северная") "${selectedFloor}01" else "${selectedFloor}16"
                                    washStatusInput = washRec?.status ?: "Убрано"
                                    washCommentInput = washRec?.comment ?: ""

                                    trashRoomInput = trashRec?.dutyRoomNumber?.toString()
                                        ?: if (side == "Северная") "${selectedFloor}02" else "${selectedFloor}17"
                                    trashStatusInput = trashRec?.status ?: "Убрано"
                                    trashCommentInput = trashRec?.comment ?: ""
                                }
                            ) {
                                Text(if (!hasAny) "Назначить" else "Изменить")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. Мытьё кухни
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🧽 Мытьё кухни: ", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (washRec != null) {
                            val color = if (washRec.status == "Не убрано") Color(0xFFC62828) else Color(0xFF1B5E20)
                            Text(
                                "комн. ${washRec.dutyRoomNumber} • ${washRec.status}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        } else {
                            Text("не назначена", fontSize = 13.sp, color = Color(0xFF546E7A))
                        }
                    }
                    if (washRec != null && washRec.comment.isNotBlank()) {
                        Text(
                            "   Заметка: ${washRec.comment}",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. Вынос мусора
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🗑️ Вынос мусора: ", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (trashRec != null) {
                            val color = if (trashRec.status == "Не убрано") Color(0xFFC62828) else Color(0xFF1B5E20)
                            Text(
                                "комн. ${trashRec.dutyRoomNumber} • ${trashRec.status}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        } else {
                            Text("не назначена", fontSize = 13.sp, color = Color(0xFF546E7A))
                        }
                    }
                    if (trashRec != null && trashRec.comment.isNotBlank()) {
                        Text(
                            "   Заметка: ${trashRec.comment}",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }

    editingSide?.let { side ->
        val washRec = kitchenDutyRecords.find {
            it.floor == selectedFloor && (it.kitchenSide == "$side (мытьё)" || it.kitchenSide == side)
        }
        val trashRec = kitchenDutyRecords.find {
            it.floor == selectedFloor && it.kitchenSide == "$side (мусор)"
        }

        AlertDialog(
            onDismissRequest = { editingSide = null },
            title = { Text("$selectedFloor этаж • Кухня: $side") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Блок 1: Мытьё кухни
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🧽 1. Мытьё кухни (основная уборка)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            OutlinedTextField(
                                value = washRoomInput,
                                onValueChange = { washRoomInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Номер комнаты (${selectedFloor}00–${selectedFloor}34)") },
                                placeholder = { Text("например: ${selectedFloor}02") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Статус уборки:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (st in listOf("Назначено", "Убрано", "Не убрано")) {
                                    FilterChip(
                                        selected = washStatusInput == st,
                                        onClick = { washStatusInput = st },
                                        label = { Text(st, fontSize = 11.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = washCommentInput,
                                onValueChange = { washCommentInput = it },
                                label = { Text("Примечание (плиты, полы, раковина)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Блок 2: Вынос мусора
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🗑️ 2. Вынос мусора", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                if (washRoomInput.isNotBlank()) {
                                    TextButton(
                                        onClick = { trashRoomInput = washRoomInput },
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text("Та же комната", fontSize = 12.sp)
                                    }
                                }
                            }

                            OutlinedTextField(
                                value = trashRoomInput,
                                onValueChange = { trashRoomInput = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Номер комнаты (${selectedFloor}00–${selectedFloor}34)") },
                                placeholder = { Text("например: ${selectedFloor}03") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Статус выноса мусора:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                for (st in listOf("Назначено", "Убрано", "Не убрано")) {
                                    FilterChip(
                                        selected = trashStatusInput == st,
                                        onClick = { trashStatusInput = st },
                                        label = { Text(st, fontSize = 11.sp) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = trashCommentInput,
                                onValueChange = { trashCommentInput = it },
                                label = { Text("Примечание (баки, пакеты)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 1. Сохранение мытья кухни
                        val washRoom = washRoomInput.toIntOrNull()
                        if (washRoom != null) {
                            val r = washRoom.coerceIn(selectedFloor * 100, selectedFloor * 100 + 34)
                            onSaveKitchenDuty(selectedFloor, "$side (мытьё)", r, washStatusInput, washCommentInput)
                            onDeleteKitchenDuty?.invoke(selectedFloor, side)
                        } else if (washRec != null) {
                            onDeleteKitchenDuty?.invoke(selectedFloor, "$side (мытьё)")
                            onDeleteKitchenDuty?.invoke(selectedFloor, side)
                        }

                        // 2. Сохранение выноса мусора
                        val trashRoom = trashRoomInput.toIntOrNull()
                        if (trashRoom != null) {
                            val r = trashRoom.coerceIn(selectedFloor * 100, selectedFloor * 100 + 34)
                            onSaveKitchenDuty(selectedFloor, "$side (мусор)", r, trashStatusInput, trashCommentInput)
                        } else if (trashRec != null) {
                            onDeleteKitchenDuty?.invoke(selectedFloor, "$side (мусор)")
                        }

                        editingSide = null
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingSide = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}
