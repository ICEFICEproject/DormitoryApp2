package com.example.dormitoryapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * DAO (Data Access Object) — интерфейс с командами для чтения и записи в базу данных.
 * Благодаря Flow<List<...>> экран приложения автоматически обновляется,
 * как только дежурный сохраняет новую запись.
 */
@Dao
interface DormDao {

    // --- 1. РАБОТА С КОМНАТАМИ (Справочник) ---

    @Query("SELECT * FROM dorm_rooms ORDER BY roomNumber ASC")
    fun getAllRoomsFlow(): Flow<List<DormRoom>>

    @Query("SELECT * FROM dorm_rooms WHERE floor = :floor ORDER BY roomNumber ASC")
    fun getRoomsByFloorFlow(floor: Int): Flow<List<DormRoom>>

    @Query("SELECT * FROM dorm_rooms WHERE roomNumber = :roomNumber LIMIT 1")
    suspend fun getRoomByNumber(roomNumber: Int): DormRoom?

    @Query("SELECT COUNT(*) FROM dorm_rooms")
    suspend fun getRoomsCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRooms(rooms: List<DormRoom>)

    @Update
    suspend fun updateRoom(room: DormRoom)

    // --- 2. ВЕЧЕРНИЙ ОБХОД И ЧИСЛЕННОСТЬ ---

    @Query("SELECT * FROM headcount_records WHERE date = :date ORDER BY roomNumber ASC")
    fun getHeadcountByDateFlow(date: String): Flow<List<HeadcountRecord>>

    @Query("SELECT * FROM headcount_records")
    suspend fun getAllHeadcountRecords(): List<HeadcountRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveHeadcountRecord(record: HeadcountRecord)

    // --- 3. ЧИСТОТА КОМНАТ ---

    @Query("SELECT * FROM cleanliness_records WHERE date = :date ORDER BY roomNumber ASC")
    fun getCleanlinessByDateFlow(date: String): Flow<List<CleanlinessRecord>>

    @Query("SELECT * FROM cleanliness_records ORDER BY date DESC, roomNumber ASC")
    fun getAllCleanlinessRecordsFlow(): Flow<List<CleanlinessRecord>>

    @Query("SELECT * FROM cleanliness_records")
    suspend fun getAllCleanlinessRecords(): List<CleanlinessRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCleanlinessRecord(record: CleanlinessRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCleanlinessRecords(records: List<CleanlinessRecord>)

    @Query("DELETE FROM cleanliness_records WHERE date = :date AND roomNumber = :roomNumber")
    suspend fun deleteCleanlinessRecord(date: String, roomNumber: Int)

    @Query("SELECT * FROM cleanliness_records WHERE date = :date")
    suspend fun getCleanlinessRecordsForDate(date: String): List<CleanlinessRecord>

    @androidx.room.Transaction
    suspend fun syncCleanlinessFromCloud(date: String, cloudRecords: List<CleanlinessRecord>) {
        val cloudRoomNumbers = cloudRecords.map { it.roomNumber }.toSet()
        val localRecords = getCleanlinessRecordsForDate(date)
        for (local in localRecords) {
            if (local.roomNumber !in cloudRoomNumbers) {
                deleteCleanlinessRecord(date, local.roomNumber)
            }
        }
        if (cloudRecords.isNotEmpty()) {
            insertCleanlinessRecords(cloudRecords)
        }
    }

    @androidx.room.Transaction
    suspend fun syncAllCleanlinessFromCloud(cloudRecords: List<CleanlinessRecord>, protectedKeys: Set<String> = emptySet()) {
        val cloudKeys = cloudRecords.map { "${it.date}_${it.roomNumber}" }.toSet()
        val localRecords = getAllCleanlinessRecords()
        for (local in localRecords) {
            val key = "${local.date}_${local.roomNumber}"
            if (key !in cloudKeys && key !in protectedKeys) {
                deleteCleanlinessRecord(local.date, local.roomNumber)
            }
        }
        if (cloudRecords.isNotEmpty()) {
            insertCleanlinessRecords(cloudRecords)
        }
    }

    // --- 4. ДЕЖУРСТВО ПО КУХНЕ ---

    @Query("SELECT * FROM kitchen_duty_records WHERE date = :date ORDER BY floor ASC, kitchenSide ASC")
    fun getKitchenDutiesByDateFlow(date: String): Flow<List<KitchenDutyRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveKitchenDutyRecord(record: KitchenDutyRecord)

    @Query("DELETE FROM kitchen_duty_records WHERE date = :date AND floor = :floor AND kitchenSide = :kitchenSide")
    suspend fun deleteKitchenDutyRecord(date: String, floor: Int, kitchenSide: String)

    @Query("SELECT * FROM kitchen_duty_records WHERE date = :date")
    suspend fun getKitchenDutiesForDate(date: String): List<KitchenDutyRecord>

    @Query("SELECT * FROM kitchen_duty_records")
    suspend fun getAllKitchenDuties(): List<KitchenDutyRecord>

    // --- 5. ТИШИНА И КОМЕНДАНТСКИЙ ЧАС ---

    @Query("SELECT * FROM silence_incidents ORDER BY timestamp DESC")
    fun getAllSilenceIncidentsFlow(): Flow<List<SilenceIncidentRecord>>

    @Query("SELECT * FROM silence_incidents")
    suspend fun getAllSilenceIncidentsList(): List<SilenceIncidentRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addSilenceIncident(incident: SilenceIncidentRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSilenceIncidents(incidents: List<SilenceIncidentRecord>)

    @Query("DELETE FROM silence_incidents WHERE id = :id")
    suspend fun deleteSilenceIncident(id: Int)

    @Query("DELETE FROM silence_incidents")
    suspend fun clearAllSilenceIncidents()

    @androidx.room.Transaction
    suspend fun syncSilenceIncidentsFromCloud(
        cloudIncidents: List<SilenceIncidentRecord>,
        protectedIncidentKeys: Set<String> = emptySet()
    ) {
        val cloudIds = cloudIncidents.map { it.id }.toSet()
        val cloudKeys = cloudIncidents.map { "${it.date}_${it.roomNumber}_${it.time}" }.toSet()
        val localIncidents = getAllSilenceIncidentsList()
        for (local in localIncidents) {
            val localKey = "${local.date}_${local.roomNumber}_${local.time}"
            if (local.id !in cloudIds) {
                // Если запись уже есть в облаке (но под облачным id), удаляем старую локальную запись, чтобы не было дубля
                if (localKey in cloudKeys) {
                    deleteSilenceIncident(local.id)
                } else if (localKey !in protectedIncidentKeys) {
                    // Записи нет в облаке и она не в оффлайн-очереди (была удалена на другом устройстве)
                    deleteSilenceIncident(local.id)
                }
            }
        }
        if (cloudIncidents.isNotEmpty()) {
            insertSilenceIncidents(cloudIncidents)
        }
    }

    // --- 6. НЕДЕЛЬНЫЙ ГРАФИК ДЕЖУРСТВ СОТРУДНИКОВ ПО ЭТАЖУ ---

    @Query("SELECT * FROM floor_duty_schedule WHERE floor = :floor ORDER BY dayOfWeek ASC")
    fun getFloorDutyScheduleFlow(floor: Int): Flow<List<FloorDutyScheduleRecord>>

    @Query("SELECT * FROM floor_duty_schedule ORDER BY floor ASC, dayOfWeek ASC")
    fun getAllFloorDutySchedulesFlow(): Flow<List<FloorDutyScheduleRecord>>

    @Query("SELECT * FROM floor_duty_schedule WHERE floor = :floor")
    suspend fun getFloorDutyScheduleList(floor: Int): List<FloorDutyScheduleRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFloorDutySchedule(records: List<FloorDutyScheduleRecord>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveFloorDutyScheduleRecord(record: FloorDutyScheduleRecord)

    // --- 7. ОФФЛАЙН-ОЧЕРЕДЬ И ИСТОРИЯ ПРОВЕРОК СТАРОСТЫ (ИС «Цифровое общежитие») ---

    @Query("SELECT * FROM leader_inspections ORDER BY createdAt DESC")
    fun getAllInspectionsFlow(): Flow<List<InspectionEntity>>

    @Query("SELECT * FROM leader_inspections WHERE syncStatus = 'PENDING' ORDER BY createdAt ASC")
    suspend fun getPendingInspections(): List<InspectionEntity>

    @Query("SELECT COUNT(*) FROM leader_inspections WHERE syncStatus = 'PENDING'")
    fun getPendingInspectionsCountFlow(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInspection(inspection: InspectionEntity)

    @Query("UPDATE leader_inspections SET syncStatus = :status, inspectionId = :inspectionId, rejectReason = :reason, rejectMessage = :message WHERE eventId = :eventId")
    suspend fun updateInspectionStatus(
        eventId: String,
        status: String,
        inspectionId: Long? = null,
        reason: String? = null,
        message: String? = null
    )

    @Query("DELETE FROM leader_inspections WHERE eventId = :eventId")
    suspend fun deleteInspection(eventId: String)

    @Query("DELETE FROM leader_inspections")
    suspend fun clearAllInspections()
}

