package com.example.dormitoryapp.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

/**
 * Интерфейс Репозитория (Single Source of Truth).
 * Абстрагирует UI и ViewModel от конкретных источников данных (Room, Supabase или будущего собственного сервера).
 * При переносе базы с Supabase на свой бэкенд достаточно изменить реализацию этого репозитория.
 */
interface DormRepository {

    // Потоки данных (из локальной базы данных Room)
    val allRoomsFlow: Flow<List<DormRoom>>
    fun getHeadcountByDateFlow(date: String): Flow<List<HeadcountRecord>>
    fun getCleanlinessByDateFlow(date: String): Flow<List<CleanlinessRecord>>
    val allCleanlinessRecordsFlow: Flow<List<CleanlinessRecord>>
    fun getKitchenDutiesByDateFlow(date: String): Flow<List<KitchenDutyRecord>>
    val allSilenceIncidentsFlow: Flow<List<SilenceIncidentRecord>>
    val allFloorDutySchedulesFlow: Flow<List<FloorDutyScheduleRecord>>

    // Операции с данными (сохранение в Room + очередь отправки в облако)
    suspend fun updateRoomCapacity(room: DormRoom, newCapacity: Int)
    suspend fun saveHeadcountRecord(record: HeadcountRecord)
    suspend fun fillUncheckedRoomsWithNorm(
        floor: Int,
        date: String,
        inspectorLabel: String,
        allRooms: List<DormRoom>,
        existingRoomNumbers: Set<Int>
    )

    suspend fun saveCleanlinessRecord(record: CleanlinessRecord)
    suspend fun deleteCleanlinessRecord(date: String, roomNumber: Int)

    suspend fun saveKitchenDutyRecord(record: KitchenDutyRecord)
    suspend fun deleteKitchenDutyRecord(date: String, floor: Int, kitchenSide: String)

    suspend fun addSilenceIncident(record: SilenceIncidentRecord)
    suspend fun deleteSilenceIncident(incident: SilenceIncidentRecord)

    suspend fun saveFloorDutyWeek(floor: Int, records: List<FloorDutyScheduleRecord>)

    // --- Интеграция с ИС «Цифровое общежитие» (leader-app-api.md) ---
    val allInspectionsFlow: Flow<List<InspectionEntity>>
    val pendingInspectionsCountFlow: Flow<Int>
    suspend fun saveInspection(inspection: InspectionEntity)
    suspend fun getPendingInspections(): List<InspectionEntity>
    suspend fun updateInspectionStatus(
        eventId: String,
        status: String,
        inspectionId: Long? = null,
        reason: String? = null,
        message: String? = null
    )
    suspend fun deleteInspection(eventId: String)
    suspend fun leaderLogin(loginInput: String, passwordInput: String, newPasswordInput: String? = null): LeaderAuthResult
    suspend fun pingLeaderServer(): LeaderPingResult
    suspend fun syncLeaderInspections(): LeaderSyncResult

    // Сетевые операции
    suspend fun syncFromCloud(date: String): Boolean
    suspend fun fetchStaffUsers(): List<RemoteStaffUser>
    suspend fun authenticate(loginInput: String, passwordInput: String, previouslySavedLogin: String?): AuthResult
}

/**
 * Реализация DormRepository, объединяющая Room (DormDao) и Supabase (SupabaseSyncService).
 */
class DormRepositoryImpl(
    private val context: Context,
    private val dao: DormDao
) : DormRepository {

    override val allRoomsFlow: Flow<List<DormRoom>> = dao.getAllRoomsFlow()

    override fun getHeadcountByDateFlow(date: String): Flow<List<HeadcountRecord>> =
        dao.getHeadcountByDateFlow(date)

    override fun getCleanlinessByDateFlow(date: String): Flow<List<CleanlinessRecord>> =
        dao.getCleanlinessByDateFlow(date)

    override val allCleanlinessRecordsFlow: Flow<List<CleanlinessRecord>> =
        dao.getAllCleanlinessRecordsFlow()

    override fun getKitchenDutiesByDateFlow(date: String): Flow<List<KitchenDutyRecord>> =
        dao.getKitchenDutiesByDateFlow(date)

    override val allSilenceIncidentsFlow: Flow<List<SilenceIncidentRecord>> =
        dao.getAllSilenceIncidentsFlow()

    override val allFloorDutySchedulesFlow: Flow<List<FloorDutyScheduleRecord>> =
        dao.getAllFloorDutySchedulesFlow()

    override val allInspectionsFlow: Flow<List<InspectionEntity>> =
        dao.getAllInspectionsFlow()

    override val pendingInspectionsCountFlow: Flow<Int> =
        dao.getPendingInspectionsCountFlow()

    override suspend fun saveInspection(inspection: InspectionEntity) {
        dao.insertInspection(inspection)
    }

    override suspend fun getPendingInspections(): List<InspectionEntity> {
        return dao.getPendingInspections()
    }

    override suspend fun updateInspectionStatus(
        eventId: String,
        status: String,
        inspectionId: Long?,
        reason: String?,
        message: String?
    ) {
        dao.updateInspectionStatus(eventId, status, inspectionId, reason, message)
    }

    override suspend fun deleteInspection(eventId: String) {
        dao.deleteInspection(eventId)
    }

    override suspend fun leaderLogin(
        loginInput: String,
        passwordInput: String,
        newPasswordInput: String?
    ): LeaderAuthResult {
        return LeaderApiService.login(context, loginInput, passwordInput, newPasswordInput)
    }

    override suspend fun pingLeaderServer(): LeaderPingResult {
        return LeaderApiService.ping(context)
    }

    override suspend fun syncLeaderInspections(): LeaderSyncResult {
        val pending = dao.getPendingInspections()
        if (pending.isEmpty()) {
            return LeaderSyncResult.Success(emptyList())
        }
        val result = LeaderApiService.syncInspections(context, pending)
        if (result is LeaderSyncResult.Success) {
            for (item in result.results) {
                val dbStatus = when (item.status.lowercase()) {
                    "accepted" -> InspectionEntity.STATUS_ACCEPTED
                    "duplicate" -> InspectionEntity.STATUS_DUPLICATE
                    "rejected" -> InspectionEntity.STATUS_REJECTED
                    else -> InspectionEntity.STATUS_ACCEPTED
                }
                dao.updateInspectionStatus(
                    eventId = item.eventId,
                    status = dbStatus,
                    inspectionId = item.inspectionId,
                    reason = item.reason,
                    message = item.message
                )
            }
        }
        return result
    }

    override suspend fun updateRoomCapacity(room: DormRoom, newCapacity: Int) {
        val validCapacity = newCapacity.coerceIn(0, 20)
        dao.updateRoom(room.copy(capacity = validCapacity))
        SupabaseSyncService.uploadRoomCapacity(room.roomNumber, validCapacity)
    }

    override suspend fun saveHeadcountRecord(record: HeadcountRecord) {
        val queueKey = "headcount_${record.date}_${record.roomNumber}"
        SyncQueueManager.markPending(context, queueKey)
        dao.saveHeadcountRecord(record)
        if (SupabaseSyncService.uploadHeadcount(record)) {
            SyncQueueManager.markSynced(context, queueKey)
        }
    }

    override suspend fun fillUncheckedRoomsWithNorm(
        floor: Int,
        date: String,
        inspectorLabel: String,
        allRooms: List<DormRoom>,
        existingRoomNumbers: Set<Int>
    ) {
        val floorRooms = allRooms.filter { it.floor == floor }
        for (room in floorRooms) {
            if (room.roomNumber !in existingRoomNumbers) {
                val record = HeadcountRecord(
                    date = date,
                    roomNumber = room.roomNumber,
                    floor = room.floor,
                    presentCount = room.capacity,
                    inspectorName = inspectorLabel
                )
                saveHeadcountRecord(record)
            }
        }
    }

    override suspend fun saveCleanlinessRecord(record: CleanlinessRecord) {
        val queueKey = "cleanliness_${record.date}_${record.roomNumber}"
        SyncQueueManager.markPending(context, queueKey)
        dao.saveCleanlinessRecord(record)
        if (SupabaseSyncService.uploadCleanliness(record)) {
            SyncQueueManager.markSynced(context, queueKey)
        }
    }

    override suspend fun deleteCleanlinessRecord(date: String, roomNumber: Int) {
        val uploadKey = "cleanliness_${date}_$roomNumber"
        val deleteKey = "delete_cleanliness_${date}_$roomNumber"
        SyncQueueManager.markSynced(context, uploadKey)
        SyncQueueManager.markPending(context, deleteKey)
        dao.deleteCleanlinessRecord(date, roomNumber)
        if (SupabaseSyncService.deleteCleanlinessRecordCloud(date, roomNumber)) {
            SyncQueueManager.markSynced(context, deleteKey)
        }
    }

    override suspend fun saveKitchenDutyRecord(record: KitchenDutyRecord) {
        val queueKey = "kitchen_${record.date}_${record.floor}_${record.kitchenSide}"
        SyncQueueManager.markPending(context, queueKey)
        dao.saveKitchenDutyRecord(record)
        if (SupabaseSyncService.uploadKitchenDuty(record)) {
            SyncQueueManager.markSynced(context, queueKey)
        }
    }

    override suspend fun deleteKitchenDutyRecord(date: String, floor: Int, kitchenSide: String) {
        val uploadKey = "kitchen_${date}_${floor}_$kitchenSide"
        val deleteKey = "delete_kitchen_${date}_${floor}_$kitchenSide"
        SyncQueueManager.markSynced(context, uploadKey)
        SyncQueueManager.markPending(context, deleteKey)
        dao.deleteKitchenDutyRecord(date, floor, kitchenSide)
        if (SupabaseSyncService.deleteKitchenDutyCloud(date, floor, kitchenSide)) {
            SyncQueueManager.markSynced(context, deleteKey)
        }
    }

    override suspend fun addSilenceIncident(record: SilenceIncidentRecord) {
        val queueKey = "silence_${record.date}_${record.roomNumber}_${record.time}"
        SyncQueueManager.markPending(context, queueKey)
        dao.addSilenceIncident(record)
        if (SupabaseSyncService.uploadSilenceIncident(record)) {
            SyncQueueManager.markSynced(context, queueKey)
        }
    }

    override suspend fun deleteSilenceIncident(incident: SilenceIncidentRecord) {
        val uploadKey = "silence_${incident.date}_${incident.roomNumber}_${incident.time}"
        val deleteKey = "delete_silence_${incident.date}_${incident.roomNumber}_${incident.time}"
        SyncQueueManager.markSynced(context, uploadKey)
        SyncQueueManager.markPending(context, deleteKey)
        dao.deleteSilenceIncident(incident.id)
        if (SupabaseSyncService.deleteSilenceIncidentCloud(incident)) {
            SyncQueueManager.markSynced(context, deleteKey)
        }
    }

    override suspend fun saveFloorDutyWeek(floor: Int, records: List<FloorDutyScheduleRecord>) {
        val now = System.currentTimeMillis()
        val updated = records.map { it.copy(timestamp = now) }
        dao.saveFloorDutySchedule(updated)
        updated.forEach { r ->
            SupabaseSyncService.uploadFloorDutySchedule(r)
        }
    }

    override suspend fun syncFromCloud(date: String): Boolean {
        return SupabaseSyncService.pullAllDataForDate(context, date, dao)
    }

    override suspend fun fetchStaffUsers(): List<RemoteStaffUser> {
        return SupabaseAuthService.fetchStaffUsers()
    }

    override suspend fun authenticate(
        loginInput: String,
        passwordInput: String,
        previouslySavedLogin: String?
    ): AuthResult {
        return SupabaseAuthService.authenticate(loginInput, passwordInput, previouslySavedLogin)
    }
}
