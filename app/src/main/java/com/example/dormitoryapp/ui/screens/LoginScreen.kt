package com.example.dormitoryapp.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dormitoryapp.R
import com.example.dormitoryapp.data.LeaderApiService

/**
 * Экран авторизации (Логин и Пароль) для привязки к ИС «Цифровое общежитие» (POST /auth).
 * Поддерживает смену пароля (HTTP 409), ввод адреса локального сервера и отображение модели телефона.
 */
@Composable
fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    serverUrl: String = LeaderApiService.DEFAULT_BASE_URL,
    passwordChangeMessage: String? = null,
    onUpdateServerUrl: (String) -> Unit = {},
    onDismissPasswordChange: () -> Unit = {},
    onDemoLogin: () -> Unit = {},
    onLoginSubmit: (login: String, pass: String, newPass: String?) -> Unit
) {
    var login by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    var showServerSettingsDialog by remember { mutableStateOf(false) }
    var tempServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }

    // Для диалога смены временного пароля (HTTP 409)
    var newPassword by remember { mutableStateOf("") }
    var isNewPasswordVisible by remember { mutableStateOf(false) }
    var newPasswordError by remember { mutableStateOf<String?>(null) }

    val keyboardController = LocalSoftwareKeyboardController.current
    val deviceName = remember { LeaderApiService.getDeviceName() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF3F5F8))
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.White,
                contentColor = Color(0xFF181C20)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Верхний ряд: Настройки сервера
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = {
                        tempServerUrl = serverUrl
                        showServerSettingsDialog = true
                    }) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Настройки сервера",
                            tint = Color.Gray
                        )
                    }
                }

                // Логотип
                Image(
                    painter = painterResource(id = R.drawable.ic_crown_logo),
                    contentDescription = "Логотип",
                    modifier = Modifier
                        .height(68.dp)
                        .width(90.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "ИС «Цифровое общежитие»",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Привязка устройства старосты",
                    fontSize = 13.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Поле «Логин»
                OutlinedTextField(
                    value = login,
                    onValueChange = { login = it },
                    label = { Text("Логин") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, tint = Color.Gray)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Поле «Пароль»
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    leadingIcon = {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray)
                    },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Показать/скрыть пароль"
                            )
                        }
                    },
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            keyboardController?.hide()
                            if (login.isNotBlank() && password.isNotBlank()) {
                                onLoginSubmit(login, password, null)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Информация об устройстве (передается в device_name)
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Smartphone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Устройство: $deviceName",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }

                // Сообщение об ошибке
                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFCE8E6)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFC62828),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = Color(0xFFB71C1C),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Кнопка «Войти и привязать»
                Button(
                    onClick = {
                        keyboardController?.hide()
                        onLoginSubmit(login, password, null)
                    },
                    enabled = !isLoading && login.isNotBlank() && password.isNotBlank(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else {
                        Text(
                            text = "Войти и привязать",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Кнопка для тестирования приложения без сервера
                androidx.compose.material3.OutlinedButton(
                    onClick = onDemoLogin,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                ) {
                    Text(
                        text = "🚀 Демо-вход (без сервера)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Привязка выполняется один раз на вахте по сети общежития. Токен действует до 90 дней.",
                    fontSize = 11.sp,
                    color = Color(0xFF546E7A),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Сервер: $serverUrl",
                    fontSize = 10.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    // Диалог смены временного пароля (HTTP 409 password_change_required)
    if (!passwordChangeMessage.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = onDismissPasswordChange,
            title = {
                Text(
                    text = "Смена временного пароля",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = passwordChangeMessage,
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    Text(
                        text = "Новый пароль должен быть не короче 10 символов и содержать буквы и цифры.",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    OutlinedTextField(
                        value = newPassword,
                        onValueChange = {
                            newPassword = it
                            newPasswordError = null
                        },
                        label = { Text("Новый пароль") },
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
                                Icon(
                                    if (isNewPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            }
                        },
                        visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (newPasswordError != null) {
                        Text(
                            text = newPasswordError!!,
                            color = Color(0xFFC62828),
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPassword.trim()
                        val hasLetter = trimmed.any { it.isLetter() }
                        val hasDigit = trimmed.any { it.isDigit() }
                        if (trimmed.length < 10 || !hasLetter || !hasDigit) {
                            newPasswordError = "Пароль должен содержать минимум 10 знаков, буквы и цифры"
                        } else {
                            onDismissPasswordChange()
                            onLoginSubmit(login, password, trimmed)
                        }
                    }
                ) {
                    Text("Сохранить и войти")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissPasswordChange) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог настройки адреса сервера (IP/порт)
    if (showServerSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showServerSettingsDialog = false },
            title = {
                Text(
                    text = "Адрес сервера общежития",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Укажите базовый URL сервера в локальной сети на вахте:",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )
                    OutlinedTextField(
                        value = tempServerUrl,
                        onValueChange = { tempServerUrl = it },
                        label = { Text("Базовый URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    TextButton(onClick = { tempServerUrl = LeaderApiService.DEFAULT_BASE_URL }) {
                        Text("Сбросить на значение по умолчанию (172.17.0.21:8000)")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateServerUrl(tempServerUrl)
                        showServerSettingsDialog = false
                    }
                ) {
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerSettingsDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
