package com.example.dormitoryapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

/**
 * Запись проведённой проверки (чистоты или тишины) для оффлайн-очереди и истории.
 * Соответствует спецификации обмена с сервером leader-app-api.md (POST /inspections/sync).
 */
@Entity(tableName = "leader_inspections")
data class InspectionEntity(
    @PrimaryKey
    val eventId: String,               // UUID, генерируется один раз при завершении
    val type: String,                  // "quiet" | "cleanliness"
    val floor: Int,                    // Номер этажа
    val startedAt: String,             // ISO 8601 со смещением ("2026-10-25T23:05:00+03:00")
    val finishedAt: String,            // ISO 8601 со смещением ("2026-10-25T23:20:00+03:00")
    val roomsJson: String,             // JSON-массив комнат: [{"room_id": 41, "score": 1, "comment": "музыка"}]
    val syncStatus: String = STATUS_PENDING, // PENDING, ACCEPTED, DUPLICATE, REJECTED
    val inspectionId: Long? = null,    // ID принятой проверки из ответа сервера
    val rejectReason: String? = null,  // Код отказа: wrong_floor, quiet_window и т.д.
    val rejectMessage: String? = null, // Текст отказа на русском от сервера
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_DUPLICATE = "DUPLICATE"
        const val STATUS_REJECTED = "REJECTED"

        fun parseRoomsJson(json: String): List<InspectionRoomItem> {
            val list = mutableListOf<InspectionRoomItem>()
            if (json.isBlank()) return list
            try {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        InspectionRoomItem(
                            roomId = obj.optLong("room_id", 0),
                            score = obj.optInt("score", 0),
                            comment = obj.optString("comment", "")
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return list
        }

        fun roomsToJson(rooms: List<InspectionRoomItem>): String {
            val arr = JSONArray()
            for (r in rooms) {
                val obj = JSONObject().apply {
                    put("room_id", r.roomId)
                    put("score", r.score)
                    put("comment", r.comment)
                }
                arr.put(obj)
            }
            return arr.toString()
        }
    }
}
