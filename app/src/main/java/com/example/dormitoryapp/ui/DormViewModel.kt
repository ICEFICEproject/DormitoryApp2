package com.example.dormitoryapp.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.dormitoryapp.data.AuthResult
import com.example.dormitoryapp.data.CleanlinessRecord
import com.example.dormitoryapp.data.DormDatabase
import com.example.dormitoryapp.data.DormRepository
import com.example.dormitoryapp.data.DormRepositoryImpl
import com.example.dormitoryapp.data.DormRoom
import com.example.dormitoryapp.data.FloorDutyScheduleRecord
import com.example.dormitoryapp.data.HeadcountRecord
import com.example.dormitoryapp.data.KitchenDutyRecord
import com.example.dormitoryapp.data.RemoteStaffUser
import com.example.dormitoryapp.data.RoomSortMode
import com.example.dormitoryapp.data.SilenceIncidentRecord
import com.example.dormitoryapp.data.StaffRole
import com.example.dormitoryapp.data.SupabaseAuthService
import com.example.dormitoryapp.data.SupabaseSyncService
import com.example.dormitoryapp.data.SyncQueueManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

import com.example.dormitoryapp.data.LeaderApiService
import com.example.dormitoryapp.data.LeaderAuthResult
import com.example.dormitoryapp.data.LeaderPingResult
import com.example.dormitoryapp.data.LeaderProfile
import com.example.dormitoryapp.data.LeaderSyncResult
import com.example.dormitoryapp.data.InspectionEntity
import com.example.dormitoryapp.data.InspectionRoomItem
import java.util.UUID

/**
 * ViewModel — связывает экраны с локальной базой Room и сервером ИС «Цифровое общежитие».
 * Поддерживает оффлайн-проверки комнат, оффлайн-очередь с UUID-идентификаторами
 * и синхронизацию по проводу/Wi-Fi на вахте (POST /auth, GET /ping, POST /inspections/sync).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DormViewModel(application: Application) : AndroidViewModel(application) {

    private val database = DormDatabase.getDatabase(application)
    private val dao = database.dormDao()
    private val repository: DormRepository = DormRepositoryImpl(application, dao)
    private val prefs = application.getSharedPreferences("dorm_staff_prefs", Context.MODE_PRIVATE)

    // Профиль старосты из ИС «Цифровое общежитие»
    private val _leaderProfile = MutableStateFlow<LeaderProfile?>(LeaderApiService.getCachedProfile(application))
    val leaderProfile: StateFlow<LeaderProfile?> = _leaderProfile.asStateFlow()

    // Базовый адрес сервера (по умолчанию http://172.17.0.21:8000/api/leader/v1)
    private val _serverUrl = MutableStateFlow(LeaderApiService.getBaseUrl(application))
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    fun updateServerUrl(newUrl: String) {
        val trimmed = newUrl.trim()
        LeaderApiService.setBaseUrl(getApplication(), trimmed)
        _serverUrl.value = LeaderApiService.getBaseUrl(getApplication())
    }

    // Признак авторизации (активен ли токен устройства)
    private val _isLoggedIn = MutableStateFlow(
        LeaderApiService.getDeviceToken(application) != null && _leaderProfile.value != null
    )
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    // Состояние процесса входа в сеть
    private val _isAuthLoading = MutableStateFlow(false)
    val isAuthLoading: StateFlow<Boolean> = _isAuthLoading.asStateFlow()

    private val _authErrorMessage = MutableStateFlow<String?>(null)
    val authErrorMessage: StateFlow<String?> = _authErrorMessage.asStateFlow()

    // Сообщение о требовании сменить временный пароль (HTTP 409 password_change_required)
    private val _passwordChangeMessage = MutableStateFlow<String?>(null)
    val passwordChangeMessage: StateFlow<String?> = _passwordChangeMessage.asStateFlow()

    // Признак необходимости повторной авторизации при истечении токена (HTTP 401)
    private val _requiresReAuth = MutableStateFlow(false)
    val requiresReAuth: StateFlow<Boolean> = _requiresReAuth.asStateFlow()

    // Индикаторы процесса проверки связи (GET /ping)
    private val _isPingLoading = MutableStateFlow(false)
    val isPingLoading: StateFlow<Boolean> = _isPingLoading.asStateFlow()

    private val _lastPingResult = MutableStateFlow<String?>(null)
    val lastPingResult: StateFlow<String?> = _lastPingResult.asStateFlow()

    private val _pingErrorMessage = MutableStateFlow<String?>(null)
    val pingErrorMessage: StateFlow<String?> = _pingErrorMessage.asStateFlow()

    // Индикатор процесса синхронизации проверок с сервером (POST /inspections/sync)
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncErrorMessage = MutableStateFlow<String?>(null)
    val syncErrorMessage: StateFlow<String?> = _syncErrorMessage.asStateFlow()

    private val _lastSyncSuccessMessage = MutableStateFlow<String?>(null)
    val lastSyncSuccessMessage: StateFlow<String?> = _lastSyncSuccessMessage.asStateFlow()

    // Очередь и история проверок
    val pendingInspectionsCount: StateFlow<Int> = repository.pendingInspectionsCountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val allInspections: StateFlow<List<InspectionEntity>> = repository.allInspectionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- СЕССИИ ПРОВЕРОК (ЧИСТОТА И ТИШИНА) ---
    // Время старта обхода чистоты (ISO 8601)
    private val _cleanlinessStartedAt = MutableStateFlow<String?>(null)
    val cleanlinessStartedAt: StateFlow<String?> = _cleanlinessStartedAt.asStateFlow()

    // Текущие проставленные оценки в активной сессии чистоты (roomNumber -> (score, comment))
    private val _cleanlinessSessionScores = MutableStateFlow<Map<Int, Pair<Int, String>>>(emptyMap())
    val cleanlinessSessionScores: StateFlow<Map<Int, Pair<Int, String>>> = _cleanlinessSessionScores.asStateFlow()

    private val _cleanlinessSessionError = MutableStateFlow<String?>(null)
    val cleanlinessSessionError: StateFlow<String?> = _cleanlinessSessionError.asStateFlow()

    // Время старта обхода тишины (ISO 8601)
    private val _quietStartedAt = MutableStateFlow<String?>(null)
    val quietStartedAt: StateFlow<String?> = _quietStartedAt.asStateFlow()

    // Зафиксированные нарушения тишины в активной сессии (roomNumber -> comment)
    private val _quietSessionIncidents = MutableStateFlow<List<Pair<Int, String>>>(emptyList())
    val quietSessionIncidents: StateFlow<List<Pair<Int, String>>> = _quietSessionIncidents.asStateFlow()

    private val _quietSessionError = MutableStateFlow<String?>(null)
    val quietSessionError: StateFlow<String?> = _quietSessionError.asStateFlow()

    // Логин авторизованного пользователя
    private val _currentLogin = MutableStateFlow(
        prefs.getString("current_login", "") ?: ""
    )
    val currentLogin: StateFlow<String> = _currentLogin.asStateFlow()

    // Должность сотрудника
    private val _currentRole = MutableStateFlow(
        runCatching {
            StaffRole.valueOf(prefs.getString("staff_role", StaffRole.FLOOR_HEAD.name)!!)
        }.getOrDefault(StaffRole.FLOOR_HEAD)
    )
    val currentRole: StateFlow<StaffRole> = _currentRole.asStateFlow()

    // ФИО проверяющего
    private val _inspectorName = MutableStateFlow(
        _leaderProfile.value?.user?.fullName ?: (prefs.getString("inspector_name", "") ?: "")
    )
    val inspectorName: StateFlow<String> = _inspectorName.asStateFlow()

    // Комната проживания сотрудника (если указана в базе)
    private val _livingRoomNumber = MutableStateFlow<Int?>(
        prefs.getInt("living_room_number", -1).let { if (it == -1) null else it }
    )
    val livingRoomNumber: StateFlow<Int?> = _livingRoomNumber.asStateFlow()

    // Закрепленный за сотрудником этаж (из профиля или настроек)
    private val _assignedFloor = MutableStateFlow(
        _leaderProfile.value?.floor ?: prefs.getInt("assigned_floor", 3).coerceIn(2, 5)
    )
    val assignedFloor: StateFlow<Int> = _assignedFloor.asStateFlow()

    // Текущий просматриваемый этаж на экранах
    private val _selectedFloor = MutableStateFlow(_assignedFloor.value)
    val selectedFloor: StateFlow<Int> = _selectedFloor.asStateFlow()

    // Режим порядка комнат (Обычный 1-35, Шахматный зигзаг, Свой кастомный)
    private val _roomSortMode = MutableStateFlow(
        runCatching {
            RoomSortMode.valueOf(prefs.getString("room_sort_mode", RoomSortMode.DEFAULT.name)!!)
        }.getOrDefault(RoomSortMode.DEFAULT)
    )
    val roomSortMode: StateFlow<RoomSortMode> = _roomSortMode.asStateFlow()

    // Версия кастомного порядка (для мгновенной перерисовки Compose списков)
    private val _customOrderVersion = MutableStateFlow(0)
    val customOrderVersion: StateFlow<Int> = _customOrderVersion.asStateFlow()

    fun setRoomSortMode(mode: RoomSortMode) {
        _roomSortMode.value = mode
        prefs.edit().putString("room_sort_mode", mode.name).apply()
    }

    fun getCustomOrderForFloor(floor: Int): List<Int>? {
        val str = prefs.getString("custom_order_floor_$floor", null) ?: return null
        if (str.isBlank()) return null
        return str.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    fun saveCustomOrderForFloor(floor: Int, roomNumbers: List<Int>) {
        val str = roomNumbers.joinToString(",")
        prefs.edit().putString("custom_order_floor_$floor", str).apply()
        _customOrderVersion.value++
    }

    fun resetCustomOrderForFloor(floor: Int) {
        prefs.edit().remove("custom_order_floor_$floor").apply()
        _customOrderVersion.value++
    }

    fun getSortedRooms(rooms: List<DormRoom>, mode: RoomSortMode): List<DormRoom> {
        if (rooms.isEmpty()) return rooms
        return when (mode) {
            RoomSortMode.DEFAULT -> rooms.sortedBy { it.roomNumber }
            RoomSortMode.CHESS -> {
                val sorted = rooms.sortedBy { it.roomNumber }
                val result = mutableListOf<DormRoom>()
                var left = 0
                var right = sorted.size - 1
                while (left <= right) {
                    if (left == right) {
                        result.add(sorted[left])
                    } else {
                        result.add(sorted[left])
                        result.add(sorted[right])
                    }
                    left++
                    right--
                }
                result
            }
            RoomSortMode.CUSTOM -> {
                val floor = rooms.first().floor
                val customOrder = getCustomOrderForFloor(floor)
                if (customOrder.isNullOrEmpty()) {
                    rooms.sortedBy { it.roomNumber }
                } else {
                    val roomMap = rooms.associateBy { it.roomNumber }
                    val ordered = customOrder.mapNotNull { roomMap[it] }.toMutableList()
                    rooms.forEach { if (it !in ordered) ordered.add(it) }
                    ordered
                }
            }
        }
    }

    // Выбранная дата (по умолчанию сегодня)
    private val _selectedDate = MutableStateFlow(LocalDate.now().toString())
    val selectedDate: StateFlow<String> = _selectedDate.asStateFlow()

    // Все 140 комнат общежития
    val allRooms: StateFlow<List<DormRoom>> = repository.allRoomsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Записи вечернего обхода численности за выбранную дату
    val headcountRecords: StateFlow<List<HeadcountRecord>> = _selectedDate
        .flatMapLatest { date -> repository.getHeadcountByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Записи проверки чистоты комнат за выбранную дату
    val cleanlinessRecords: StateFlow<List<CleanlinessRecord>> = _selectedDate
        .flatMapLatest { date -> repository.getCleanlinessByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Все записи проверки чистоты комнат за всё время (для расчёта накопительных штрафных баллов)
    val allCleanlinessRecords: StateFlow<List<CleanlinessRecord>> = repository.allCleanlinessRecordsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Записи дежурства по кухне (Северная и Южная) за выбранную дату
    val kitchenDutyRecords: StateFlow<List<KitchenDutyRecord>> = _selectedDate
        .flatMapLatest { date -> repository.getKitchenDutiesByDateFlow(date) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Все записи нарушений тишины
    val silenceIncidents: StateFlow<List<SilenceIncidentRecord>> = repository.allSilenceIncidentsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // График дежурств сотрудников по этажам
    val allFloorDutySchedules: StateFlow<List<FloorDutyScheduleRecord>> = repository.allFloorDutySchedulesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Список всех зарегистрированных сотрудников из Supabase
    private val _staffUsers = MutableStateFlow<List<RemoteStaffUser>>(emptyList())
    val staffUsers: StateFlow<List<RemoteStaffUser>> = _staffUsers.asStateFlow()

    init {
        viewModelScope.launch {
            DormDatabase.ensureInitialRoomsPopulated(dao)
            // Если профиль уже сохранён в памяти, обновляем данные этажа
            _leaderProfile.value?.let { profile ->
                syncProfileRoomsToDatabase(profile)
            }
        }
    }

    private suspend fun syncProfileRoomsToDatabase(profile: LeaderProfile) {
        val floor = profile.floor
        val dbRooms = mutableListOf<DormRoom>()
        for (r in profile.rooms) {
            val num = r.roomNumberInt
            if (num > 0) {
                val isFirstWing = (num % 100) <= 15
                val wingName = if (isFirstWing) "Северная (00–15)" else "Южная (16–34)"
                val cap = when {
                    (num % 100) == 34 -> 8
                    isFirstWing -> 4
                    else -> 6
                }
                dbRooms.add(
                    DormRoom(
                        roomNumber = num,
                        floor = floor,
                        wing = wingName,
                        capacity = cap
                    )
                )
            }
        }
        if (dbRooms.isNotEmpty()) {
            dao.insertRooms(dbRooms)
        }
    }

    // --- 0. АВТОРИЗАЦИЯ И ПРИВЯЗКА (POST /auth) ---

    fun login(loginInput: String, passwordInput: String, newPasswordInput: String? = null) {
        viewModelScope.launch {
            _isAuthLoading.value = true
            _authErrorMessage.value = null
            _passwordChangeMessage.value = null

            val result = repository.leaderLogin(loginInput, passwordInput, newPasswordInput)

            when (result) {
                is LeaderAuthResult.Success -> {
                    val profile = result.profile
                    _leaderProfile.value = profile
                    _inspectorName.value = profile.user.fullName
                    _assignedFloor.value = profile.floor
                    _selectedFloor.value = profile.floor
                    _currentLogin.value = loginInput.trim()

                    val role = when (profile.position.lowercase()) {
                        "commander" -> StaffRole.FLOOR_COMMANDER
                        "elder" -> StaffRole.FLOOR_HEAD
                        "initiative" -> StaffRole.INITIATIVE_GROUP
                        else -> StaffRole.FLOOR_HEAD
                    }
                    _currentRole.value = role
                    _isLoggedIn.value = true
                    _authErrorMessage.value = null
                    _passwordChangeMessage.value = null

                    prefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putString("current_login", loginInput.trim())
                        .putString("inspector_name", profile.user.fullName)
                        .putString("staff_role", role.name)
                        .putInt("assigned_floor", profile.floor)
                        .apply()

                    syncProfileRoomsToDatabase(profile)

                    // Если ранее требовался повторный вход на вахте при отправке очереди,
                    // сразу же отправляем данные в тот же заход!
                    if (_requiresReAuth.value) {
                        _requiresReAuth.value = false
                        syncLeaderData()
                    }
                }

                is LeaderAuthResult.PasswordChangeRequired -> {
                    _passwordChangeMessage.value = result.message
                }

                is LeaderAuthResult.Error -> {
                    _authErrorMessage.value = result.message
                }
            }

            _isAuthLoading.value = false
        }
    }

    fun dismissPasswordChangePrompt() {
        _passwordChangeMessage.value = null
    }

    /**
     * Демо-вход для тестирования приложения без физического сервера общежития.
     * Загружает тестовый профиль старосты 3 этажа со всеми комнатами и правилами ТЗ.
     */
    fun loginDemo(floor: Int = 3) {
        val demoRooms = (0..34).map { num ->
            val roomNumStr = "${floor * 100 + num}"
            com.example.dormitoryapp.data.LeaderRoom(
                id = (floor * 100 + num).toLong(),
                number = roomNumStr,
                active = num != 13 // 13-я комната условно на ремонте для проверки логики
            )
        }
        val demoProfile = com.example.dormitoryapp.data.LeaderProfile(
            user = com.example.dormitoryapp.data.LeaderUser(17, "Иванов", "Пётр", "Сергеевич"),
            position = "elder",
            positionLabel = "Староста",
            floor = floor,
            rooms = demoRooms,
            timezone = "Europe/Moscow",
            serverTime = LeaderApiService.currentIsoTime(),
            rules = com.example.dormitoryapp.data.LeaderRules(
                quietStart = "23:00",
                quietLastStart = "23:59",
                quietFinishBy = "00:30",
                cleanlinessMaxHours = 3,
                cleanlinessScore = listOf(0, 5),
                commentMax = 500
            )
        )

        LeaderApiService.saveToken(getApplication(), "demo-token-90-days", "2026-12-31T23:59:59+03:00")
        LeaderApiService.saveCachedProfile(getApplication(), demoProfile)

        _leaderProfile.value = demoProfile
        _inspectorName.value = demoProfile.user.fullName
        _assignedFloor.value = floor
        _selectedFloor.value = floor
        _currentLogin.value = "ivanov"
        _currentRole.value = StaffRole.FLOOR_HEAD
        _isLoggedIn.value = true
        _authErrorMessage.value = null
        _passwordChangeMessage.value = null

        prefs.edit()
            .putBoolean("is_logged_in", true)
            .putString("current_login", "ivanov")
            .putString("inspector_name", demoProfile.user.fullName)
            .putString("staff_role", StaffRole.FLOOR_HEAD.name)
            .putInt("assigned_floor", floor)
            .apply()

        viewModelScope.launch {
            syncProfileRoomsToDatabase(demoProfile)
        }
    }

    fun logout() {
        LeaderApiService.clearToken(getApplication())
        prefs.edit()
            .putBoolean("is_logged_in", false)
            .remove("current_login")
            .remove("inspector_name")
            .remove("staff_role")
            .remove("assigned_floor")
            .remove("living_room_number")
            .apply()
        _isLoggedIn.value = false
        _currentLogin.value = ""
        _inspectorName.value = ""
        _currentRole.value = StaffRole.FLOOR_HEAD
        _assignedFloor.value = 3
        _livingRoomNumber.value = null
        _selectedFloor.value = 3
        _authErrorMessage.value = null
        _leaderProfile.value = null
        // Важно: неотправленные проверки НЕ удаляем, они остаются в Room!
    }

    // --- 1. ПРОВЕРКА СОЕДИНЕНИЯ НА ВАХТЕ (GET /ping) ---

    fun pingLeaderServer() {
        viewModelScope.launch {
            _isPingLoading.value = true
            _pingErrorMessage.value = null
            _lastPingResult.value = null

            val result = repository.pingLeaderServer()
            when (result) {
                is LeaderPingResult.Success -> {
                    val profile = result.profile
                    _leaderProfile.value = profile
                    _assignedFloor.value = profile.floor
                    _inspectorName.value = profile.user.fullName
                    syncProfileRoomsToDatabase(profile)
                    _lastPingResult.value = "Связь есть. Время сервера: ${result.serverTime}"
                    _pingErrorMessage.value = null
                    _requiresReAuth.value = false
                }
                is LeaderPingResult.Unauthorized -> {
                    _requiresReAuth.value = true
                    _pingErrorMessage.value = result.message
                }
                is LeaderPingResult.Error -> {
                    _pingErrorMessage.value = result.message
                }
            }
            _isPingLoading.value = false
        }
    }

    // --- 2. ОТПРАВКА ДАННЫХ ПРОВЕРОК НА ВАХТЕ (POST /inspections/sync) ---

    fun syncLeaderData() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncErrorMessage.value = null
            _lastSyncSuccessMessage.value = null

            val result = repository.syncLeaderInspections()
            when (result) {
                is LeaderSyncResult.Success -> {
                    val total = result.results.size
                    if (total == 0) {
                        _lastSyncSuccessMessage.value = "Очередь проверок пуста, всё синхронизировано."
                    } else {
                        val accepted = result.results.count { it.status == "accepted" }
                        val dup = result.results.count { it.status == "duplicate" }
                        val rejected = result.results.count { it.status == "rejected" }
                        _lastSyncSuccessMessage.value = "Отправлено проверок: $total (Принято: $accepted, Дубли: $dup, Отклонено: $rejected)"
                    }
                    _requiresReAuth.value = false
                }
                is LeaderSyncResult.Unauthorized -> {
                    _requiresReAuth.value = true
                    _syncErrorMessage.value = result.message
                }
                is LeaderSyncResult.Error -> {
                    _syncErrorMessage.value = result.message
                }
            }
            _isSyncing.value = false
        }
    }

    fun dismissReAuthDialog() {
        _requiresReAuth.value = false
    }

    fun clearSyncMessages() {
        _syncErrorMessage.value = null
        _lastSyncSuccessMessage.value = null
        _pingErrorMessage.value = null
        _lastPingResult.value = null
    }

    // --- 3. СЕССИЯ ПРОВЕРКИ ЧИСТОТЫ ---

    fun startCleanlinessSession() {
        _cleanlinessStartedAt.value = LeaderApiService.currentIsoTime()
        _cleanlinessSessionScores.value = emptyMap()
        _cleanlinessSessionError.value = null
    }

    fun recordCleanlinessRoomScore(roomNumber: Int, score: Int, comment: String) {
        val trimmedComment = comment.take(500)
        val current = _cleanlinessSessionScores.value.toMutableMap()
        current[roomNumber] = (score to trimmedComment)
        _cleanlinessSessionScores.value = current

        // Также сохраняем в локальную таблицу CleanlinessRecord для обычного интерфейса и отчетов
        val room = allRooms.value.find { it.roomNumber == roomNumber }
        val statusStr = when (score) {
            0 -> "Чисто"
            1 -> "Немного грязно"
            2 -> "Умеренно грязно"
            3 -> "Грязно"
            else -> "Очень грязно"
        }
        val record = CleanlinessRecord(
            date = _selectedDate.value,
            roomNumber = roomNumber,
            floor = room?.floor ?: (roomNumber / 100),
            status = statusStr,
            comment = trimmedComment,
            inspectorName = currentInspectorLabel()
        )
        viewModelScope.launch {
            repository.saveCleanlinessRecord(record)
        }
    }

    fun finishCleanlinessSession(): Boolean {
        val started = _cleanlinessStartedAt.value
        if (started == null) {
            _cleanlinessSessionError.value = "Сессия проверки чистоты не была начата."
            return false
        }
        val scores = _cleanlinessSessionScores.value
        if (scores.isEmpty()) {
            _cleanlinessSessionError.value = "Оцените хотя бы одну комнату перед завершением обхода чистоты."
            return false
        }

        val profile = _leaderProfile.value
        val floor = profile?.floor ?: _assignedFloor.value
        val profileRooms = profile?.rooms.orEmpty()

        val roomItems = mutableListOf<InspectionRoomItem>()
        for ((roomNum, pair) in scores) {
            val (score, comment) = pair
            val matchedRoom = profileRooms.find { it.number == roomNum.toString() }
            val roomId = matchedRoom?.id ?: roomNum.toLong()
            roomItems.add(
                InspectionRoomItem(
                    roomId = roomId,
                    score = score,
                    comment = comment
                )
            )
        }

        val finished = LeaderApiService.currentIsoTime()
        val eventId = UUID.randomUUID().toString()

        val inspection = InspectionEntity(
            eventId = eventId,
            type = "cleanliness",
            floor = floor,
            startedAt = started,
            finishedAt = finished,
            roomsJson = InspectionEntity.roomsToJson(roomItems),
            syncStatus = InspectionEntity.STATUS_PENDING
        )

        viewModelScope.launch {
            repository.saveInspection(inspection)
        }

        _cleanlinessStartedAt.value = null
        _cleanlinessSessionScores.value = emptyMap()
        _cleanlinessSessionError.value = null
        return true
    }

    fun cancelCleanlinessSession() {
        _cleanlinessStartedAt.value = null
        _cleanlinessSessionScores.value = emptyMap()
        _cleanlinessSessionError.value = null
    }

    // --- 4. СЕССИЯ ПРОВЕРКИ ТИШИНЫ ---

    fun startQuietSession() {
        val started = LeaderApiService.currentIsoTime()
        _quietStartedAt.value = started
        _quietSessionIncidents.value = emptyList()
        _quietSessionError.value = null
    }

    fun addQuietRoomViolation(roomNumber: Int, comment: String) {
        val trimmed = comment.take(500)
        val current = _quietSessionIncidents.value.toMutableList()
        current.removeAll { it.first == roomNumber }
        current.add(roomNumber to trimmed)
        _quietSessionIncidents.value = current

        // Также добавляем в локальную таблицу SilenceIncidentRecord для интерфейса
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val record = SilenceIncidentRecord(
            date = _selectedDate.value,
            time = currentTime,
            roomNumber = roomNumber,
            floor = roomNumber / 100,
            violationType = "Шум после отбоя",
            comment = trimmed,
            inspectorName = currentInspectorLabel()
        )
        viewModelScope.launch {
            repository.addSilenceIncident(record)
        }
    }

    fun removeQuietRoomViolation(roomNumber: Int) {
        val current = _quietSessionIncidents.value.toMutableList()
        current.removeAll { it.first == roomNumber }
        _quietSessionIncidents.value = current
    }

    fun finishQuietSession(): Boolean {
        val started = _quietStartedAt.value
        if (started == null) {
            _quietSessionError.value = "Сессия проверки тишины не была начата."
            return false
        }

        val profile = _leaderProfile.value
        val floor = profile?.floor ?: _assignedFloor.value
        val profileRooms = profile?.rooms.orEmpty()

        val roomItems = mutableListOf<InspectionRoomItem>()
        for ((roomNum, comment) in _quietSessionIncidents.value) {
            val matchedRoom = profileRooms.find { it.number == roomNum.toString() }
            val roomId = matchedRoom?.id ?: roomNum.toLong()
            roomItems.add(
                InspectionRoomItem(
                    roomId = roomId,
                    score = 1, // по ТЗ: для тишины только шумные комнаты, score = 1
                    comment = comment
                )
            )
        }

        val finished = LeaderApiService.currentIsoTime()
        val eventId = UUID.randomUUID().toString()

        val inspection = InspectionEntity(
            eventId = eventId,
            type = "quiet",
            floor = floor,
            startedAt = started,
            finishedAt = finished,
            roomsJson = InspectionEntity.roomsToJson(roomItems), // пустой список = "на этаже тихо"
            syncStatus = InspectionEntity.STATUS_PENDING
        )

        viewModelScope.launch {
            repository.saveInspection(inspection)
        }

        _quietStartedAt.value = null
        _quietSessionIncidents.value = emptyList()
        _quietSessionError.value = null
        return true
    }

    fun cancelQuietSession() {
        _quietStartedAt.value = null
        _quietSessionIncidents.value = emptyList()
        _quietSessionError.value = null
    }

    // Сохранение совместимости со старыми вызовами
    fun triggerCloudSync(silent: Boolean = false) {
        // Оффлайн-архитектура: синхронизация выполняется вручную на вахте (syncLeaderData)
    }

    // --- ПРОВЕРКА ПРАВ НА РЕДАКТИРОВАНИЕ ЭТАЖА И ДАТЫ ---

    fun canEditFloor(floor: Int, date: String = _selectedDate.value): Boolean {
        val today = LocalDate.now()
        val targetDate = runCatching { LocalDate.parse(date) }.getOrDefault(today)

        // 1. В будущее никто не может выставлять отметки
        if (targetDate.isAfter(today)) return false

        // 2. Доступ к этажу: зам. воспитателя ко всем, остальные — только к своему
        val hasFloorAccess = _currentRole.value == StaffRole.DEPUTY_EDUCATOR || floor == _assignedFloor.value
        if (!hasFloorAccess) return false

        // 3. Сегодняшний день могут отмечать все сотрудники на своем этаже
        if (targetDate == today) return true

        // 4. Прошлые дни (вчера и ранее) могут редактировать ТОЛЬКО Командир этажа и Заместитель воспитателя
        return _currentRole.value == StaffRole.FLOOR_COMMANDER || _currentRole.value == StaffRole.DEPUTY_EDUCATOR
    }

    /**
     * Проверка: может ли текущий пользователь изменить/сбросить запись, если она была создана Зам. воспитателя.
     * Если запись создана Заместителем воспитателя за прошлую дату (или вообще зам. воспитателя) —
     * редактировать или удалять её разрешено ТОЛЬКО Заместителю воспитателя!
     */
    fun canModifyPastDeputyRecord(inspectorName: String, date: String = _selectedDate.value): Boolean {
        val today = LocalDate.now()
        val targetDate = runCatching { LocalDate.parse(date) }.getOrDefault(today)
        val isDeputy = _currentRole.value == StaffRole.DEPUTY_EDUCATOR
        val isCreatedByDeputy = inspectorName.contains("Заместитель", ignoreCase = true) ||
                inspectorName.contains("Зам.", ignoreCase = true) ||
                inspectorName.contains("Воспитател", ignoreCase = true)

        if (isCreatedByDeputy && (targetDate.isBefore(today) || !isDeputy)) {
            return isDeputy
        }
        return true
    }

    /**
     * Проверка прав на удаление инцидента тишины:
     * - Проверяет именно дату самого инцидента (а не выбранную в данный момент дату на экране).
     * - Если замечание стоит за прошлые даты (вчера и ранее) — удалить может ТОЛЬКО Заместитель воспитателя!
     * - Если инцидент создан Заместителем воспитателя — удалить его может ТОЛЬКО Заместитель воспитателя!
     * - Для сегодняшних записей: зам. воспитателя может удалять любые, а сотрудники этажа — только своего этажа (если запись создана не зам. воспитателя).
     */
    fun canDeleteSilenceIncident(incident: SilenceIncidentRecord): Boolean {
        val today = LocalDate.now()
        val incidentDate = runCatching { LocalDate.parse(incident.date) }.getOrDefault(today)

        // 1. В будущее никто не может ставить/удалять отметки
        if (incidentDate.isAfter(today)) return false

        val isDeputy = _currentRole.value == StaffRole.DEPUTY_EDUCATOR
        val isMyFloor = incident.floor == _assignedFloor.value

        // 2. Если замечание стоит за прошлые даты (вчера и ранее) — удалить может ТОЛЬКО зам. воспитателя!
        if (incidentDate.isBefore(today)) {
            return isDeputy
        }

        // 3. Для сегодняшней даты: зам. воспитателя имеет доступ ко всем этажам, остальные — только к своему закрепленному
        if (!isDeputy && !isMyFloor) return false

        val isCreatedByDeputy = incident.inspectorName.contains("Заместитель", ignoreCase = true) ||
                incident.inspectorName.contains("Зам.", ignoreCase = true) ||
                incident.inspectorName.contains("Воспитател", ignoreCase = true)

        // 4. Запись сделана Зам. воспитателя — удалить может ТОЛЬКО Зам. воспитателя!
        if (isCreatedByDeputy && !isDeputy) {
            return false
        }

        // 5. Сегодняшняя запись на своем этаже
        return true
    }

    fun selectFloor(floor: Int) {
        if (floor in 2..5) {
            _selectedFloor.value = floor
        }
    }

    fun changeDateByDays(daysDelta: Long) {
        val today = LocalDate.now()
        val current = runCatching { LocalDate.parse(_selectedDate.value) }.getOrDefault(today)
        val target = current.plusDays(daysDelta)
        if (target.isAfter(today)) return // Запрет перехода в будущие даты
        _selectedDate.value = target.toString()
        triggerCloudSync()
    }

    fun resetToToday() {
        _selectedDate.value = LocalDate.now().toString()
        triggerCloudSync()
    }

    private fun currentInspectorLabel(): String {
        return "${_currentRole.value.title} (${_assignedFloor.value} эт.): ${_inspectorName.value}"
    }

    // --- 1. МЕТОДЫ МОДУЛЯ «ЧИСЛЕННОСТЬ» ---

    fun updateRoomCapacity(room: DormRoom, newCapacity: Int) {
        if (!canEditFloor(room.floor)) return
        viewModelScope.launch {
            repository.updateRoomCapacity(room, newCapacity)
        }
    }

    fun saveRoomHeadcount(room: DormRoom, presentCount: Int) {
        if (!canEditFloor(room.floor)) return
        val validCount = presentCount.coerceIn(0, room.capacity)
        val record = HeadcountRecord(
            date = _selectedDate.value,
            roomNumber = room.roomNumber,
            floor = room.floor,
            presentCount = validCount,
            inspectorName = currentInspectorLabel()
        )
        viewModelScope.launch {
            repository.saveHeadcountRecord(record)
        }
    }

    fun fillUncheckedRoomsWithNorm(floor: Int) {
        if (!canEditFloor(floor)) return
        viewModelScope.launch {
            val existingRoomNumbers = headcountRecords.value
                .filter { it.floor == floor }
                .map { it.roomNumber }
                .toSet()
            repository.fillUncheckedRoomsWithNorm(
                floor = floor,
                date = _selectedDate.value,
                inspectorLabel = currentInspectorLabel(),
                allRooms = allRooms.value,
                existingRoomNumbers = existingRoomNumbers
            )
        }
    }

    // --- 2. МЕТОДЫ МОДУЛЯ «ЧИСТОТА И КУХНИ» ---

    fun saveRoomCleanliness(room: DormRoom, status: String, comment: String) {
        if (!canEditFloor(room.floor)) return
        val existing = cleanlinessRecords.value.find { it.roomNumber == room.roomNumber }
        if (existing != null && !canModifyPastDeputyRecord(existing.inspectorName, _selectedDate.value)) {
            return
        }
        val record = CleanlinessRecord(
            date = _selectedDate.value,
            roomNumber = room.roomNumber,
            floor = room.floor,
            status = status,
            comment = comment.trim(),
            inspectorName = currentInspectorLabel()
        )
        viewModelScope.launch {
            repository.saveCleanlinessRecord(record)
        }
    }

    fun resetRoomCleanliness(room: DormRoom) {
        if (!canEditFloor(room.floor)) return
        val existing = cleanlinessRecords.value.find { it.roomNumber == room.roomNumber }
        if (existing != null && !canModifyPastDeputyRecord(existing.inspectorName, _selectedDate.value)) {
            return
        }
        viewModelScope.launch {
            repository.deleteCleanlinessRecord(_selectedDate.value, room.roomNumber)
        }
    }

    fun saveKitchenDuty(
        floor: Int,
        kitchenSide: String,
        dutyRoomNumber: Int,
        status: String,
        comment: String
    ) {
        if (!canEditFloor(floor)) return
        val existing = kitchenDutyRecords.value.find { it.floor == floor && it.kitchenSide == kitchenSide }
        if (existing != null && !canModifyPastDeputyRecord(existing.inspectorName, _selectedDate.value)) {
            return
        }
        val record = KitchenDutyRecord(
            date = _selectedDate.value,
            floor = floor,
            kitchenSide = kitchenSide,
            dutyRoomNumber = dutyRoomNumber,
            status = status,
            comment = comment.trim(),
            inspectorName = currentInspectorLabel()
        )
        viewModelScope.launch {
            repository.saveKitchenDutyRecord(record)
        }
    }

    fun deleteKitchenDuty(
        floor: Int,
        kitchenSide: String
    ) {
        if (!canEditFloor(floor)) return
        val existing = kitchenDutyRecords.value.find { it.floor == floor && it.kitchenSide == kitchenSide }
        if (existing != null && !canModifyPastDeputyRecord(existing.inspectorName, _selectedDate.value)) {
            return
        }
        val date = _selectedDate.value
        viewModelScope.launch {
            repository.deleteKitchenDutyRecord(date, floor, kitchenSide)
        }
    }

    // --- 3. МЕТОДЫ МОДУЛЯ «ТИШИНА» ---

    fun addSilenceIncident(roomNumber: Int, violationType: String, comment: String) {
        val floor = roomNumber / 100
        val numOnFloor = roomNumber % 100
        if (floor !in 2..5 || numOnFloor !in 0..34) return
        if (!canEditFloor(floor)) return
        val currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val record = SilenceIncidentRecord(
            date = _selectedDate.value,
            time = currentTime,
            roomNumber = roomNumber,
            floor = floor,
            violationType = violationType,
            comment = comment.trim(),
            inspectorName = currentInspectorLabel()
        )
        viewModelScope.launch {
            repository.addSilenceIncident(record)
            triggerCloudSync(silent = true)
        }
    }

    fun deleteSilenceIncident(incident: SilenceIncidentRecord) {
        if (!canDeleteSilenceIncident(incident)) return
        viewModelScope.launch {
            repository.deleteSilenceIncident(incident)
            triggerCloudSync(silent = true)
        }
    }

    // --- 4. МЕТОДЫ МОДУЛЯ «ГРАФИК ДЕЖУРСТВ ПО ЭТАЖУ» ---

    fun canEditDutySchedule(floor: Int): Boolean {
        return _currentRole.value == StaffRole.DEPUTY_EDUCATOR ||
                (_currentRole.value == StaffRole.FLOOR_COMMANDER && _assignedFloor.value == floor)
    }

    fun saveFloorDutyRecord(record: FloorDutyScheduleRecord) {
        if (!canEditDutySchedule(record.floor)) return
        viewModelScope.launch {
            repository.saveFloorDutyWeek(record.floor, listOf(record))
        }
    }

    fun saveFloorDutyWeek(floor: Int, records: List<FloorDutyScheduleRecord>) {
        if (!canEditDutySchedule(floor)) return
        viewModelScope.launch {
            repository.saveFloorDutyWeek(floor, records)
        }
    }
}


