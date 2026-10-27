package com.example.dormitoryapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.data.CleanlinessRecord
import com.example.dormitoryapp.data.DormRoom
import com.example.dormitoryapp.data.FloorDutyScheduleRecord
import com.example.dormitoryapp.data.HeadcountRecord
import com.example.dormitoryapp.data.KitchenDutyRecord
import com.example.dormitoryapp.data.RemoteStaffUser
import com.example.dormitoryapp.data.SilenceIncidentRecord
import com.example.dormitoryapp.data.StaffRole
import java.time.LocalDate

/**
 * Экран 1: Главная сводка.
 * - Для Заместителя воспитателя: показывает всё общежитие и все этажи (2–5).
 * - Для Командира этажа, Старосты и Инициативной группы: показывает ТОЛЬКО их закрепленный этаж,
 *   чтобы не перегружать экран чужими данными.
 */
@Composable
fun DashboardScreen(
    selectedDate: String,
    currentRole: StaffRole,
    assignedFloor: Int,
    allRooms: List<DormRoom>,
    headcountRecords: List<HeadcountRecord>,
    cleanlinessRecords: List<CleanlinessRecord>,
    kitchenDutyRecords: List<KitchenDutyRecord>,
    silenceIncidents: List<SilenceIncidentRecord>,
    floorDutySchedules: List<FloorDutyScheduleRecord> = emptyList(),
    staffUsers: List<RemoteStaffUser> = emptyList(),
    onSaveFloorDutyWeek: (Int, List<FloorDutyScheduleRecord>) -> Unit = { _, _ -> },
    onNavigateToFloorHeadcount: (Int) -> Unit,
    onOpenShiftSummary: () -> Unit = {}
) {
    val isDeputy = currentRole == StaffRole.DEPUTY_EDUCATOR
    var editingFloor by remember { mutableStateOf<Int?>(null) }
    var selectedDutyFloor by remember { mutableIntStateOf(assignedFloor) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (isDeputy) {
            // --- РЕЖИМ ЗАМЕСТИТЕЛЯ ВОСПИТАТЕЛЯ (ВСЕ ЭТАЖИ 2–5) ---
            val totalNorm = allRooms.sumOf { it.capacity }
            val totalPresent = headcountRecords.sumOf { it.presentCount }
            val checkedRoomNumbers = headcountRecords.map { it.roomNumber }.toSet()
            val normOfCheckedRooms = allRooms.filter { it.roomNumber in checkedRoomNumbers }.sumOf { it.capacity }
            val totalAbsentInChecked = (normOfCheckedRooms - totalPresent).coerceAtLeast(0)

            val todaySilenceIncidents = silenceIncidents.filter { it.date == selectedDate }
            val dirtyRoomsCount = cleanlinessRecords.count { it.status != "Чисто" }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Общая сводка общежития за $selectedDate",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Проверено комнат: ${checkedRoomNumbers.size} из ${allRooms.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStatBox(
                                label = "По норме (всего)",
                                value = "$totalNorm",
                                subLabel = "проверенных: $normOfCheckedRooms",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SummaryStatBox(
                                label = "Ночуют (Факт)",
                                value = "$totalPresent",
                                subLabel = "в общежитии",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SummaryStatBox(
                                label = "Отсутствуют",
                                value = "$totalAbsentInChecked",
                                subLabel = "Норма − Факт",
                                highlightRed = totalAbsentInChecked > 0,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            // Карточка «Итоги 15-дневной смены (Антирейтинг)»
            item {
                ShiftSummaryBannerCard(
                    title = "Итоги 15-дневной смены",
                    subtitle = "Антирейтинг комнат по чистоте и тишине, образцовые комнаты и экспорт в Excel",
                    onClick = onOpenShiftSummary
                )
            }

            // График дежурств по этажам (для зам. воспитателя с переключателем этажей)
            item {
                val dutyFloorSchedule = floorDutySchedules.filter { it.floor == selectedDutyFloor }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "График дежурств сотрудников",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            TextButton(
                                onClick = { editingFloor = selectedDutyFloor },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Изменить", fontSize = 13.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Переключатель этажей (2, 3, 4, 5 этаж)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (f in 2..5) {
                                val isSelected = f == selectedDutyFloor
                                Surface(
                                    onClick = { selectedDutyFloor = f },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier.padding(vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "$f этаж",
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FloorDutyScheduleList(
                            duties = dutyFloorSchedule
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Расчет численности по всем этажам (2–5)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            for (floor in 2..5) {
                item {
                    FloorSummaryCard(
                        floor = floor,
                        allRooms = allRooms,
                        headcountRecords = headcountRecords,
                        onClick = { onNavigateToFloorHeadcount(floor) }
                    )
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Дисциплина и чистота по общежитию ($selectedDate)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Проверено комнат на чистоту: ${cleanlinessRecords.size} (с замечаниями: $dirtyRoomsCount)")
                        Text("• Дежурства по кухням отмечены: ${kitchenDutyRecords.size} из 8 кухонь")
                        Text("• Нарушений тишины после отбоя: ${todaySilenceIncidents.size}")
                    }
                }
            }
        } else {
            // --- РЕЖИМ СОТРУДНИКА ЭТАЖА (ТОЛЬКО ЗАКРЕПЛЕННЫЙ ЭТАЖ) ---
            val floor = assignedFloor
            val floorRooms = allRooms.filter { it.floor == floor }
            val floorHeadcount = headcountRecords.filter { it.floor == floor }
            val floorCleanliness = cleanlinessRecords.filter { it.floor == floor }
            val floorKitchens = kitchenDutyRecords.filter { it.floor == floor }
            val floorSilence = silenceIncidents.filter { it.floor == floor && it.date == selectedDate }

            val floorNormTotal = floorRooms.sumOf { it.capacity }
            val floorPresent = floorHeadcount.sumOf { it.presentCount }
            val floorCheckedSet = floorHeadcount.map { it.roomNumber }.toSet()
            val floorNormChecked = floorRooms.filter { it.roomNumber in floorCheckedSet }.sumOf { it.capacity }
            val floorAbsent = (floorNormChecked - floorPresent).coerceAtLeast(0)

            val dirtyFloorRooms = floorCleanliness.filter { it.status != "Чисто" }
            val northDuties = floorKitchens.filter { it.kitchenSide.startsWith("Северная") }
            val southDuties = floorKitchens.filter { it.kitchenSide.startsWith("Южная") }

            // 1. Главная карточка своего этажа
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Ваш закрепленный этаж: $floor этаж (${floor}00–${floor}34)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Дата: $selectedDate • Проверено комнат: ${floorCheckedSet.size} из ${floorRooms.size}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStatBox(
                                label = "Норма этажа",
                                value = "$floorNormTotal",
                                subLabel = "проверенных: $floorNormChecked",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SummaryStatBox(
                                label = "Ночуют (Факт)",
                                value = "$floorPresent",
                                subLabel = "в комнатах",
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            SummaryStatBox(
                                label = "Отсутствуют",
                                value = "$floorAbsent",
                                subLabel = "Норма − Факт",
                                highlightRed = floorAbsent > 0,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { onNavigateToFloorHeadcount(floor) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Перейти к обходу $floor этажа")
                        }
                    }
                }
            }

            // Карточка «Итоги 15-дневной смены (Антирейтинг)»
            item {
                ShiftSummaryBannerCard(
                    title = "Итоги смены ($floor этаж)",
                    subtitle = "Антирейтинг дежурства за 15 дней, нарушители и образцовые комнаты этажа",
                    onClick = onOpenShiftSummary
                )
            }

            // Карточка графика дежурств по закрепленному этажу
            item {
                val floorDuties = floorDutySchedules.filter { it.floor == floor }
                val canEditSchedule = currentRole == StaffRole.FLOOR_COMMANDER || currentRole == StaffRole.DEPUTY_EDUCATOR

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "График дежурств по этажу",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (canEditSchedule) {
                                TextButton(
                                    onClick = { editingFloor = floor },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Изменить", fontSize = 13.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        FloorDutyScheduleList(
                            duties = floorDuties
                        )
                    }
                }
            }

            // 2. Состояние кухонь закрепленного этажа
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Кухни $floor этажа за сегодня",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        fun formatKitchenText(duties: List<KitchenDutyRecord>): String {
                            if (duties.isEmpty()) return "не назначено"
                            return duties.joinToString(", ") { d ->
                                val role = when {
                                    d.kitchenSide.contains("мытьё", ignoreCase = true) -> "мытьё"
                                    d.kitchenSide.contains("мусор", ignoreCase = true) -> "мусор"
                                    else -> "дежурный"
                                }
                                "$role: комн. ${d.dutyRoomNumber} (${d.status})"
                            }
                        }

                        Text(
                            text = "• Кухня: Северная — ${formatKitchenText(northDuties)}",
                            fontSize = 14.sp
                        )
                        Text(
                            text = "• Кухня: Южная — ${formatKitchenText(southDuties)}",
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // 3. Чистота комнат и тишина закрепленного этажа
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Чистота и тишина на $floor этаже",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text("• Проверено комнат на чистоту: ${floorCleanliness.size} из ${floorRooms.size}")
                        if (dirtyFloorRooms.isEmpty()) {
                            Text(
                                text = "• Замечаний по чистоте нет (везде «Чисто»)",
                                color = Color(0xFF2E7D32),
                                fontSize = 13.sp
                            )
                        } else {
                            Text(
                                text = "• Комнаты с замечаниями (${dirtyFloorRooms.size}): " +
                                    dirtyFloorRooms.joinToString { "${it.roomNumber} (${it.status})" },
                                color = Color(0xFFC62828),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text("• Нарушений тишины после отбоя: ${floorSilence.size}")
                    }
                }
            }
        }
    }

    editingFloor?.let { floorToEdit ->
        val currentDuties = floorDutySchedules.filter { it.floor == floorToEdit }
        val relevantStaff = staffUsers.filter { it.assignedFloor == floorToEdit }
        EditFloorDutyScheduleDialog(
            floor = floorToEdit,
            currentDuties = currentDuties,
            floorStaff = relevantStaff,
            onDismiss = { editingFloor = null },
            onSave = { updatedList ->
                onSaveFloorDutyWeek(floorToEdit, updatedList)
            }
        )
    }
}

@Composable
private fun FloorSummaryCard(
    floor: Int,
    allRooms: List<DormRoom>,
    headcountRecords: List<HeadcountRecord>,
    onClick: () -> Unit
) {
    val floorRooms = allRooms.filter { it.floor == floor }
    val floorRecords = headcountRecords.filter { it.floor == floor }
    val floorNormTotal = floorRooms.sumOf { it.capacity }
    val floorPresent = floorRecords.sumOf { it.presentCount }
    val floorCheckedSet = floorRecords.map { it.roomNumber }.toSet()
    val floorNormChecked = floorRooms.filter { it.roomNumber in floorCheckedSet }.sumOf { it.capacity }
    val floorAbsent = (floorNormChecked - floorPresent).coerceAtLeast(0)

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$floor этаж (${floor}00–${floor}34)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Surface(
                    color = if (floorRecords.size == floorRooms.size && floorRooms.isNotEmpty())
                        Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "Обход: ${floorRecords.size}/${floorRooms.size} комн.",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        fontSize = 12.sp,
                        color = if (floorRecords.size == floorRooms.size && floorRooms.isNotEmpty())
                            Color(0xFF2E7D32) else Color(0xFFE65100),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Штат этажа: $floorNormTotal чел.", fontSize = 13.sp)
                Text(
                    "Ночуют: $floorPresent чел.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Уехали/Отс.: $floorAbsent чел.",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (floorAbsent > 0) Color(0xFFC62828) else Color(0xFF2E7D32)
                )
            }
        }
    }
}

@Composable
private fun SummaryStatBox(
    label: String,
    value: String,
    subLabel: String,
    highlightRed: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(10.dp)
            )
            .padding(10.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Text(text = label, fontSize = 11.sp, color = Color.Gray)
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (highlightRed) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurface
            )
            Text(text = subLabel, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun FloorDutyScheduleList(
    duties: List<FloorDutyScheduleRecord>
) {
    val dayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")
    val dayNames = listOf("Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье")
    val todayDayOfWeek = LocalDate.now().dayOfWeek.value // 1..7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        for (day in 1..7) {
            val isToday = (day == todayDayOfWeek)
            val record = duties.find { it.dayOfWeek == day }
            val person = record?.personName?.trim().orEmpty()
            val role = record?.personRole?.trim().orEmpty()

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(6.dp)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayLabels[day - 1],
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Text(
                        text = dayNames[day - 1],
                        fontSize = 13.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else Color.Gray,
                        fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.width(96.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    if (person.isNotBlank()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = person,
                                fontSize = 13.sp,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (role.isNotBlank()) {
                                Text(
                                    text = role,
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "— не назначен",
                            fontSize = 13.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (isToday) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = "Сегодня",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditFloorDutyScheduleDialog(
    floor: Int,
    currentDuties: List<FloorDutyScheduleRecord>,
    floorStaff: List<RemoteStaffUser>,
    onDismiss: () -> Unit,
    onSave: (List<FloorDutyScheduleRecord>) -> Unit
) {
    val dayNames = listOf(
        "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
    )
    val dayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

    val scheduleMap = remember {
        mutableStateMapOf<Int, Pair<String, String>>().apply {
            for (day in 1..7) {
                val record = currentDuties.find { it.dayOfWeek == day }
                put(day, (record?.personName.orEmpty()) to (record?.personRole.orEmpty()))
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "График дежурств: $floor этаж",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "Назначьте дежурных сотрудников на каждый день недели:",
                        fontSize = 13.sp,
                        color = Color.Gray
                    )
                }

                items(7) { index ->
                    val day = index + 1
                    val currentPair = scheduleMap[day] ?: ("" to "")
                    val personName = currentPair.first

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = dayLabels[index],
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = dayNames[index],
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }

                                if (personName.isNotBlank()) {
                                    TextButton(
                                        onClick = { scheduleMap[day] = "" to "" },
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text("Очистить", fontSize = 11.sp, color = Color(0xFFC62828))
                                    }
                                }
                            }

                            // Быстрый выбор из сотрудников этажа
                            if (floorStaff.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (staff in floorStaff) {
                                        val isSelected = personName == staff.fullName
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isSelected) {
                                                    scheduleMap[day] = "" to ""
                                                } else {
                                                    scheduleMap[day] = staff.fullName to staff.role.title
                                                }
                                            },
                                            label = {
                                                Text(
                                                    text = "${staff.fullName} (${staff.role.title})",
                                                    fontSize = 11.sp
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = personName,
                                onValueChange = { newName ->
                                    val matchedStaff = floorStaff.find { it.fullName.equals(newName.trim(), ignoreCase = true) }
                                    val role = matchedStaff?.role?.title ?: currentPair.second
                                    scheduleMap[day] = newName to role
                                },
                                placeholder = { Text("Или введите ФИО вручную", fontSize = 12.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val result = (1..7).map { day ->
                        val pair = scheduleMap[day] ?: ("" to "")
                        FloorDutyScheduleRecord(
                            floor = floor,
                            dayOfWeek = day,
                            dayName = dayNames[day - 1],
                            personName = pair.first.trim(),
                            personRole = pair.second.trim()
                        )
                    }
                    onSave(result)
                    onDismiss()
                }
            ) {
                Text("Сохранить")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
private fun ShiftSummaryBannerCard(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.tertiary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "15 ДНЕЙ",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f),
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.6f)
            )
        }
    }
}

