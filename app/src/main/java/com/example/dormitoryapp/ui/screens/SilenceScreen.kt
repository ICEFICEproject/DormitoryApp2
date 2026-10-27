package com.example.dormitoryapp.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.data.SilenceIncidentRecord
import com.example.dormitoryapp.data.StaffRole
import java.time.LocalDate

/**
 * Экран 4: Контроль тишины комнат во время комендантского часа (23:00 - 00:30).
 * Поддерживает официальную сессию обхода для ИС «Цифровое общежитие» (POST /inspections/sync)
 * и оффлайн фиксацию шума.
 */
@Composable
fun SilenceScreen(
    selectedDate: String,
    incidents: List<SilenceIncidentRecord>,
    currentRole: StaffRole,
    assignedFloor: Int,
    canEditFloor: (Int) -> Boolean,
    canDeleteIncident: (SilenceIncidentRecord) -> Boolean = { canEditFloor(it.floor) },
    onAddIncident: (Int, String, String) -> Unit,
    onDeleteIncident: (SilenceIncidentRecord) -> Unit,
    quietStartedAt: String? = null,
    quietIncidentsCount: Int = 0,
    quietSessionError: String? = null,
    onStartQuietSession: (() -> Unit)? = null,
    onFinishQuietSession: (() -> Unit)? = null,
    onCancelQuietSession: (() -> Unit)? = null
) {
    val isDeputy = currentRole == StaffRole.DEPUTY_EDUCATOR
    var floorFilter by remember(assignedFloor, isDeputy) {
        mutableIntStateOf(if (isDeputy) 0 else assignedFloor)
    }
    var showAddDialog by remember { mutableStateOf(false) }

    var roomInput by remember(assignedFloor) { mutableStateOf("${assignedFloor}00") }
    var selectedType by remember { mutableStateOf("Шум после отбоя") }
    var commentInput by remember { mutableStateOf("") }

    // Замечания по тишине отображаются СТРОГО для выбранной в календаре даты
    val dateIncidents = remember(incidents, selectedDate) {
        incidents.filter { it.date == selectedDate }
    }
    val filteredIncidents = if (floorFilter == 0) dateIncidents else dateIncidents.filter { it.floor == floorFilter }

    Scaffold(
        floatingActionButton = {
            if (canEditFloor(assignedFloor)) {
                ExtendedFloatingActionButton(
                    onClick = {
                        roomInput = "${assignedFloor}00"
                        showAddDialog = true
                    },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = {
                        Text(if (isDeputy) "Зафиксировать шум" else "Зафиксировать шум ($assignedFloor эт.)")
                    }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            // Фильтр этажа
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = floorFilter == 0,
                    onClick = { floorFilter = 0 },
                    label = { Text("Все") }
                )
                for (floor in 2..5) {
                    val isMyFloor = floor == assignedFloor
                    FilterChip(
                        selected = floorFilter == floor,
                        onClick = { floorFilter = floor },
                        label = { Text(if (isMyFloor) "$floor эт. ★" else "$floor эт.") }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Блок управления официальной проверкой тишины (ИС «Цифровое общежитие»)
            if (canEditFloor(assignedFloor) && onStartQuietSession != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (quietStartedAt != null) Color(0xFFEDE7F6) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (quietStartedAt == null) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Официальный контроль тишины (23:00–23:59)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Фиксирует начало и конец проверки тишины для сервера",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                }
                                Button(
                                    onClick = onStartQuietSession,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Начать обход", fontSize = 12.sp)
                                }
                            }
                        } else {
                            val startTimeStr = quietStartedAt.substringAfter('T').take(5)
                            Text(
                                text = "Обход тишины начат в $startTimeStr • Шумящих комнат: $quietIncidentsCount",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF512DA8)
                            )
                            Text(
                                text = if (quietIncidentsCount == 0) "Если нарушений нет — при завершении запишется: «на этаже тихо»." else "Комнаты с нарушениями будут отправлены на сервер.",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                            if (!quietSessionError.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "✕ $quietSessionError",
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
                                    onClick = { onFinishQuietSession?.invoke() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF512DA8)),
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        if (quietIncidentsCount == 0) "Завершить: На этаже тихо" else "Завершить обход ($quietIncidentsCount)",
                                        fontSize = 12.sp
                                    )
                                }
                                OutlinedButton(
                                    onClick = { onCancelQuietSession?.invoke() },
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

            // Заголовок даты и счетчик
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Замечания за $selectedDate",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dateIncidents.isNotEmpty()) {
                    Surface(
                        color = Color(0xFFFFCDD2),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Нарушений: ${dateIncidents.size}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFC62828),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (filteredIncidents.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Нарушений тишины за $selectedDate не зафиксировано.\nВ общежитии порядок!",
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filteredIncidents, key = { it.id }) { inc ->
                        val canModifyThis = canDeleteIncident(inc)
                        val today = LocalDate.now()
                        val incDate = runCatching { LocalDate.parse(inc.date) }.getOrDefault(today)
                        val isPast = incDate.isBefore(today)
                        val isCreatedByDeputy = inc.inspectorName.contains("Заместитель", ignoreCase = true) ||
                                inc.inspectorName.contains("Зам.", ignoreCase = true) ||
                                inc.inspectorName.contains("Воспитател", ignoreCase = true)

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFFCE8E6),
                                contentColor = Color(0xFF181C20)
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Комната ${inc.roomNumber} (${inc.floor} эт.) • ${inc.time}",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = Color(0xFFB71C1C)
                                        )
                                        Surface(
                                            color = Color(0xFFFFCDD2),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = "+1 штр. балл",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFC62828),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Тип: ${inc.violationType}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                    if (inc.comment.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "Описание: ${inc.comment}", fontSize = 13.sp)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Дата: ${inc.date} • Проверил: ${inc.inspectorName}",
                                        fontSize = 11.sp,
                                        color = Color.DarkGray
                                    )
                                    if (!canModifyThis) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val lockMsg = when {
                                            isPast -> "🔒 Запись за прошлую дату (удалить может только зам. воспитателя)"
                                            isCreatedByDeputy -> "🔒 Зафиксировано зам. воспитателя (удалить может только зам. воспитателя)"
                                            else -> "🔒 Нет прав на удаление этой записи"
                                        }
                                        Text(
                                            text = lockMsg,
                                            fontSize = 11.sp,
                                            color = Color(0xFF7B1FA2),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }

                                if (canModifyThis) {
                                    IconButton(onClick = { onDeleteIncident(inc) }) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Удалить",
                                            tint = Color(0xFFC62828)
                                        )
                                    }
                                } else {
                                    Icon(
                                        Icons.Default.Lock,
                                        contentDescription = "Заблокировано",
                                        tint = Color(0xFF7B1FA2),
                                        modifier = Modifier.padding(8.dp).size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        val violationTypes = listOf(
            "Шум после отбоя",
            "Громкая музыка / крики",
            "Гости после комендантского часа",
            "Повторное нарушение"
        )

        val minFloor = if (isDeputy) 2 else assignedFloor
        val maxFloor = if (isDeputy) 5 else assignedFloor
        val floorPrompt = if (isDeputy) "200–534 (комнаты 00–34)" else "${assignedFloor}00–${assignedFloor}34"
        val enteredNum = roomInput.toIntOrNull()
        val floorOfRoom = if (enteredNum != null) enteredNum / 100 else 0
        val numOnFloor = if (enteredNum != null) enteredNum % 100 else -1
        val isRoomValid = enteredNum != null && floorOfRoom in minFloor..maxFloor && numOnFloor in 0..34

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    if (isDeputy) "Нарушение тишины (2–5 эт.)"
                    else "Нарушение тишины ($assignedFloor этаж)"
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "📅 Дата записи: $selectedDate",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }

                    OutlinedTextField(
                        value = roomInput,
                        onValueChange = { roomInput = it.filter { ch -> ch.isDigit() }.take(3) },
                        label = { Text("Номер комнаты ($floorPrompt)") },
                        isError = roomInput.isNotBlank() && !isRoomValid,
                        supportingText = if (roomInput.isNotBlank() && !isRoomValid) {
                            { Text("В общежитии комнаты на этаже только с 00 по 34!", color = MaterialTheme.colorScheme.error, fontSize = 11.sp) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "⚠️ Каждое замечание добавляет комнате +1 штрафной балл за всё время.",
                        fontSize = 12.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Medium
                    )

                    Text("Характер нарушения:", fontWeight = FontWeight.Medium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (vt in violationTypes) {
                            FilterChip(
                                selected = selectedType == vt,
                                onClick = { selectedType = vt },
                                label = { Text(vt, fontSize = 12.sp) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    OutlinedTextField(
                        value = commentInput,
                        onValueChange = { commentInput = it.take(500) },
                        label = { Text("Комментарий (без ФИО, до 500 символов)") },
                        supportingText = { Text("Не указывайте ФИО — его читают сотрудники ИС", fontSize = 10.sp) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (enteredNum != null) {
                            onAddIncident(enteredNum, selectedType, commentInput)
                            commentInput = ""
                            showAddDialog = false
                        }
                    },
                    enabled = isRoomValid
                ) {
                    Text("Записать в журнал")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
