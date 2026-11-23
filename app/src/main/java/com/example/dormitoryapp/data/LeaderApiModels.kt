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
        get() = listOf(lastName, firstName, middleName)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            .joinToString(" ")
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
            put("last_name", user.lastName.trim())
            put("first_name", user.firstName.trim())
            val mid = user.middleName.trim()
            if (mid.isNotBlank() && !mid.equals("null", ignoreCase = true)) {
                put("middle_name", mid)
            } else {
                put("middle_name", JSONObject.NULL)
            }
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

            fun cleanString(obj: JSONObject, key: String): String {
                if (obj.isNull(key)) return ""
                val value = obj.optString(key, "").trim()
                return if (value.equals("null", ignoreCase = true)) "" else value
            }

            val user = LeaderUser(
                id = userObj.optLong("id", 0),
                lastName = cleanString(userObj, "last_name"),
                firstName = cleanString(userObj, "first_name"),
                middleName = cleanString(userObj, "middle_name")
            )

            val roomsList = mutableListOf<LeaderRoom>()
            val roomsArr = root.optJSONArray("rooms")
                ?: root.optJSONArray("rooms_list")
                ?: userObj.optJSONArray("rooms")
                ?: JSONArray()

            for (i in 0 until roomsArr.length()) {
                val r = roomsArr.optJSONObject(i) ?: continue
                roomsList.add(
                    LeaderRoom(
                        id = r.optLong("id", 0),
                        number = cleanString(r, "number"),
                        active = if (r.isNull("active")) true else r.optBoolean("active", true)
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
                quietStart = cleanString(rulesObj, "quiet_start").ifBlank { "23:00" },
                quietLastStart = cleanString(rulesObj, "quiet_last_start").ifBlank { "23:59" },
                quietFinishBy = cleanString(rulesObj, "quiet_finish_by").ifBlank { "00:30" },
                cleanlinessMaxHours = rulesObj.optInt("cleanliness_max_hours", 3),
                cleanlinessScore = scores,
                commentMax = rulesObj.optInt("comment_max", 500)
            )

            // Гибкий поиск этажа: root.floor, user.floor, номера комнат
            val floorFromRoot = when {
                root.has("floor") && !root.isNull("floor") -> root.optString("floor").toIntOrNull()
                root.has("floor_number") && !root.isNull("floor_number") -> root.optString("floor_number").toIntOrNull()
                else -> null
            }
            val floorFromUser = when {
                userObj.has("floor") && !userObj.isNull("floor") -> userObj.optString("floor").toIntOrNull()
                userObj.has("floor_number") && !userObj.isNull("floor_number") -> userObj.optString("floor_number").toIntOrNull()
                else -> null
            }
            val floorFromRooms = roomsList.firstOrNull { it.roomNumberInt > 0 }?.let { it.roomNumberInt / 100 }
            val resolvedFloor = (floorFromRoot ?: floorFromUser ?: floorFromRooms ?: 3).coerceIn(2, 5)

            // Гибкий поиск должности: root.position, user.position, user.role
            val rawPos = when {
                root.has("position") && !root.isNull("position") -> root.optString("position")
                userObj.has("position") && !userObj.isNull("position") -> userObj.optString("position")
                userObj.has("role") && !userObj.isNull("role") -> userObj.optString("role")
                else -> ""
            }.trim().lowercase()

            val resolvedPosition = when (rawPos) {
                "commander", "floor_commander", "комендант", "командир", "командир этажа" -> "commander"
                "elder", "староста", "староста этажа" -> "elder"
                "initiative", "стажер", "инициативная группа" -> "initiative"
                else -> if (rawPos.isNotBlank()) rawPos else "elder"
            }

            val resolvedPosLabel = cleanString(root, "position_label").ifBlank {
                cleanString(userObj, "position_label").ifBlank {
                    when (resolvedPosition) {
                        "commander" -> "Командир этажа"
                        "initiative" -> "Инициативная группа"
                        else -> "Староста"
                    }
                }
            }

            return LeaderProfile(
                user = user,
                position = resolvedPosition,
                positionLabel = resolvedPosLabel,
                floor = resolvedFloor,
                rooms = roomsList,
                timezone = cleanString(root, "timezone").ifBlank { "Europe/Moscow" },
                serverTime = if (root.isNull("server_time")) null else cleanString(root, "server_time").ifBlank { null },
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
    val comment: String,
    val markedAt: String? = null
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
