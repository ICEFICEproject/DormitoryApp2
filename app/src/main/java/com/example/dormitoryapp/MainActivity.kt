package com.example.dormitoryapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.shape.CircleShape
import android.content.Intent
import android.content.ClipData
import androidx.core.content.FileProvider
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import com.example.dormitoryapp.ui.components.WatchmanSyncDialog
import com.example.dormitoryapp.data.ReportService
import com.example.dormitoryapp.data.ShiftRankingSummary
import com.example.dormitoryapp.data.StaffRole
import com.example.dormitoryapp.ui.components.ShiftSummaryDialog
import com.example.dormitoryapp.ui.DormViewModel
import com.example.dormitoryapp.ui.screens.CleanlinessScreen
import com.example.dormitoryapp.ui.screens.DashboardScreen
import com.example.dormitoryapp.ui.screens.HeadcountScreen
import com.example.dormitoryapp.ui.screens.LoginScreen
import com.example.dormitoryapp.ui.screens.SilenceScreen
import com.example.dormitoryapp.ui.theme.DormitoryAppTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DormitoryAppTheme {
                val viewModel: DormViewModel = viewModel()
                val isLoggedIn by viewModel.isLoggedIn.collectAsState()
                val isAuthLoading by viewModel.isAuthLoading.collectAsState()
                val authErrorMessage by viewModel.authErrorMessage.collectAsState()
                val serverUrl by viewModel.serverUrl.collectAsState()
                val passwordChangeMessage by viewModel.passwordChangeMessage.collectAsState()

                if (!isLoggedIn) {
                    LoginScreen(
                        isLoading = isAuthLoading,
                        errorMessage = authErrorMessage,
                        serverUrl = serverUrl,
                        passwordChangeMessage = passwordChangeMessage,
                        onUpdateServerUrl = { viewModel.updateServerUrl(it) },
                        onDismissPasswordChange = { viewModel.dismissPasswordChangePrompt() },
                        onDemoLogin = { viewModel.loginDemo() },
                        onLoginSubmit = { login, password, newPassword ->
                            viewModel.login(login, password, newPassword)
                        }
                    )
                } else {
                    DormMainApp(viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DormMainApp(viewModel: DormViewModel) {
    val currentRole by viewModel.currentRole.collectAsState()
    val currentLogin by viewModel.currentLogin.collectAsState()
    val inspectorName by viewModel.inspectorName.collectAsState()
    val assignedFloor by viewModel.assignedFloor.collectAsState()
    val livingRoomNumber by viewModel.livingRoomNumber.collectAsState()
    val selectedFloor by viewModel.selectedFloor.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()

    val allRooms by viewModel.allRooms.collectAsState()
    val headcountRecords by viewModel.headcountRecords.collectAsState()
    val cleanlinessRecords by viewModel.cleanlinessRecords.collectAsState()
    val allCleanlinessRecords by viewModel.allCleanlinessRecords.collectAsState()
    val kitchenDutyRecords by viewModel.kitchenDutyRecords.collectAsState()
    val silenceIncidents by viewModel.silenceIncidents.collectAsState()
    val floorDutySchedules by viewModel.allFloorDutySchedules.collectAsState()
    val staffUsers by viewModel.staffUsers.collectAsState()

    val roomSortMode by viewModel.roomSortMode.collectAsState()
    val customOrderVersion by viewModel.customOrderVersion.collectAsState()

    // Состояния интеграции с сервером ИС «Цифровое общежитие»
    val leaderProfile by viewModel.leaderProfile.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val pendingCount by viewModel.pendingInspectionsCount.collectAsState()
    val allInspections by viewModel.allInspections.collectAsState()
    val isPingLoading by viewModel.isPingLoading.collectAsState()
    val lastPingResult by viewModel.lastPingResult.collectAsState()
    val pingErrorMessage by viewModel.pingErrorMessage.collectAsState()
    val lastSyncSuccessMessage by viewModel.lastSyncSuccessMessage.collectAsState()
    val syncErrorMessage by viewModel.syncErrorMessage.collectAsState()
    val requiresReAuth by viewModel.requiresReAuth.collectAsState()

    val cleanlinessStartedAt by viewModel.cleanlinessStartedAt.collectAsState()
    val cleanlinessScores by viewModel.cleanlinessSessionScores.collectAsState()
    val cleanlinessSessionError by viewModel.cleanlinessSessionError.collectAsState()

    val quietStartedAt by viewModel.quietStartedAt.collectAsState()
    val quietIncidents by viewModel.quietSessionIncidents.collectAsState()
    val quietSessionError by viewModel.quietSessionError.collectAsState()

    var showWatchmanDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    var currentTab by remember { mutableIntStateOf(0) }
    var showProfileDialog by remember { mutableStateOf(false) }
    var showShiftSummaryDialog by remember { mutableStateOf(false) }
    var showReportTypeDialog by remember { mutableStateOf(false) }
    var showSendReportDialog by remember { mutableStateOf(false) }
    var selectedReportType by remember { mutableStateOf(ReportService.ReportType.ALL) }

    val pagerState = rememberPagerState(initialPage = currentTab) { 4 }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        currentTab = pagerState.currentPage
    }

    val isDeputy = currentRole == StaffRole.DEPUTY_EDUCATOR

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    title = {
                        Column {
                            Text(
                                text = if (isDeputy) "Общежитие (все этажи)"
                                else "Общежитие • Мой этаж: $assignedFloor-й",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentRole.title} • $inspectorName",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        // Кнопка связи и отправки данных на вахте
                        IconButton(onClick = { showWatchmanDialog = true }) {
                            BadgedBox(
                                badge = {
                                    if (pendingCount > 0) {
                                        Badge { Text("$pendingCount") }
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Cable,
                                    contentDescription = "На вахте: Синхронизация",
                                    tint = if (pendingCount > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        IconButton(onClick = { showProfileDialog = true }) {
                            Icon(Icons.Default.Person, contentDescription = "Профиль и выход")
                        }
                    }
                )

                // Аккуратная отдельная панель переключения даты
                val isToday = selectedDate == LocalDate.now().toString()
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.changeDateByDays(-1) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущий день")
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        TextButton(
                            onClick = { viewModel.resetToToday() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = if (isToday) "$selectedDate (Сегодня)" else " $selectedDate",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isToday) MaterialTheme.colorScheme.primary else Color(0xFFC62828)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        IconButton(
                            onClick = { viewModel.changeDateByDays(1) },
                            enabled = !isToday,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = "Следующий день",
                                tint = if (!isToday) MaterialTheme.colorScheme.onSurface else Color.LightGray
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentTab == 0,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(0) }
                    },
                    icon = { Icon(Icons.Default.Assessment, contentDescription = null) },
                    label = { Text("Сводка") }
                )
                NavigationBarItem(
                    selected = currentTab == 1,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(1) }
                    },
                    icon = { Icon(Icons.Default.People, contentDescription = null) },
                    label = { Text("Численность") }
                )
                NavigationBarItem(
                    selected = currentTab == 2,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(2) }
                    },
                    icon = { Icon(Icons.Default.CleaningServices, contentDescription = null) },
                    label = { Text("Чистота") }
                )
                NavigationBarItem(
                    selected = currentTab == 3,
                    onClick = {
                        coroutineScope.launch { pagerState.animateScrollToPage(3) }
                    },
                    icon = { Icon(Icons.Default.Nightlight, contentDescription = null) },
                    label = { Text("Тишина") }
                )
            }
        }
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1
            ) { page ->
                when (page) {
                    0 -> DashboardScreen(
                        selectedDate = selectedDate,
                        currentRole = currentRole,
                        assignedFloor = assignedFloor,
                        allRooms = allRooms,
                        headcountRecords = headcountRecords,
                        cleanlinessRecords = cleanlinessRecords,
                        kitchenDutyRecords = kitchenDutyRecords,
                        silenceIncidents = silenceIncidents,
                        floorDutySchedules = floorDutySchedules,
                        staffUsers = staffUsers,
                        onSaveFloorDutyWeek = { floor, records ->
                            viewModel.saveFloorDutyWeek(floor, records)
                        },
                        onNavigateToFloorHeadcount = { floor ->
                            viewModel.selectFloor(floor)
                            coroutineScope.launch { pagerState.animateScrollToPage(1) }
                        },
                        onOpenShiftSummary = {
                            showShiftSummaryDialog = true
                        }
                    )

                    1 -> HeadcountScreen(
                        selectedFloor = selectedFloor,
                        assignedFloor = assignedFloor,
                        canEditThisFloor = viewModel.canEditFloor(selectedFloor),
                        onSelectFloor = { viewModel.selectFloor(it) },
                        allRooms = allRooms,
                        headcountRecords = headcountRecords,
                        roomSortMode = roomSortMode,
                        onSetRoomSortMode = { viewModel.setRoomSortMode(it) },
                        getSortedRooms = { rooms, mode -> viewModel.getSortedRooms(rooms, mode) },
                        onSaveCustomOrder = { floor, order -> viewModel.saveCustomOrderForFloor(floor, order) },
                        onResetCustomOrder = { floor -> viewModel.resetCustomOrderForFloor(floor) },
                        customOrderVersion = customOrderVersion,
                        onSaveRoomHeadcount = { room, count ->
                            viewModel.saveRoomHeadcount(room, count)
                        },
                        onUpdateRoomCapacity = { room, newCap ->
                            viewModel.updateRoomCapacity(room, newCap)
                        },
                        onFillUncheckedWithNorm = { floor ->
                            viewModel.fillUncheckedRoomsWithNorm(floor)
                        }
                    )

                    2 -> CleanlinessScreen(
                        selectedDate = selectedDate,
                        selectedFloor = selectedFloor,
                        assignedFloor = assignedFloor,
                        canEditFloor = { floor -> viewModel.canEditFloor(floor) },
                        canModifyInspectorRecord = { inspector -> viewModel.canModifyPastDeputyRecord(inspector) },
                        onSelectFloor = { viewModel.selectFloor(it) },
                        allRooms = allRooms,
                        cleanlinessRecords = cleanlinessRecords,
                        allCleanlinessRecords = allCleanlinessRecords,
                        silenceIncidents = silenceIncidents,
                        kitchenDutyRecords = kitchenDutyRecords,
                        roomSortMode = roomSortMode,
                        onSetRoomSortMode = { viewModel.setRoomSortMode(it) },
                        getSortedRooms = { rooms, mode -> viewModel.getSortedRooms(rooms, mode) },
                        onSaveCustomOrder = { floor, order -> viewModel.saveCustomOrderForFloor(floor, order) },
                        onResetCustomOrder = { floor -> viewModel.resetCustomOrderForFloor(floor) },
                        customOrderVersion = customOrderVersion,
                        cleanlinessStartedAt = cleanlinessStartedAt,
                        cleanlinessScoresCount = cleanlinessScores.size,
                        cleanlinessSessionError = cleanlinessSessionError,
                        onStartCleanlinessSession = { viewModel.startCleanlinessSession() },
                        onFinishCleanlinessSession = { viewModel.finishCleanlinessSession() },
                        onCancelCleanlinessSession = { viewModel.cancelCleanlinessSession() },
                        onSaveRoomCleanliness = { room, status, comment ->
                            val score = when (status) {
                                "Чисто" -> 0
                                "Немного грязно" -> 1
                                "Умеренно грязно" -> 2
                                "Грязно" -> 3
                                else -> 4
                            }
                            viewModel.recordCleanlinessRoomScore(room.roomNumber, score, comment)
                        },
                        onResetRoomCleanliness = { room ->
                            viewModel.resetRoomCleanliness(room)
                        },
                        onSaveKitchenDuty = { floor, side, dutyRoom, status, comment ->
                            viewModel.saveKitchenDuty(floor, side, dutyRoom, status, comment)
                        },
                        onDeleteKitchenDuty = { floor, side ->
                            viewModel.deleteKitchenDuty(floor, side)
                        }
                    )

                    3 -> SilenceScreen(
                        selectedDate = selectedDate,
                        incidents = silenceIncidents,
                        currentRole = currentRole,
                        assignedFloor = assignedFloor,
                        canEditFloor = { floor -> viewModel.canEditFloor(floor) },
                        canDeleteIncident = { inc -> viewModel.canDeleteSilenceIncident(inc) },
                        quietStartedAt = quietStartedAt,
                        quietIncidentsCount = quietIncidents.size,
                        quietSessionError = quietSessionError,
                        onStartQuietSession = { viewModel.startQuietSession() },
                        onFinishQuietSession = { viewModel.finishQuietSession() },
                        onCancelQuietSession = { viewModel.cancelQuietSession() },
                        onAddIncident = { roomNum, vType, comment ->
                            viewModel.addQuietRoomViolation(roomNum, comment)
                        },
                        onDeleteIncident = { inc ->
                            viewModel.removeQuietRoomViolation(inc.roomNumber)
                            viewModel.deleteSilenceIncident(inc)
                        }
                    )
                }
            }
        }
    }

    // Окно профиля сотрудника и кнопка «Выйти из аккаунта»
    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { showProfileDialog = false },
            title = { Text("Профиль сотрудника") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F5F8)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("ФИО: $inspectorName", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("Логин в системе: $currentLogin", fontSize = 13.sp, color = Color.DarkGray)
                            Text("Должность: ${currentRole.title}", fontSize = 13.sp)
                            Text("Рабочий этаж: $assignedFloor-й этаж", fontSize = 13.sp)
                            Text(
                                text = "Комната проживания: " + (livingRoomNumber?.let { "комн. $it" } ?: "не указана"),
                                fontSize = 13.sp,
                                color = if (livingRoomNumber != null) MaterialTheme.colorScheme.primary else Color.Gray,
                                fontWeight = if (livingRoomNumber != null) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Версия приложения: 1.5 (сборка 5)",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Кнопка «На вахте: Связь и синхронизация»
                    Button(
                        onClick = {
                            showProfileDialog = false
                            showWatchmanDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (pendingCount > 0) Color(0xFFE65100) else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Cable, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (pendingCount > 0) "На вахте: Отправить данные ($pendingCount)" else "На вахте: Проверить связь")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Кнопка «Итоги 15-дневной смены»
                    Button(
                        onClick = {
                            showProfileDialog = false
                            showShiftSummaryDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Итоги смены (15 дней)")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Кнопка «Отправить отчёт»
                    Button(
                        onClick = {
                            showProfileDialog = false
                            showReportTypeDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Отправить отчёт за день")
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            showProfileDialog = false
                            viewModel.logout()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Выйти из аккаунта")
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showProfileDialog = false }) {
                    Text("Закрыть")
                }
            }
        )
    }

    // Диалог «На вахте: Связь и синхронизация»
    WatchmanSyncDialog(
        isOpen = showWatchmanDialog || requiresReAuth,
        onDismiss = {
            showWatchmanDialog = false
            viewModel.dismissReAuthDialog()
            viewModel.clearSyncMessages()
        },
        profile = leaderProfile,
        serverUrl = serverUrl,
        onUpdateServerUrl = { viewModel.updateServerUrl(it) },
        pendingCount = pendingCount,
        inspectionsHistory = allInspections,
        isPingLoading = isPingLoading,
        lastPingResult = lastPingResult,
        pingErrorMessage = pingErrorMessage,
        onPingClick = { viewModel.pingLeaderServer() },
        isSyncing = isSyncing,
        lastSyncSuccessMessage = lastSyncSuccessMessage,
        syncErrorMessage = syncErrorMessage,
        onSyncClick = { viewModel.syncLeaderData() },
        requiresReAuth = requiresReAuth,
        onDismissReAuth = { viewModel.dismissReAuthDialog() },
        onReAuthSubmit = { login, pass -> viewModel.login(login, pass) }
    )

    // Шаг 1: Диалог выбора типа отчета (Численность, Чистота, Тишина, Сводный)
    if (showReportTypeDialog) {
        AlertDialog(
            onDismissRequest = { showReportTypeDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Assessment,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Какой отчёт отправить?", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Выберите вид отчёта за $selectedDate ($selectedFloor этаж):",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    val types = listOf(
                        Triple(
                            ReportService.ReportType.HEADCOUNT,
                            "1. Численность",
                            "Ночной обход, норма мест, фактически ночующие и отсутствующие"
                        ),
                        Triple(
                            ReportService.ReportType.CLEANLINESS,
                            "2. Чистота",
                            "Санитарное состояние всех комнат (включая чистые), дежурства по кухне и штрафы"
                        ),
                        Triple(
                            ReportService.ReportType.SILENCE,
                            "3. Тишина",
                            "Соблюдение режима тишины, журнал инцидентов и штрафные баллы"
                        ),
                        Triple(
                            ReportService.ReportType.ALL,
                            "Полный сводный отчёт",
                            "Все 3 раздела (Численность + Чистота + Тишина) в одном документе"
                        )
                    )

                    types.forEach { (type, title, desc) ->
                        val itemIcon = when (type) {
                            ReportService.ReportType.HEADCOUNT -> Icons.Default.People
                            ReportService.ReportType.CLEANLINESS -> Icons.Default.CleaningServices
                            ReportService.ReportType.SILENCE -> Icons.Default.Nightlight
                            ReportService.ReportType.ALL -> Icons.Default.Assessment
                        }
                        val itemColor = when (type) {
                            ReportService.ReportType.HEADCOUNT -> Color(0xFF1565C0)
                            ReportService.ReportType.CLEANLINESS -> Color(0xFF2E7D32)
                            ReportService.ReportType.SILENCE -> Color(0xFF7B1FA2)
                            ReportService.ReportType.ALL -> MaterialTheme.colorScheme.primary
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedReportType = type
                                    showReportTypeDialog = false
                                    showSendReportDialog = true
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
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .background(itemColor.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        itemIcon,
                                        contentDescription = null,
                                        tint = itemColor,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReportTypeDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Шаг 2: Диалог выбора получателя и отправки отчета
    if (showSendReportDialog) {
        AlertDialog(
            onDismissRequest = { showSendReportDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Кому отправить отчёт?")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Отчёт «${selectedReportType.title}» за $selectedDate ($selectedFloor этаж) будет автоматически сформирован и открыт в почтовом приложении:",
                        fontSize = 13.sp,
                        color = Color.DarkGray
                    )

                    ReportService.ReportRecipient.values().forEach { recipient ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showSendReportDialog = false
                                    try {
                                        val excelFile = ReportService.generateExcelReport(
                                            context = context,
                                            reportType = selectedReportType,
                                            date = selectedDate,
                                            floor = selectedFloor,
                                            inspectorName = inspectorName,
                                            inspectorRole = currentRole.title,
                                            recipient = recipient,
                                            allRooms = allRooms,
                                            headcounts = headcountRecords,
                                            cleanliness = cleanlinessRecords,
                                            allCleanliness = allCleanlinessRecords,
                                            kitchenDuty = kitchenDutyRecords,
                                            silenceIncidents = silenceIncidents
                                        )

                                        val reportBody = ReportService.generateReportText(
                                            reportType = selectedReportType,
                                            date = selectedDate,
                                            floor = selectedFloor,
                                            inspectorName = inspectorName,
                                            inspectorRole = currentRole.title,
                                            recipient = recipient,
                                            allRooms = allRooms,
                                            headcounts = headcountRecords,
                                            cleanliness = cleanlinessRecords,
                                            allCleanliness = allCleanlinessRecords,
                                            kitchenDuty = kitchenDutyRecords,
                                            silenceIncidents = silenceIncidents
                                        )

                                        val subject = "Отчёт: ${selectedReportType.title} — $selectedFloor этаж ($selectedDate) — ${recipient.title}"
                                        val fileUri = FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.fileprovider",
                                            excelFile
                                        )

                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.email))
                                            putExtra(Intent.EXTRA_SUBJECT, subject)
                                            putExtra(Intent.EXTRA_TEXT, reportBody)
                                            putExtra(Intent.EXTRA_STREAM, fileUri)
                                            clipData = ClipData.newRawUri("report", fileUri)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }

                                        context.startActivity(
                                            Intent.createChooser(sendIntent, "Отправить отчёт через...")
                                        )
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Ошибка при формировании отчёта: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                                    }
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
                                Icon(
                                    itemIcon,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = recipient.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = recipient.subtitle,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSendReportDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }

    // Диалог 15-дневной смены (Антирейтинг и образцовые комнаты)
    if (showShiftSummaryDialog) {
        ShiftSummaryDialog(
            currentRole = currentRole,
            assignedFloor = assignedFloor,
            allRooms = allRooms,
            allCleanliness = allCleanlinessRecords,
            allSilence = silenceIncidents,
            onDismiss = { showShiftSummaryDialog = false },
            onSendExcelReport = { summary, recipient ->
                showShiftSummaryDialog = false
                try {
                    val excelFile = ReportService.generateShift15DaysExcelReport(
                        context = context,
                        summary = summary,
                        inspectorName = inspectorName,
                        inspectorRole = currentRole.title,
                        recipient = recipient,
                        allCleanliness = allCleanlinessRecords,
                        allSilence = silenceIncidents
                    )

                    val reportBody = ReportService.generateShift15DaysReportText(
                        summary = summary,
                        inspectorName = inspectorName,
                        inspectorRole = currentRole.title,
                        recipient = recipient
                    )

                    val floorTitle = if (summary.filterFloor != null) "${summary.filterFloor} этаж" else "Все этажи"
                    val subject = "Итоги 15-дневной смены ($floorTitle, ${summary.startDate} — ${summary.endDate}) — ${recipient.title}"
                    val fileUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        excelFile
                    )

                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient.email))
                        putExtra(Intent.EXTRA_SUBJECT, subject)
                        putExtra(Intent.EXTRA_TEXT, reportBody)
                        putExtra(Intent.EXTRA_STREAM, fileUri)
                        clipData = ClipData.newRawUri("shift_report", fileUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }

                    context.startActivity(
                        Intent.createChooser(sendIntent, "Отправить отчёт за смену через...")
                    )
                } catch (e: Exception) {
                    Toast.makeText(context, "Ошибка при формировании отчёта за смену: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
            }
        )
    }
}