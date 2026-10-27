package com.example.dormitoryapp.data

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
 * Модель сотрудника, загружаемая из Supabase.
 */
data class RemoteStaffUser(
    val id: Long,
    val login: String,
    val fullName: String,
    val role: StaffRole,
    val assignedFloor: Int,
    val isActivated: Boolean,
    val livingRoomNumber: Int? = null
)

sealed class AuthResult {
    data class Success(val user: RemoteStaffUser) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

/**
 * Сетевой сервис для связи с облачной базой Supabase.
 * Проверяет логин, пароль, должность, закрепленный этаж и блокирует вход
 * под уже зарегистрированными пользователями.
 */
object SupabaseAuthService {

    private val client = NetworkClient.okHttpClient
    private val SUPABASE_URL = NetworkClient.SUPABASE_URL
    private val SUPABASE_ANON_KEY = NetworkClient.SUPABASE_ANON_KEY

    /**
     * Преобразование текстовой должности из базы в перечисление StaffRole.
     * Понимает и английские ключи (Starosta, FLOOR_HEAD), и русские названия.
     */
    fun parseRole(rawRole: String): StaffRole {
        val lower = rawRole.trim().lowercase()
        return when {
            lower.contains("deputy") || lower.contains("admin") || lower.contains("воспит") || lower.contains("зам") -> StaffRole.DEPUTY_EDUCATOR
            lower.contains("commander") || lower.contains("команд") || lower.contains("комманд") || lower.contains("комдир") -> StaffRole.FLOOR_COMMANDER
            lower.contains("head") || lower.contains("starosta") || lower.contains("старост") -> StaffRole.FLOOR_HEAD
            lower.contains("init") || lower.contains("инициатив") || lower.contains("стажер") || lower.contains("стажёр") -> StaffRole.INITIATIVE_GROUP
            else -> StaffRole.FLOOR_HEAD
        }
    }

    /**
     * Проверка логина и пароля в Supabase.
     */
    suspend fun authenticate(
        loginInput: String,
        passwordInput: String,
        previouslySavedLogin: String?
    ): AuthResult = withContext(Dispatchers.IO) {
        val cleanLogin = loginInput.trim()
        val cleanPassword = passwordInput.trim()

        if (cleanLogin.isBlank()) {
            return@withContext AuthResult.Error("Введите ваш логин")
        }
        if (cleanPassword.isBlank()) {
            return@withContext AuthResult.Error("Введите ваш пароль")
        }

        try {
            val encodedLogin = URLEncoder.encode(cleanLogin, "UTF-8")
            val url = "$SUPABASE_URL/rest/v1/staff_users?login=eq.$encodedLogin&select=*"

            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()

            if (!response.isSuccessful) {
                return@withContext AuthResult.Error("Ошибка сервера Supabase (${response.code}). Проверьте интернет.")
            }

            val jsonArray = JSONArray(body)
            if (jsonArray.length() == 0) {
                return@withContext AuthResult.Error("Пользователь с логином «$cleanLogin» не найден. Обратитесь к администратору.")
            }

            val userObj = jsonArray.getJSONObject(0)
            val dbPassword = userObj.optString("password")
            val fullName = userObj.optString("full_name", "Сотрудник")
            val roleStr = userObj.optString("role", "FLOOR_HEAD")
            val assignedFloor = userObj.optInt("assigned_floor", 3).coerceIn(2, 5)
            val isActivated = userObj.optBoolean("is_activated", false)
            val livingRoom = if (userObj.isNull("room_number")) null else userObj.optInt("room_number")
            val id = userObj.optLong("id", 0)

            // 1. Проверяем пароль
            if (dbPassword != cleanPassword) {
                return@withContext AuthResult.Error("Неверный пароль. Попробуйте еще раз.")
            }

            val parsedRole = parseRole(roleStr)

            // 2. Проверяем повторный вход под уже активированным лицом:
            // Если аккаунт уже активирован, а вход идет с нового/чужого телефона (и это не зам. воспитателя)
            val isDeputy = parsedRole == StaffRole.DEPUTY_EDUCATOR
            if (isActivated && !isDeputy) {
                // Если на данном телефоне ранее был сохранен ДРУГОЙ логин — блокируем смену аккаунта
                val isAnotherAccountOnThisDevice = previouslySavedLogin != null && !previouslySavedLogin.equals(cleanLogin, ignoreCase = true)
                if (isAnotherAccountOnThisDevice) {
                    return@withContext AuthResult.Error(
                        "На этом устройстве уже зарегистрирован другой сотрудник ($previouslySavedLogin). Вход под чужими данными запрещен."
                    )
                }
            }

            // 3. Если это первый вход — помечаем пользователя как активированного в облачной базе Supabase
            if (!isActivated) {
                markUserAsActivated(cleanLogin)
            }

            val staffUser = RemoteStaffUser(
                id = id,
                login = cleanLogin,
                fullName = fullName,
                role = parsedRole,
                assignedFloor = assignedFloor,
                isActivated = true,
                livingRoomNumber = livingRoom
            )

            return@withContext AuthResult.Success(staffUser)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext AuthResult.Error("Не удалось подключиться к базе данных. Проверьте подключение к интернету.")
        }
    }

    /**
     * Пометка аккаунта как активированного в Supabase.
     */
    private fun markUserAsActivated(login: String) {
        try {
            val encodedLogin = URLEncoder.encode(login, "UTF-8")
            val patchUrl = "$SUPABASE_URL/rest/v1/staff_users?login=eq.$encodedLogin"
            val jsonBody = JSONObject().apply {
                put("is_activated", true)
            }.toString()

            val requestBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
            val patchRequest = Request.Builder()
                .url(patchUrl)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .addHeader("Prefer", "return=minimal")
                .patch(requestBody)
                .build()

            client.newCall(patchRequest).execute().close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Загрузка списка всех зарегистрированных сотрудников из Supabase (для графиков дежурств и списков).
     */
    suspend fun fetchStaffUsers(): List<RemoteStaffUser> = withContext(Dispatchers.IO) {
        try {
            val url = "$SUPABASE_URL/rest/v1/staff_users?select=*"
            val request = Request.Builder()
                .url(url)
                .addHeader("apikey", SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $SUPABASE_ANON_KEY")
                .get()
                .build()

            val response = client.newCall(request).execute()
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) return@withContext emptyList()

            val jsonArray = JSONArray(body)
            val result = mutableListOf<RemoteStaffUser>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                result.add(
                    RemoteStaffUser(
                        id = obj.optLong("id", 0),
                        login = obj.optString("login"),
                        fullName = obj.optString("full_name"),
                        role = parseRole(obj.optString("role", "FLOOR_HEAD")),
                        assignedFloor = obj.optInt("assigned_floor", 3).coerceIn(2, 5),
                        isActivated = obj.optBoolean("is_activated", false),
                        livingRoomNumber = if (obj.isNull("room_number")) null else obj.optInt("room_number")
                    )
                )
            }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}

