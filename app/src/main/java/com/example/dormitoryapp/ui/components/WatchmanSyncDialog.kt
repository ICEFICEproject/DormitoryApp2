package com.example.dormitoryapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.data.InspectionEntity
import com.example.dormitoryapp.data.LeaderApiService
import com.example.dormitoryapp.data.LeaderProfile

/**
 * Диалог панели «На вахте: Связь и синхронизация».
 * Реализует сценарий:
 * 1. Проверить соединение (GET /ping)
 * 2. Отправить данные (POST /inspections/sync)
 * 3. Если токен истёк (401), сразу запрашивает логин и пароль и отправляет данные в тот же заход!
 */
@Composable
fun WatchmanSyncDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    profile: LeaderProfile?,
    serverUrl: String,
    onUpdateServerUrl: (String) -> Unit,
    pendingCount: Int,
    inspectionsHistory: List<InspectionEntity>,
    isPingLoading: Boolean,
    lastPingResult: String?,
    pingErrorMessage: String?,
    onPingClick: () -> Unit,
    isSyncing: Boolean,
    lastSyncSuccessMessage: String?,
    syncErrorMessage: String?,
    onSyncClick: () -> Unit,
    requiresReAuth: Boolean,
    onDismissReAuth: () -> Unit,
    onReAuthSubmit: (login: String, pass: String) -> Unit
) {
    if (!isOpen) return

    var showServerSettings by remember { mutableStateOf(false) }
    var tempUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    // Состояния для окна повторного входа при 401
    var reAuthLogin by remember { mutableStateOf(profile?.user?.lastName ?: "") }
    var reAuthPassword by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Cable,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "На вахте: Синхронизация",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                }
                IconButton(onClick = {
                    tempUrl = serverUrl
                    showServerSettings = true
                }) {
                    Icon(Icons.Default.Settings, contentDescription = "Сервер", tint = Color.Gray)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Статус очереди проверок
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (pendingCount > 0) Color(0xFFFFF3E0) else Color(0xFFE8F5E9)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (pendingCount > 0) Color(0xFFE65100).copy(alpha = 0.15f)
                                    else Color(0xFF2E7D32).copy(alpha = 0.15f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (pendingCount > 0) Icons.Default.CloudUpload else Icons.Default.CloudDone,
                                contentDescription = null,
                                tint = if (pendingCount > 0) Color(0xFFE65100) else Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = if (pendingCount > 0) "Готово к отправке: $pendingCount" else "Очередь пуста",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (pendingCount > 0) Color(0xFFE65100) else Color(0xFF2E7D32)
                            )
                            Text(
                                text = if (pendingCount > 0) "Подключите провод на вахте и нажмите «Отправить»" else "Все завершённые проверки приняты сервером",
                                fontSize = 11.sp,
                                color = Color.DarkGray
                            )
                        }
                    }
                }

                // Кнопка 1: Проверить соединение (GET /ping)
                OutlinedButton(
                    onClick = onPingClick,
                    enabled = !isPingLoading && !isSyncing,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isPingLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверка связи...")
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("1. Проверить связь (Ping)")
                    }
                }

                // Результат ping
                if (!lastPingResult.isNullOrBlank()) {
                    Text(
                        text = "✓ $lastPingResult",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!pingErrorMessage.isNullOrBlank()) {
                    Text(
                        text = "✕ $pingErrorMessage",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Кнопка 2: Отправить данные (POST /inspections/sync)
                Button(
                    onClick = onSyncClick,
                    enabled = !isSyncing && !isPingLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отправка на сервер...")
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("2. Отправить данные ($pendingCount)")
                    }
                }

                // Результаты отправки
                if (!lastSyncSuccessMessage.isNullOrBlank()) {
                    Text(
                        text = "✓ $lastSyncSuccessMessage",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!syncErrorMessage.isNullOrBlank()) {
                    Text(
                        text = "✕ $syncErrorMessage",
                        color = Color(0xFFC62828),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // История проверок и статусы
                Text(
                    text = "История проверок на устройстве:",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                if (inspectionsHistory.isEmpty()) {
                    Text(
                        text = "Вы ещё не проводили проверок чистоты или тишины.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(inspectionsHistory) { insp ->
                            InspectionHistoryItem(insp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть")
            }
        }
    )

    // Модальное окно повторного входа при 401 (быстрая авторизация на вахте без потери очереди)
    if (requiresReAuth) {
        AlertDialog(
            onDismissRequest = onDismissReAuth,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = Color(0xFFC62828))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Срок сессии истёк (401)")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Токен устройства был отозван или истёк срок 90 дней. Введите пароль для повторного входа. Неотправленные проверки будут отправлены сразу же!",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = reAuthLogin,
                        onValueChange = { reAuthLogin = it },
                        label = { Text("Логин") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reAuthPassword,
                        onValueChange = { reAuthPassword = it },
                        label = { Text("Пароль") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reAuthLogin.isNotBlank() && reAuthPassword.isNotBlank()) {
                            onReAuthSubmit(reAuthLogin, reAuthPassword)
                        }
                    },
                    enabled = reAuthLogin.isNotBlank() && reAuthPassword.isNotBlank()
                ) {
                    Text("Войти и отправить данные")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissReAuth) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог настройки адреса сервера
    if (showServerSettings) {
        AlertDialog(
            onDismissRequest = { showServerSettings = false },
            title = { Text("Настройки адреса сервера") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Базовый URL сервера общежития:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = tempUrl,
                        onValueChange = { tempUrl = it },
                        label = { Text("URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { tempUrl = LeaderApiService.DEFAULT_BASE_URL }) {
                        Text("Сброс: 172.17.0.21:8000")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onUpdateServerUrl(tempUrl)
                    showServerSettings = false
                }) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerSettings = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun InspectionHistoryItem(insp: InspectionEntity) {
    val statusColor = when (insp.syncStatus) {
        InspectionEntity.STATUS_ACCEPTED -> Color(0xFF2E7D32)
        InspectionEntity.STATUS_DUPLICATE -> Color(0xFF1565C0)
        InspectionEntity.STATUS_REJECTED -> Color(0xFFC62828)
        else -> Color(0xFFE65100) // PENDING
    }

    val statusLabel = when (insp.syncStatus) {
        InspectionEntity.STATUS_ACCEPTED -> "Принята (#${insp.inspectionId ?: ""})"
        InspectionEntity.STATUS_DUPLICATE -> "Уже принята ранее"
        InspectionEntity.STATUS_REJECTED -> "Отклонена: ${insp.rejectMessage ?: insp.rejectReason ?: ""}"
        else -> "В очереди на отправку"
    }

    val typeLabel = if (insp.type == "quiet") "Тишина (${insp.floor} эт.)" else "Чистота (${insp.floor} эт.)"
    val timeLabel = insp.startedAt.take(16).replace("T", " ")

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = typeLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Text(
                    text = timeLabel,
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = statusLabel,
                    fontSize = 11.sp,
                    color = statusColor,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
