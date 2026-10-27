package com.example.dormitoryapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Роли сотрудников в общежитии.
 * Определяют уровень прав и отображаются в журналах проверок.
 */
enum class StaffRole(val title: String, val canEditNorms: Boolean, val canSeeAllFloors: Boolean) {
    DEPUTY_EDUCATOR("Заместитель воспитателя", canEditNorms = true, canSeeAllFloors = true),
    FLOOR_COMMANDER("Командир этажа", canEditNorms = true, canSeeAllFloors = true),
    FLOOR_HEAD("Староста этажа", canEditNorms = false, canSeeAllFloors = false),
    INITIATIVE_GROUP("Инициативная группа (стажер)", canEditNorms = false, canSeeAllFloors = false)
}

/**
 * Режимы отображения и порядка комнат при обходе.
 */
enum class RoomSortMode(val title: String, val chipLabel: String) {
    DEFAULT("Обычный (1–35)", "Обычный"),
    CHESS("Шахматный (зигзаг)", "♟️ Шахматный"),
    CUSTOM("Свой порядок", "⇅ Свой")
}


/**
 * 1. Таблица комнат общежития (dorm_rooms).
 * Хранит номер комнаты (200..534), этаж (2..5), сторону и штатную норму проживающих.
 */
@Entity(tableName = "dorm_rooms")
data class DormRoom(
    @PrimaryKey
    val roomNumber: Int,      // Например: 300, 301 ... 334
    val floor: Int,           // Этаж: 2, 3, 4, 5
    val wing: String,         // "Сторона 00–15" или "Сторона 16–34"
    val capacity: Int         // Штатное кол-во человек (по умолчанию 4 или 6, можно менять на 3, 8 и т.д.)
)

/**
 * 2. Таблица вечернего обхода численности (headcount_records).
 * Ключ: дата + номер комнаты (чтобы за один вечер у каждой комнаты была актуальная запись).
 */
@Entity(tableName = "headcount_records", primaryKeys = ["date", "roomNumber"])
data class HeadcountRecord(
    val date: String,         // Дата обхода в формате ГГГГ-ММ-ДД
    val roomNumber: Int,      // Номер комнаты
    val floor: Int,           // Этаж
    val presentCount: Int,    // Сколько человек фактически ночует сегодня
    val inspectorName: String,// Кто провел обход (Роль / Имя)
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 3. Таблица проверки чистоты комнат (cleanliness_records).
 */
@Entity(tableName = "cleanliness_records", primaryKeys = ["date", "roomNumber"])
data class CleanlinessRecord(
    val date: String,         // Дата проверки
    val roomNumber: Int,      // Номер комнаты
    val floor: Int,           // Этаж
    val status: String,       // "Чисто", "Удовлетворительно", "Замечание"
    val comment: String,      // Примечание (например, "Не вынесен мусор")
    val inspectorName: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 4. Таблица графика и проверки дежурств по кухне (kitchen_duty_records).
 * На каждом этаже (2..5) две кухни: "Северная" и "Южная".
 */
@Entity(tableName = "kitchen_duty_records", primaryKeys = ["date", "floor", "kitchenSide"])
data class KitchenDutyRecord(
    val date: String,         // Дата дежурства
    val floor: Int,           // Этаж (2, 3, 4, 5)
    val kitchenSide: String,  // "Северная" или "Южная"
    val dutyRoomNumber: Int,  // Номер дежурной комнаты (например, 305)
    val status: String,       // "Назначено", "Убрано", "Не убрано"
    val comment: String,      // Замечания по плите/столам/полу
    val inspectorName: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 5. Таблица журнала тишины во время комендантского часа (silence_incidents).
 */
@Entity(tableName = "silence_incidents")
data class SilenceIncidentRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val date: String,          // Дата
    val time: String,          // Время фиксации (например, "23:20")
    val roomNumber: Int,       // Комната-нарушитель
    val floor: Int,            // Этаж
    val violationType: String, // "Шум после отбоя", "Громкая музыка", "Гости из другой комнаты"
    val comment: String,       // Подробности
    val inspectorName: String, // Кто зафиксировал
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 6. Таблица недельного графика дежурств сотрудников по этажу (floor_duty_schedule).
 * floor: этаж (2, 3, 4, 5)
 * dayOfWeek: 1..7 (1 - Понедельник, ..., 7 - Воскресенье)
 */
@Entity(tableName = "floor_duty_schedule", primaryKeys = ["floor", "dayOfWeek"])
data class FloorDutyScheduleRecord(
    val floor: Int,            // Этаж (2, 3, 4, 5)
    val dayOfWeek: Int,        // 1..7 (Пн..Вс)
    val dayName: String,       // "Понедельник", "Вторник", etc.
    val personName: String,    // ФИО дежурного (например, "Кохан К. А.")
    val personRole: String = "", // Должность (например, "Староста")
    val timestamp: Long = System.currentTimeMillis()
)

