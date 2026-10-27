package com.example.dormitoryapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.data.CleanlinessRecord
import com.example.dormitoryapp.data.DormRoom
import com.example.dormitoryapp.data.PenaltyUtils
import com.example.dormitoryapp.data.ReportService
import com.example.dormitoryapp.data.RoomShiftStat
import com.example.dormitoryapp.data.ShiftPreset
import com.example.dormitoryapp.data.ShiftRankingSummary
import com.example.dormitoryapp.data.SilenceIncidentRecord
import com.example.dormitoryapp.data.StaffRole
import java.time.LocalDate

/**
 * Диалог анализа итогов 15-дневной смены (двух недель дежурства):
 * - Выбор пресетов (1–15, 16–30, Последние 15 дней, Свой период до 15 дней)
 * - Антирейтинг самых грязных и шумных комнат
 * - Анти-лидер общего зачёта
 * - Список образцовых комнат с 0 замечаний
 * - Кнопка отправки официального отчёта Excel (.xlsx) руководству
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftSummaryDialog(
    currentRole: StaffRole,
    assignedFloor: Int,
    allRooms: List<DormRoom>,
    allCleanliness: List<CleanlinessRecord>,
    allSilence: List<SilenceIncidentRecord>,
    onDismiss: () -> Unit,
    onSendExcelReport: (summary: ShiftRankingSummary, recipient: ReportService.ReportRecipient) -> Unit
) {
    val isDeputy = currentRole == StaffRole.DEPUTY_EDUCATOR
    var selectedPreset by remember { mutableStateOf(ShiftPreset.FIRST_HALF) }

    val initialDates = remember { PenaltyUtils.getShiftDatesForPreset(ShiftPreset.FIRST_HALF) }
    var startDate by remember { mutableStateOf(initialDates.first) }
    var endDate by remember { mutableStateOf(initialDates.second) }

    var selectedFloorFilter by remember { mutableStateOf<Int?>(if (isDeputy) null else assignedFloor) }
    var showRecipientPicker by remember { mutableStateOf(false) }

    val dateError = PenaltyUtils.validateShiftPeriod(startDate, endDate)

    val summary = remember(startDate, endDate, selectedFloorFilter, allRooms, allCleanliness, allSilence) {
        PenaltyUtils.calculateShiftSummary(
            rooms = allRooms,
            allCleanliness = allCleanliness,
            allSilence = allSilence,
            startDate = startDate.toString(),
            endDate = endDate.toString(),
            filterFloor = selectedFloorFilter
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Итоги 15-дневной смены",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    Text(
                        text = "Антирейтинг и лучшие комнаты",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Выбор пресета смены
                Text(
                    text = "Период смены дежурства (до 15–16 дней):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ShiftPreset.values().forEach { preset ->
                        FilterChip(
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                val dates = PenaltyUtils.getShiftDatesForPreset(preset)
                                startDate = dates.first
                                endDate = dates.second
                            },
                            label = { Text(preset.title, fontSize = 12.sp) }
                        )
                    }
                }

                // 2. Настройка дат вручную при выборе "Свои даты"
                if (selectedPreset == ShiftPreset.CUSTOM) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Настройка диапазона дат смены:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            
                            // Дата начала
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Начало:", fontSize = 12.sp, color = Color.DarkGray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { startDate = startDate.minusDays(1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "$startDate",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { startDate = startDate.plusDays(1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }

                            // Дата окончания
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Окончание:", fontSize = 12.sp, color = Color.DarkGray)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { endDate = endDate.minusDays(1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = "$endDate",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { endDate = endDate.plusDays(1) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                // Предупреждение о нарушении лимита 15 дней
                if (dateError != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFC62828), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = dateError,
                                color = Color(0xFFC62828),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    Text(
                        text = "📅 Выбрано: с $startDate по $endDate (${summary.daysCount} дн. из 15 макс.)",
                        fontSize = 12.sp,
                        color = Color(0xFF1565C0),
                        fontWeight = FontWeight.Medium
                    )
                }

                // 3. Фильтр этажей (для Зам. воспитателя)
                if (isDeputy) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Охват этажей:", fontSize = 12.sp, color = Color.Gray)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterChip(
                                selected = selectedFloorFilter == null,
                                onClick = { selectedFloorFilter = null },
                                label = { Text("Все этажи (2–5)", fontSize = 11.sp) }
                            )
                            for (f in 2..5) {
                                FilterChip(
                                    selected = selectedFloorFilter == f,
                                    onClick = { selectedFloorFilter = f },
                                    label = { Text("$f этаж", fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Показываются данные закрепленного $assignedFloor этажа",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 11.sp,
                            color = Color.DarkGray
                        )
                    }
                }

                HorizontalDivider()

                // 4. Ключевые показатели за смену
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "Штрафов",
                        value = "${summary.totalPenaltyPoints} б.",
                        color = if (summary.totalPenaltyPoints > 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Чистота",
                        value = "${summary.totalCleanlinessPoints} б.",
                        color = Color(0xFFE65100),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Тишина",
                        value = "${summary.totalSilencePoints} б.",
                        color = Color(0xFF7B1FA2),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Образцовых",
                        value = "${summary.exemplaryRooms.size}",
                        color = Color(0xFF2E7D32),
                        modifier = Modifier.weight(1f)
                    )
                }

                // 5. АНТИРЕЙТИНГ ПО ЧИСТОТЕ (САМЫЕ ГРЯЗНЫЕ КОМНАТЫ)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Антирейтинг чистоты (проблемные комнаты):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE65100)
                            )
                        }

                        if (summary.topCleanlinessProblemRooms.isEmpty()) {
                            Text("✅ За смену все комнаты чистые (0 замечаний)!", fontSize = 12.sp, color = Color(0xFF2E7D32))
                        } else {
                            val worst = summary.topCleanlinessProblemRooms.first()
                            Surface(
                                color = Color(0xFFFFECB3),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "🚨 Худшая по чистоте: комн. ${worst.roomNumber} (${worst.floor} эт.)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFFBF360C)
                                    )
                                    Text(
                                        text = "+${worst.cleanlinessPoints} штр. б.",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFFC62828)
                                    )
                                }
                            }

                            summary.topCleanlinessProblemRooms.drop(1).take(4).forEachIndexed { idx, stat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 2}. комн. ${stat.roomNumber} (${stat.floor} эт.)",
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = "+${stat.cleanlinessPoints} штр. б.",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFFD84315)
                                    )
                                }
                            }
                        }
                    }
                }

                // 6. АНТИРЕЙТИНГ ПО ТИШИНЕ (САМЫЕ ШУМНЫЕ КОМНАТЫ)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3E5F5)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Nightlight, contentDescription = null, tint = Color(0xFF6A1B9A), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Антирейтинг тишины (нарушители режима):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF6A1B9A)
                            )
                        }

                        if (summary.topSilenceViolatorRooms.isEmpty()) {
                            Text("✅ Ночи прошли спокойно, нарушений тишины нет!", fontSize = 12.sp, color = Color(0xFF2E7D32))
                        } else {
                            val loudest = summary.topSilenceViolatorRooms.first()
                            Surface(
                                color = Color(0xFFE1BEE7),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "📢 Самая шумная комната: комн. ${loudest.roomNumber} (${loudest.floor} эт.)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF4A148C)
                                    )
                                    Text(
                                        text = "${loudest.silenceIncidentsCount} зам. (+${loudest.silencePoints} б.)",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF4A148C)
                                    )
                                }
                            }

                            summary.topSilenceViolatorRooms.drop(1).take(4).forEachIndexed { idx, stat ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 2}. комн. ${stat.roomNumber} (${stat.floor} эт.)",
                                        fontSize = 12.sp,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = "${stat.silenceIncidentsCount} зам. (+${stat.silencePoints} б.)",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF6A1B9A)
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. ОБРАЗЦОВЫЕ КОМНАТЫ СМЕНЫ (0 ШТРАФОВ)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "👑 Образцовые комнаты смены (${summary.exemplaryRooms.size} комн.):",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF2E7D32)
                            )
                        }

                        if (summary.exemplaryRooms.isEmpty()) {
                            Text("Ни одна комната не смогла пройти смену без замечаний.", fontSize = 12.sp, color = Color.Gray)
                        } else {
                            Text(
                                text = "Идеальная чистота и тишина на протяжении всех 15 дней!",
                                fontSize = 11.sp,
                                color = Color(0xFF1B5E20)
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                summary.exemplaryRooms.take(24).forEach { roomStat ->
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA5D6A7))
                                    ) {
                                        Text(
                                            text = "${roomStat.roomNumber}",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                                if (summary.exemplaryRooms.size > 24) {
                                    Text(
                                        text = "+еще ${summary.exemplaryRooms.size - 24}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF2E7D32),
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { showRecipientPicker = true },
                enabled = dateError == null,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Отправить Excel (.xlsx)")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )

    // Выбор получателя отчета по смене (Командир этажа, Зам. воспитателя, Воспитатель)
    if (showRecipientPicker) {
        AlertDialog(
            onDismissRequest = { showRecipientPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Email, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Кому направить отчёт смены?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Официальный Excel-отчёт (.xlsx) за смену $startDate — $endDate будет отправлен выбранному адресату:",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )

                    ReportService.ReportRecipient.values().forEach { recipient ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showRecipientPicker = false
                                    onSendExcelReport(summary, recipient)
                                },
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val itemIcon = when (recipient) {
                                    ReportService.ReportRecipient.FLOOR_COMMANDER -> Icons.Default.Star
                                    ReportService.ReportRecipient.DEPUTY_EDUCATOR -> Icons.Default.Person
                                    ReportService.ReportRecipient.EDUCATOR -> Icons.Default.AccountCircle
                                }
                                Icon(itemIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(text = recipient.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text(text = recipient.subtitle, fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showRecipientPicker = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(title, fontSize = 10.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}
