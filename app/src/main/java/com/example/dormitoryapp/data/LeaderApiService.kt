package com.example.dormitoryapp.data

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/**
 * Сетевой сервис для работы с локальным сервером ИС «Цифровое общежитие».
 * Реализует методы POST /auth, GET /ping, POST /inspections/sync согласно leader-app-api.md
 */
object LeaderApiService {

    const val DEFAULT_BASE_URL = "http://172.17.0.21:8000/api/leader/v1"
    private const val PREFS_NAME = "leader_api_prefs"
    private const val KEY_SERVER_URL = "server_base_url"
    private const val KEY_DEVICE_TOKEN = "device_token"
    private const val KEY_TOKEN_EXPIRES_AT = "token_expires_at"
    private const val KEY_CACHED_PROFILE = "cached_profile_json"

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun getBaseUrl(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_SERVER_URL, DEFAULT_BASE_URL)?.trim()?.trimEnd('/') ?: DEFAULT_BASE_URL
    }

    fun setBaseUrl(context: Context, url: String) {
        val cleanUrl = url.trim().trimEnd('/')
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SERVER_URL, cleanUrl)
            .apply()
    }

    fun getDeviceToken(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_TOKEN, null)
    }

    fun saveToken(context: Context, token: String, expiresAt: String?) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_DEVICE_TOKEN, token)
            .putString(KEY_TOKEN_EXPIRES_AT, expiresAt)
            .apply()
    }

    fun clearToken(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_DEVICE_TOKEN)
            .remove(KEY_TOKEN_EXPIRES_AT)
            .apply()
    }

    fun getCachedProfile(context: Context): LeaderProfile? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_CACHED_PROFILE, null) ?: return null
        return try {
            LeaderProfile.fromJson(json)
        } catch (e: Exception) {
            null
        }
    }

    fun saveCachedProfile(context: Context, profile: LeaderProfile) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CACHED_PROFILE, profile.toJson())
            .apply()
    }

    fun getDeviceName(): String {
        val manufacturer = Build.MANUFACTURER.replaceFirstChar { it.uppercase() }
        val model = Build.MODEL
        return if (model.startsWith(manufacturer, ignoreCase = true)) model else "$manufacturer $model"
    }

    /**
     * Текущее время в формате ISO 8601 со смещением часового пояса (напр. 2026-10-25T23:12:40+03:00)
     */
    fun currentIsoTime(): String {
        return OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    }

    // --- 1. POST /auth ---

    suspend fun login(
        context: Context,
        loginInput: String,
        passwordInput: String,
        newPasswordInput: String? = null
    ): LeaderAuthResult = withContext(Dispatchers.IO) {
        val baseUrl = getBaseUrl(context)
        val url = "$baseUrl/auth"

        val bodyJson = JSONObject().apply {
            put("login", loginInput.trim())
            put("password", passwordInput.trim())
            put("device_name", getDeviceName())
            if (newPasswordInput != null) {
                put("new_password", newPasswordInput.trim())
            } else {
                put("new_password", JSONObject.NULL)
            }
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .post(bodyJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string().orEmpty()

            when (code) {
                200 -> {
                    val root = JSONObject(body)
                    val token = root.getString("token")
                    val expiresAt = if (root.isNull("expires_at")) null else root.optString("expires_at")
                    val profileObj = root.getJSONObject("profile")
                    val profile = LeaderProfile.fromJson(profileObj.toString())

                    saveToken(context, token, expiresAt)
                    saveCachedProfile(context, profile)

                    LeaderAuthResult.Success(token, expiresAt, profile)
                }
                401 -> {
                    LeaderAuthResult.Error(code, "Неверный логин или пароль")
                }
                403 -> {
                    LeaderAuthResult.Error(code, "Учётка не старосты или заблокирована — обратитесь к коменданту")
                }
                409 -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val msg = root?.optString("message", "Требуется смена временного пароля") ?: "Требуется смена пароля"
                    LeaderAuthResult.PasswordChangeRequired(msg)
                }
                422 -> {
                    val root = runCatching { JSONObject(body) }.getOrNull()
                    val errorsMap = mutableMapOf<String, String>()
                    val errorsObj = root?.optJSONObject("errors")
                    if (errorsObj != null) {
                        for (k in errorsObj.keys()) {
                            errorsMap[k] = errorsObj.optString(k)
                        }
                    }
                    val msg = root?.optString("message", "Неверно заполнены поля") ?: "Ошибка валидации"
                    LeaderAuthResult.Error(code, msg, errorsMap)
                }
                429 -> {
                    LeaderAuthResult.Error(code, "Слишком много попыток входа, подождите")
                }
                else -> {
                    LeaderAuthResult.Error(code, "Ошибка сервера ($code): $body")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LeaderAuthResult.Error(-1, "Не удалось подключиться к серверу (${e.localizedMessage ?: e.javaClass.simpleName}). Проверьте подключение по кабелю или Wi-Fi на вахте.")
        }
    }

    // --- 2. GET /ping ---

    suspend fun ping(context: Context): LeaderPingResult = withContext(Dispatchers.IO) {
        val token = getDeviceToken(context)
        if (token.isNullOrBlank()) {
            return@withContext LeaderPingResult.Unauthorized("Устройство не привязано, выполните вход")
        }

        // Если включен демо-режим тестирования без реального сервера
        if (token.startsWith("demo-token")) {
            val cached = getCachedProfile(context)
            if (cached != null) {
                return@withContext LeaderPingResult.Success(currentIsoTime(), cached)
            }
        }

        val baseUrl = getBaseUrl(context)
        val url = "$baseUrl/ping"

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .get()
            .build()

        try {
            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string().orEmpty()

            when (code) {
                200 -> {
                    val root = JSONObject(body)
                    val serverTime = root.optString("server_time", currentIsoTime())
                    val profileObj = root.getJSONObject("profile")
                    val profile = LeaderProfile.fromJson(profileObj.toString())

                    // Профиль заменяем присланным (состав комнат и этаж могли измениться)
                    saveCachedProfile(context, profile)

                    LeaderPingResult.Success(serverTime, profile)
                }
                401 -> {
                    LeaderPingResult.Unauthorized("Срок действия токена истёк или сессия завершена. Требуется повторный вход.")
                }
                else -> {
                    LeaderPingResult.Error("Сервер вернул код $code: $body")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LeaderPingResult.Error("Нет связи с сервером на вахте: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }

    // --- 3. POST /inspections/sync ---

    suspend fun syncInspections(
        context: Context,
        inspections: List<InspectionEntity>
    ): LeaderSyncResult = withContext(Dispatchers.IO) {
        if (inspections.isEmpty()) {
            return@withContext LeaderSyncResult.Success(emptyList())
        }

        val token = getDeviceToken(context)
        if (token.isNullOrBlank()) {
            return@withContext LeaderSyncResult.Unauthorized("Токен не найден, требуется вход")
        }

        // Если включен демо-режим тестирования без реального сервера
        if (token.startsWith("demo-token")) {
            val mockResults = inspections.take(50).mapIndexed { idx, insp ->
                SyncResultItem(
                    eventId = insp.eventId,
                    status = "accepted",
                    inspectionId = 900L + idx + (1..100).random(),
                    reason = null,
                    message = null
                )
            }
            return@withContext LeaderSyncResult.Success(mockResults)
        }

        val baseUrl = getBaseUrl(context)
        val url = "$baseUrl/inspections/sync"

        // Пачка до 50 завершённых проверок
        val batch = inspections.take(50)
        val inspectionsArray = JSONArray()

        for (insp in batch) {
            val inspObj = JSONObject().apply {
                put("event_id", insp.eventId)
                put("type", insp.type)
                put("floor", insp.floor)
                put("started_at", insp.startedAt)
                put("finished_at", insp.finishedAt)

                val roomsList = InspectionEntity.parseRoomsJson(insp.roomsJson)
                val roomsArr = JSONArray()
                for (r in roomsList) {
                    val rObj = JSONObject().apply {
                        put("room_id", r.roomId)
                        put("score", r.score)
                        put("comment", r.comment)
                    }
                    roomsArr.put(rObj)
                }
                put("rooms", roomsArr)
            }
            inspectionsArray.put(inspObj)
        }

        val requestJson = JSONObject().apply {
            put("sent_at", currentIsoTime())
            put("inspections", inspectionsArray)
        }.toString()

        val request = Request.Builder()
            .url(url)
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .post(requestJson.toRequestBody(jsonMediaType))
            .build()

        try {
            val response = client.newCall(request).execute()
            val code = response.code
            val body = response.body?.string().orEmpty()

            when (code) {
                200 -> {
                    val root = JSONObject(body)
                    val resultsArr = root.optJSONArray("results") ?: JSONArray()
                    val results = mutableListOf<SyncResultItem>()
                    for (i in 0 until resultsArr.length()) {
                        val ro = resultsArr.getJSONObject(i)
                        results.add(
                            SyncResultItem(
                                eventId = ro.optString("event_id", ""),
                                status = ro.optString("status", "accepted"),
                                inspectionId = if (ro.isNull("inspection_id")) null else ro.optLong("inspection_id"),
                                reason = if (ro.isNull("reason")) null else ro.optString("reason"),
                                message = if (ro.isNull("message")) null else ro.optString("message")
                            )
                        )
                    }
                    LeaderSyncResult.Success(results)
                }
                401 -> {
                    LeaderSyncResult.Unauthorized("Токен не действует, требуется вход")
                }
                422 -> {
                    LeaderSyncResult.Error(code, "Пачка составлена неверно (ошибка приложения): $body")
                }
                else -> {
                    LeaderSyncResult.Error(code, "Ошибка сервера ($code): $body")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LeaderSyncResult.Error(-1, "Сбой связи при отправке данных: ${e.localizedMessage ?: e.javaClass.simpleName}")
        }
    }
}
