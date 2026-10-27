package com.example.dormitoryapp.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Модели данных интеграции приложения старосты с ИС «Цифровое общежитие».
 * Соответствуют спецификации leader-app-api.md
 */

data class LeaderUser(
    val id: Long,
    val lastName: String,
    val firstName: String,
    val middleName: String
) {
    val fullName: String
        get() = listOf(lastName, firstName, middleName).filter { it.isNotBlank() }.joinToString(" ")
}

data class LeaderRoom(
    val id: Long,
    val number: String,
    val active: Boolean
) {
    val roomNumberInt: Int
        get() = number.toIntOrNull() ?: 0
}

data class LeaderRules(
    val quietStart: String = "23:00",
    val quietLastStart: String = "23:59",
    val quietFinishBy: String = "00:30",
    val cleanlinessMaxHours: Int = 3,
    val cleanlinessScore: List<Int> = listOf(0, 5),
    val commentMax: Int = 500
)

data class LeaderProfile(
    val user: LeaderUser,
    val position: String,
    val positionLabel: String,
    val floor: Int,
    val rooms: List<LeaderRoom>,
    val timezone: String,
    val serverTime: String?,
    val rules: LeaderRules
) {
    fun toJson(): String {
        val root = JSONObject()
        val userObj = JSONObject().apply {
            put("id", user.id)
            put("last_name", user.lastName)
            put("first_name", user.firstName)
            put("middle_name", user.middleName)
        }
        root.put("user", userObj)
        root.put("position", position)
        root.put("position_label", positionLabel)
        root.put("floor", floor)
        root.put("timezone", timezone)
        root.put("server_time", serverTime ?: "")

        val roomsArr = JSONArray()
        for (r in rooms) {
            val ro = JSONObject().apply {
                put("id", r.id)
                put("number", r.number)
                put("active", r.active)
            }
            roomsArr.put(ro)
        }
        root.put("rooms", roomsArr)

        val rulesObj = JSONObject().apply {
            put("quiet_start", rules.quietStart)
            put("quiet_last_start", rules.quietLastStart)
            put("quiet_finish_by", rules.quietFinishBy)
            put("cleanliness_max_hours", rules.cleanlinessMaxHours)
            val scoreArr = JSONArray().apply {
                rules.cleanlinessScore.forEach { put(it) }
            }
            put("cleanliness_score", scoreArr)
            put("comment_max", rules.commentMax)
        }
        root.put("rules", rulesObj)

        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): LeaderProfile {
            val root = JSONObject(jsonStr)
            val userObj = root.optJSONObject("user") ?: JSONObject()
            val user = LeaderUser(
                id = userObj.optLong("id", 0),
                lastName = userObj.optString("last_name", ""),
                firstName = userObj.optString("first_name", ""),
                middleName = userObj.optString("middle_name", "")
            )

            val roomsList = mutableListOf<LeaderRoom>()
            val roomsArr = root.optJSONArray("rooms") ?: JSONArray()
            for (i in 0 until roomsArr.length()) {
                val r = roomsArr.getJSONObject(i)
                roomsList.add(
                    LeaderRoom(
                        id = r.optLong("id", 0),
                        number = r.optString("number", ""),
                        active = r.optBoolean("active", true)
                    )
                )
            }

            val rulesObj = root.optJSONObject("rules") ?: JSONObject()
            val scoreArr = rulesObj.optJSONArray("cleanliness_score")
            val scores = if (scoreArr != null && scoreArr.length() >= 2) {
                listOf(scoreArr.getInt(0), scoreArr.getInt(1))
            } else {
                listOf(0, 5)
            }
            val rules = LeaderRules(
                quietStart = rulesObj.optString("quiet_start", "23:00"),
                quietLastStart = rulesObj.optString("quiet_last_start", "23:59"),
                quietFinishBy = rulesObj.optString("quiet_finish_by", "00:30"),
                cleanlinessMaxHours = rulesObj.optInt("cleanliness_max_hours", 3),
                cleanlinessScore = scores,
                commentMax = rulesObj.optInt("comment_max", 500)
            )

            return LeaderProfile(
                user = user,
                position = root.optString("position", "elder"),
                positionLabel = root.optString("position_label", "Староста"),
                floor = root.optInt("floor", 3),
                rooms = roomsList,
                timezone = root.optString("timezone", "Europe/Moscow"),
                serverTime = if (root.isNull("server_time")) null else root.optString("server_time"),
                rules = rules
            )
        }
    }
}

sealed class LeaderAuthResult {
    data class Success(val token: String, val expiresAt: String?, val profile: LeaderProfile) : LeaderAuthResult()
    data class PasswordChangeRequired(val message: String) : LeaderAuthResult()
    data class Error(val code: Int, val message: String, val errors: Map<String, String>? = null) : LeaderAuthResult()
}

sealed class LeaderPingResult {
    data class Success(val serverTime: String, val profile: LeaderProfile) : LeaderPingResult()
    data class Unauthorized(val message: String = "Токен не действует, требуется вход") : LeaderPingResult()
    data class Error(val message: String) : LeaderPingResult()
}

data class InspectionRoomItem(
    val roomId: Long,
    val score: Int,
    val comment: String
)

data class SyncResultItem(
    val eventId: String,
    val status: String, // "accepted", "duplicate", "rejected"
    val inspectionId: Long?,
    val reason: String?,
    val message: String?
)

sealed class LeaderSyncResult {
    data class Success(val results: List<SyncResultItem>) : LeaderSyncResult()
    data class Unauthorized(val message: String = "Токен не действует, требуется вход") : LeaderSyncResult()
    data class Error(val code: Int, val message: String) : LeaderSyncResult()
}
