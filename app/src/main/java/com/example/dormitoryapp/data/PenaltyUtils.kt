package com.example.dormitoryapp.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Модель накопительной статистики штрафных баллов по комнате.
 */
data class RoomPenaltyStats(
    val roomNumber: Int,
    val cleanlinessPoints: Int,
    val silencePoints: Int,
    val totalPoints: Int
)

/**
 * Периоды выборки штрафных баллов для экрана сводки.
 */
enum class PenaltyPeriod(val title: String, val shortTitle: String) {
    ALL_TIME("За всё время", "Всё время"),
    CURRENT_MONTH("За этот месяц", "Этот месяц"),
    SELECTED_DATE("За этот день", "Этот день")
}

/**
 * Пресеты для расчёта 15-дневной смены / двух недель дежурства.
 */
enum class ShiftPreset(val title: String) {
    FIRST_HALF("1–15 число"),
    SECOND_HALF("16–30/31 число"),
    LAST_15_DAYS("Последние 15 дней"),
    CUSTOM("Свои даты (до 15 дн.)")
}

/**
 * Детальная статистика комнаты за 15-дневную смену.
 */
data class RoomShiftStat(
    val roomNumber: Int,
    val floor: Int,
    val cleanlinessPoints: Int,
    val silencePoints: Int,
    val silenceIncidentsCount: Int,
    val totalPoints: Int,
    val isExemplary: Boolean
)

/**
 * Полный сводный отчет за 15-дневную смену.
 */
data class ShiftRankingSummary(
    val periodTitle: String,
    val startDate: String,
    val endDate: String,
    val daysCount: Int,
    val filterFloor: Int?,
    val allRoomStats: List<RoomShiftStat>,
    val topCleanlinessProblemRooms: List<RoomShiftStat>,
    val topSilenceViolatorRooms: List<RoomShiftStat>,
    val topOverallPenaltyRooms: List<RoomShiftStat>,
    val exemplaryRooms: List<RoomShiftStat>,
    val totalPenaltyPoints: Int,
    val totalCleanlinessPoints: Int,
    val totalSilencePoints: Int,
    val totalSilenceIncidentsCount: Int
)

object PenaltyUtils {

    /**
     * Конвертация статуса чистоты в штрафные баллы:
     * - "Чисто" -> 0 баллов
     * - "Немного грязно" -> 1 балл
     * - "Умеренно грязно" -> 2 балла
     * - "Грязно" -> 3 балла
     * - "Очень грязно" -> 4 балла
     */
    fun getCleanlinessPoints(status: String?): Int = when (status?.trim()) {
        "Чисто" -> 0
        "Немного грязно" -> 1
        "Умеренно грязно" -> 2
        "Грязно" -> 3
        "Очень грязно" -> 4
        else -> 0
    }

    /**
     * За каждое зафиксированное замечание по тишине комнате начисляется 1 штрафной балл.
     */
    const val SILENCE_INCIDENT_POINTS = 1

    /**
     * Расчёт накопительных штрафных баллов по конкретной комнате за всё время.
     */
    fun calculateRoomStats(
        roomNumber: Int,
        allCleanliness: List<CleanlinessRecord>,
        allSilence: List<SilenceIncidentRecord>
    ): RoomPenaltyStats {
        val clPoints = allCleanliness
            .filter { it.roomNumber == roomNumber }
            .sumOf { getCleanlinessPoints(it.status) }

        val silPoints = allSilence
            .count { it.roomNumber == roomNumber } * SILENCE_INCIDENT_POINTS

        return RoomPenaltyStats(
            roomNumber = roomNumber,
            cleanlinessPoints = clPoints,
            silencePoints = silPoints,
            totalPoints = clPoints + silPoints
        )
    }

    /**
     * Расчёт накопительных штрафных баллов по всем комнатам (Map: roomNumber -> RoomPenaltyStats).
     */
    fun calculateAllRoomsStats(
        rooms: List<DormRoom>,
        allCleanliness: List<CleanlinessRecord>,
        allSilence: List<SilenceIncidentRecord>
    ): Map<Int, RoomPenaltyStats> {
        val clGrouped = allCleanliness.groupBy { it.roomNumber }
        val silGrouped = allSilence.groupBy { it.roomNumber }

        return rooms.associate { room ->
            val clPoints = clGrouped[room.roomNumber]?.sumOf { getCleanlinessPoints(it.status) } ?: 0
            val silPoints = (silGrouped[room.roomNumber]?.size ?: 0) * SILENCE_INCIDENT_POINTS
            room.roomNumber to RoomPenaltyStats(
                roomNumber = room.roomNumber,
                cleanlinessPoints = clPoints,
                silencePoints = silPoints,
                totalPoints = clPoints + silPoints
            )
        }
    }

    /**
     * Расчёт штрафных баллов по комнатам с учётом выбранного временного периода.
     */
    fun calculateStatsForPeriod(
        rooms: List<DormRoom>,
        allCleanliness: List<CleanlinessRecord>,
        allSilence: List<SilenceIncidentRecord>,
        period: PenaltyPeriod,
        selectedDate: String
    ): Map<Int, RoomPenaltyStats> {
        val filteredCleanliness = when (period) {
            PenaltyPeriod.ALL_TIME -> allCleanliness
            PenaltyPeriod.SELECTED_DATE -> allCleanliness.filter { it.date == selectedDate }
            PenaltyPeriod.CURRENT_MONTH -> {
                val prefix = selectedDate.take(7) // например, "2026-09"
                allCleanliness.filter { it.date.startsWith(prefix) }
            }
        }

        val filteredSilence = when (period) {
            PenaltyPeriod.ALL_TIME -> allSilence
            PenaltyPeriod.SELECTED_DATE -> allSilence.filter { it.date == selectedDate }
            PenaltyPeriod.CURRENT_MONTH -> {
                val prefix = selectedDate.take(7)
                allSilence.filter { it.date.startsWith(prefix) }
            }
        }

        return calculateAllRoomsStats(rooms, filteredCleanliness, filteredSilence)
    }

    /**
     * Получить начальную и конечную дату для пресета 15-дневной смены.
     */
    fun getShiftDatesForPreset(
        preset: ShiftPreset,
        refDate: LocalDate = LocalDate.now()
    ): Pair<LocalDate, LocalDate> {
        return when (preset) {
            ShiftPreset.FIRST_HALF -> {
                val start = refDate.withDayOfMonth(1)
                val end = refDate.withDayOfMonth(15)
                start to end
            }
            ShiftPreset.SECOND_HALF -> {
                val start = refDate.withDayOfMonth(16)
                val end = refDate.withDayOfMonth(refDate.lengthOfMonth())
                start to end
            }
            ShiftPreset.LAST_15_DAYS -> {
                val end = refDate
                val start = refDate.minusDays(14) // 14 дней до сегодня + сегодня = 15 дней
                start to end
            }
            ShiftPreset.CUSTOM -> {
                val end = refDate
                val start = refDate.minusDays(14)
                start to end
            }
        }
    }

    /**
     * Валидация выбранного периода смены.
     * Возвращает текст ошибки или null, если период корректен (до 16 дней включительно для месяцев с 31 днем).
     */
    fun validateShiftPeriod(startDate: LocalDate, endDate: LocalDate): String? {
        if (endDate.isBefore(startDate)) {
            return "Дата окончания не может быть раньше даты начала!"
        }
        val days = ChronoUnit.DAYS.between(startDate, endDate) + 1
        if (days > 16) {
            return "Период смены не может превышать 16 дней (сейчас: $days дн.)!"
        }
        return null
    }

    /**
     * Расчёт сводки за 15-дневную смену дежурства:
     * - Рейтинг самых проблемных комнат по чистоте
     * - Рейтинг самых шумных комнат
     * - Анти-Топ общего зачета
     * - Список образцовых комнат (0 штрафов)
     */
    fun calculateShiftSummary(
        rooms: List<DormRoom>,
        allCleanliness: List<CleanlinessRecord>,
        allSilence: List<SilenceIncidentRecord>,
        startDate: String,
        endDate: String,
        filterFloor: Int? = null
    ): ShiftRankingSummary {
        val targetRooms = if (filterFloor != null) {
            rooms.filter { it.floor == filterFloor }
        } else {
            rooms
        }.sortedBy { it.roomNumber }

        val filteredCleanliness = allCleanliness.filter {
            it.date in startDate..endDate && (filterFloor == null || it.floor == filterFloor)
        }

        val filteredSilence = allSilence.filter {
            it.date in startDate..endDate && (filterFloor == null || it.floor == filterFloor)
        }

        val clGrouped = filteredCleanliness.groupBy { it.roomNumber }
        val silGrouped = filteredSilence.groupBy { it.roomNumber }

        val roomStats = targetRooms.map { room ->
            val clPoints = clGrouped[room.roomNumber]?.sumOf { getCleanlinessPoints(it.status) } ?: 0
            val silList = silGrouped[room.roomNumber] ?: emptyList()
            val silCount = silList.size
            val silPoints = silCount * SILENCE_INCIDENT_POINTS
            val total = clPoints + silPoints

            RoomShiftStat(
                roomNumber = room.roomNumber,
                floor = room.floor,
                cleanlinessPoints = clPoints,
                silencePoints = silPoints,
                silenceIncidentsCount = silCount,
                totalPoints = total,
                isExemplary = total == 0
            )
        }

        val startLocal = runCatching { LocalDate.parse(startDate) }.getOrDefault(LocalDate.now())
        val endLocal = runCatching { LocalDate.parse(endDate) }.getOrDefault(LocalDate.now())
        val daysCount = (ChronoUnit.DAYS.between(startLocal, endLocal) + 1).toInt().coerceAtLeast(1)

        val topCleanliness = roomStats
            .filter { it.cleanlinessPoints > 0 }
            .sortedWith(compareByDescending<RoomShiftStat> { it.cleanlinessPoints }.thenBy { it.roomNumber })

        val topSilence = roomStats
            .filter { it.silenceIncidentsCount > 0 }
            .sortedWith(compareByDescending<RoomShiftStat> { it.silenceIncidentsCount }.thenBy { it.roomNumber })

        val topOverall = roomStats
            .filter { it.totalPoints > 0 }
            .sortedWith(compareByDescending<RoomShiftStat> { it.totalPoints }.thenBy { it.roomNumber })

        val exemplary = roomStats
            .filter { it.isExemplary }
            .sortedBy { it.roomNumber }

        val floorLabel = if (filterFloor != null) "$filterFloor этаж" else "Все этажи (2–5)"
        val periodTitle = "Итоги 15-дневной смены ($startDate — $endDate, $floorLabel)"

        return ShiftRankingSummary(
            periodTitle = periodTitle,
            startDate = startDate,
            endDate = endDate,
            daysCount = daysCount,
            filterFloor = filterFloor,
            allRoomStats = roomStats,
            topCleanlinessProblemRooms = topCleanliness,
            topSilenceViolatorRooms = topSilence,
            topOverallPenaltyRooms = topOverall,
            exemplaryRooms = exemplary,
            totalPenaltyPoints = roomStats.sumOf { it.totalPoints },
            totalCleanlinessPoints = roomStats.sumOf { it.cleanlinessPoints },
            totalSilencePoints = roomStats.sumOf { it.silencePoints },
            totalSilenceIncidentsCount = filteredSilence.size
        )
    }
}
