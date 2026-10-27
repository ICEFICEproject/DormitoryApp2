package com.example.dormitoryapp.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Сервис живой синхронизации данных между телефонами всех старост через Supabase.
 * - При внесении изменений они улетают в облако; если интернета нет — помещаются в очередь SyncQueueManager.
 * - При синхронизации оффлайн-записи сначала отправляются на сервер, защищая работу дежурных от стирания.
 */
object SupabaseSyncService {

    private val SUPABASE_URL = NetworkClient.SUPABASE_URL
    private val SUPABASE_ANON_KEY = NetworkClient.SUPABASE_ANON_KEY
    private val jsonMediaType = NetworkClient.jsonMediaType
    private val client = NetworkClient.okHttpClient

    private fun buildPostRequest(endpoint: String, json: String): Request {
        return Request.Builder()
            .url("$SUPABASE_URL/rest/v1/$endpoint")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .addHeader("Prefer", "resolution=merge-duplicates")
            .post(json.toRequestBody(jsonMediaType))
            .build()
    }

    private fun buildGetRequest(endpointWithParams: String): Request {
        return Request.Builder()
            .url("$SUPABASE_URL/rest/v1/$endpointWithParams")
            .addHeader("apikey", SUPABASE_ANON_KEY)
            .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
            .get()
            .build()
    }

    // --- 1. ОТПРАВКА ДАННЫХ В ОБЛАКО (UPSERT) ---

    suspend fun uploadHeadcount(record: HeadcountRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("date", record.date)
                put("room_number", record.roomNumber)
                put("floor", record.floor)
                put("present_count", record.presentCount)
                put("inspector_name", record.inspectorName)
            }.toString()

            client.newCall(buildPostRequest("cloud_headcount", json)).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadCleanliness(record: CleanlinessRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("date", record.date)
                put("room_number", record.roomNumber)
                put("floor", record.floor)
                put("status", record.status)
                put("comment", record.comment)
                put("inspector_name", record.inspectorName)
            }.toString()

            client.newCall(buildPostRequest("cloud_cleanliness", json)).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteCleanlinessRecordCloud(date: String, roomNumber: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedDate = URLEncoder.encode(date, "UTF-8")
            val url = "$SUPABASE_URL/rest/v1/cloud_cleanliness?date=eq.$encodedDate&room_number=eq.$roomNumber"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadKitchenDuty(record: KitchenDutyRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("date", record.date)
                put("floor", record.floor)
                put("kitchen_side", record.kitchenSide)
                put("duty_room_number", record.dutyRoomNumber)
                put("status", record.status)
                put("comment", record.comment)
                put("inspector_name", record.inspectorName)
            }.toString()

            client.newCall(buildPostRequest("cloud_kitchen_duty", json)).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteKitchenDutyCloud(date: String, floor: Int, kitchenSide: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedDate = URLEncoder.encode(date, "UTF-8")
            val encodedSide = URLEncoder.encode(kitchenSide, "UTF-8")
            val url = "$SUPABASE_URL/rest/v1/cloud_kitchen_duty?date=eq.$encodedDate&floor=eq.$floor&kitchen_side=eq.$encodedSide"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadSilenceIncident(incident: SilenceIncidentRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("date", incident.date)
                put("time", incident.time)
                put("room_number", incident.roomNumber)
                put("floor", incident.floor)
                put("violation_type", incident.violationType)
                put("comment", incident.comment)
                put("inspector_name", incident.inspectorName)
            }.toString()

            client.newCall(buildPostRequest("cloud_silence_incidents", json)).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteSilenceIncidentCloud(incident: SilenceIncidentRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val encodedDate = URLEncoder.encode(incident.date, "UTF-8")
            val encodedTime = URLEncoder.encode(incident.time, "UTF-8")
            val url = if (incident.id > 0) {
                "$SUPABASE_URL/rest/v1/cloud_silence_incidents?or=(id.eq.${incident.id},and(date.eq.$encodedDate,room_number.eq.${incident.roomNumber},time.eq.$encodedTime))"
            } else {
                "$SUPABASE_URL/rest/v1/cloud_silence_incidents?date=eq.$encodedDate&room_number=eq.${incident.roomNumber}&time=eq.$encodedTime"
            }
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .delete()
                .build()

            client.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadRoomCapacity(roomNumber: Int, capacity: Int): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("room_number", roomNumber)
                put("capacity", capacity)
            }.toString()

            client.newCall(buildPostRequest("cloud_room_capacities", json)).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadFloorDutySchedule(record: FloorDutyScheduleRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("floor", record.floor)
                put("day_of_week", record.dayOfWeek)
                put("day_name", record.dayName)
                put("person_name", record.personName)
                put("person_role", record.personRole)
                put("updated_at", record.timestamp)
            }.toString()

            client.newCall(buildPostRequest("cloud_floor_duty_schedule", json)).execute().use { response ->
                response.isSuccessful
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    // --- 2. СБРОС ОЧЕРЕДИ ОФФЛАЙН-ОТМЕТОК В ОБЛАКО ---

    suspend fun flushPendingOfflineQueue(context: Context, dao: DormDao) = withContext(Dispatchers.IO) {
        val pending = SyncQueueManager.getAllPending(context)
        if (pending.isEmpty()) return@withContext

        // 0. Сначала обрабатываем отложенные оффлайн-удаления
        for (key in pending) {
            if (key.startsWith("delete_cleanliness_")) {
                val parts = key.removePrefix("delete_cleanliness_").split("_")
                if (parts.size == 2) {
                    val date = parts[0]
                    val roomNumber = parts[1].toIntOrNull() ?: continue
                    if (deleteCleanlinessRecordCloud(date, roomNumber)) {
                        SyncQueueManager.markSynced(context, key)
                    }
                }
            } else if (key.startsWith("delete_kitchen_")) {
                val parts = key.removePrefix("delete_kitchen_").split("_")
                if (parts.size >= 3) {
                    val date = parts[0]
                    val floor = parts[1].toIntOrNull() ?: continue
                    val side = parts.subList(2, parts.size).joinToString("_")
                    if (deleteKitchenDutyCloud(date, floor, side)) {
                        SyncQueueManager.markSynced(context, key)
                    }
                }
            } else if (key.startsWith("delete_silence_")) {
                val parts = key.removePrefix("delete_silence_").split("_")
                if (parts.size >= 3) {
                    val date = parts[0]
                    val room = parts[1].toIntOrNull() ?: continue
                    val time = parts[2]
                    val dummy = SilenceIncidentRecord(
                        id = 0,
                        date = date,
                        time = time,
                        roomNumber = room,
                        floor = room / 100,
                        violationType = "",
                        comment = "",
                        inspectorName = ""
                    )
                    if (deleteSilenceIncidentCloud(dummy)) {
                        SyncQueueManager.markSynced(context, key)
                    }
                }
            }
        }

        // 1. Отправка оффлайн-чистоты
        val localClean = dao.getAllCleanlinessRecords()
        for (rec in localClean) {
            val key = "cleanliness_${rec.date}_${rec.roomNumber}"
            if (key in pending) {
                if (uploadCleanliness(rec)) {
                    SyncQueueManager.markSynced(context, key)
                }
            }
        }

        // 2. Отправка оффлайн-тишины
        val localSilence = dao.getAllSilenceIncidentsList()
        for (rec in localSilence) {
            val key = "silence_${rec.date}_${rec.roomNumber}_${rec.time}"
            if (key in pending) {
                if (uploadSilenceIncident(rec)) {
                    SyncQueueManager.markSynced(context, key)
                }
            }
        }

        // 3. Отправка оффлайн-численности
        val localHeadcount = dao.getAllHeadcountRecords()
        for (rec in localHeadcount) {
            val key = "headcount_${rec.date}_${rec.roomNumber}"
            if (key in pending) {
                if (uploadHeadcount(rec)) {
                    SyncQueueManager.markSynced(context, key)
                }
            }
        }

        // 4. Отправка оффлайн-дежурств по кухне
        val localKitchen = dao.getAllKitchenDuties()
        for (rec in localKitchen) {
            val key = "kitchen_${rec.date}_${rec.floor}_${rec.kitchenSide}"
            if (key in pending) {
                if (uploadKitchenDuty(rec)) {
                    SyncQueueManager.markSynced(context, key)
                }
            }
        }
    }

    // --- 3. СКАЧИВАНИЕ СВЕЖИХ ДАННЫХ ИЗ ОБЛАКА В ТЕЛЕФОН С ЗАЩИТОЙ ОФФЛАЙНА ---

    suspend fun pullAllDataForDate(context: Context, date: String, dao: DormDao): Boolean = withContext(Dispatchers.IO) {
        try {
            // 0. Сначала сбрасываем оффлайн-очередь, отправляя локальные отметки в облако
            runCatching { flushPendingOfflineQueue(context, dao) }

            val pendingKeys = SyncQueueManager.getAllPending(context)
            val protectedCleanKeys = pendingKeys
                .filter { it.startsWith("cleanliness_") }
                .map { it.removePrefix("cleanliness_") }
                .toSet()

            val deletedCleanKeys = pendingKeys
                .filter { it.startsWith("delete_cleanliness_") }
                .map { it.removePrefix("delete_cleanliness_") }
                .toSet()

            val protectedSilenceKeys = pendingKeys
                .filter { it.startsWith("silence_") }
                .map { it.removePrefix("silence_") }
                .toSet()

            val deletedSilenceKeys = pendingKeys
                .filter { it.startsWith("delete_silence_") }
                .map { it.removePrefix("delete_silence_") }
                .toSet()

            val deletedKitchenKeys = pendingKeys
                .filter { it.startsWith("delete_kitchen_") }
                .map { it.removePrefix("delete_kitchen_") }
                .toSet()

            val encodedDate = URLEncoder.encode(date, "UTF-8")

            // 1. Синхронизируем измененные нормы комнат
            runCatching {
                client.newCall(buildGetRequest("cloud_room_capacities?select=*")).execute().use { capResponse ->
                    if (capResponse.isSuccessful) {
                        val body = capResponse.body?.string().orEmpty()
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val rNum = obj.optInt("room_number")
                            val cap = obj.optInt("capacity")
                            val local = dao.getRoomByNumber(rNum)
                            if (local != null && local.capacity != cap) {
                                dao.updateRoom(local.copy(capacity = cap))
                            }
                        }
                    }
                }
            }

            // 2. Скачиваем численность за указанную дату
            runCatching {
                client.newCall(buildGetRequest("cloud_headcount?date=eq.$encodedDate&select=*")).execute().use { hcResponse ->
                    if (hcResponse.isSuccessful) {
                        val body = hcResponse.body?.string().orEmpty()
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val record = HeadcountRecord(
                                date = obj.optString("date"),
                                roomNumber = obj.optInt("room_number"),
                                floor = obj.optInt("floor"),
                                presentCount = obj.optInt("present_count"),
                                inspectorName = obj.optString("inspector_name")
                            )
                            dao.saveHeadcountRecord(record)
                        }
                    }
                }
            }

            // 3. Скачиваем чистоту комнат за всё время с защитой оффлайн-записей
            runCatching {
                client.newCall(buildGetRequest("cloud_cleanliness?select=*")).execute().use { cleanResponse ->
                    if (cleanResponse.isSuccessful) {
                        val body = cleanResponse.body?.string().orEmpty()
                        val array = JSONArray(body)
                        val cloudCleanList = mutableListOf<CleanlinessRecord>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val recDate = obj.optString("date")
                            val recRoom = obj.optInt("room_number")
                            val key = "${recDate}_$recRoom"
                            // Не восстанавливаем запись, если она ожидает оффлайн-удаления
                            if (key !in deletedCleanKeys) {
                                cloudCleanList.add(
                                    CleanlinessRecord(
                                        date = recDate,
                                        roomNumber = recRoom,
                                        floor = obj.optInt("floor"),
                                        status = obj.optString("status"),
                                        comment = obj.optString("comment"),
                                        inspectorName = obj.optString("inspector_name")
                                    )
                                )
                            }
                        }
                        dao.syncAllCleanlinessFromCloud(cloudCleanList, protectedCleanKeys)
                    }
                }
            }

            // 4. Скачиваем дежурства по кухням за указанную дату
            runCatching {
                client.newCall(buildGetRequest("cloud_kitchen_duty?date=eq.$encodedDate&select=*")).execute().use { kitchenResponse ->
                    if (kitchenResponse.isSuccessful) {
                        val body = kitchenResponse.body?.string().orEmpty()
                        val array = JSONArray(body)
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val kDate = obj.optString("date")
                            val kFloor = obj.optInt("floor")
                            val kSide = obj.optString("kitchen_side")
                            val kKey = "${kDate}_${kFloor}_$kSide"
                            if (kKey !in deletedKitchenKeys) {
                                val record = KitchenDutyRecord(
                                    date = kDate,
                                    floor = kFloor,
                                    kitchenSide = kSide,
                                    dutyRoomNumber = obj.optInt("duty_room_number"),
                                    status = obj.optString("status"),
                                    comment = obj.optString("comment"),
                                    inspectorName = obj.optString("inspector_name")
                                )
                                dao.saveKitchenDutyRecord(record)
                            }
                        }
                    }
                }
            }

            // 5. Скачиваем инциденты тишины с защитой оффлайн-записей
            runCatching {
                client.newCall(buildGetRequest("cloud_silence_incidents?select=*&order=id.desc")).execute().use { silenceResponse ->
                    if (silenceResponse.isSuccessful) {
                        val body = silenceResponse.body?.string().orEmpty()
                        val array = JSONArray(body)
                        val cloudList = mutableListOf<SilenceIncidentRecord>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            val sDate = obj.optString("date")
                            val sRoom = obj.optInt("room_number")
                            val sTime = obj.optString("time")
                            val sKey = "${sDate}_${sRoom}_$sTime"
                            if (sKey !in deletedSilenceKeys) {
                                cloudList.add(
                                    SilenceIncidentRecord(
                                        id = obj.optInt("id"),
                                        date = sDate,
                                        time = sTime,
                                        roomNumber = sRoom,
                                        floor = obj.optInt("floor"),
                                        violationType = obj.optString("violation_type"),
                                        comment = obj.optString("comment"),
                                        inspectorName = obj.optString("inspector_name")
                                    )
                                )
                            }
                        }
                        dao.syncSilenceIncidentsFromCloud(cloudList, protectedSilenceKeys)
                    }
                }
            }

            // 6. Скачиваем актуальный график дежурств по этажам
            runCatching {
                client.newCall(buildGetRequest("cloud_floor_duty_schedule?select=*")).execute().use { dutyResponse ->
                    if (dutyResponse.isSuccessful) {
                        val body = dutyResponse.body?.string().orEmpty()
                        val array = JSONArray(body)
                        val cloudDuties = mutableListOf<FloorDutyScheduleRecord>()
                        for (i in 0 until array.length()) {
                            val obj = array.getJSONObject(i)
                            cloudDuties.add(
                                FloorDutyScheduleRecord(
                                    floor = obj.optInt("floor"),
                                    dayOfWeek = obj.optInt("day_of_week"),
                                    dayName = obj.optString("day_name"),
                                    personName = obj.optString("person_name"),
                                    personRole = obj.optString("person_role"),
                                    timestamp = obj.optLong("updated_at", System.currentTimeMillis())
                                )
                            )
                        }
                        if (cloudDuties.isNotEmpty()) {
                            dao.saveFloorDutySchedule(cloudDuties)
                        }
                    }
                }
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
