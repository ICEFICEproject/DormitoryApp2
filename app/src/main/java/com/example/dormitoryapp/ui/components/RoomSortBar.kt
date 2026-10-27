package com.example.dormitoryapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.data.RoomSortMode

/**
 * Панель выбора порядка комнат:
 * 1. Обычный (1-35)
 * 2. Шахматный (зигзаг по коридору: 500, 534, 501, 533...)
 * 3. Свой порядок (с возможностью перетаскивания и кнопками ▲ ▼)
 */
@Composable
fun RoomSortBar(
    currentMode: RoomSortMode,
    onModeSelected: (RoomSortMode) -> Unit,
    onResetCustomOrder: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Маршрут:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF455A64)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (mode in RoomSortMode.entries) {
                    FilterChip(
                        selected = currentMode == mode,
                        onClick = { onModeSelected(mode) },
                        label = {
                            Text(
                                text = mode.chipLabel,
                                fontSize = 11.sp,
                                fontWeight = if (currentMode == mode) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        if (currentMode == RoomSortMode.CUSTOM) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Зажмите или жмите ▲ ▼ для перемещения",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                TextButton(onClick = onResetCustomOrder) {
                    Icon(
                        Icons.Default.RestartAlt,
                        contentDescription = "Сбросить порядок",
                        tint = Color(0xFFC62828),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                    Text(
                        text = "Сбросить",
                        fontSize = 11.sp,
                        color = Color(0xFFC62828),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
