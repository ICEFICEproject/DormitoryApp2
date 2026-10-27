package com.example.dormitoryapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Главный класс локальной базы данных Room.
 * Объединяет все 5 таблиц и автоматически заполняет 140 комнат при первом запуске.
 */
@Database(
    entities = [
        DormRoom::class,
        HeadcountRecord::class,
        CleanlinessRecord::class,
        KitchenDutyRecord::class,
        SilenceIncidentRecord::class,
        FloorDutyScheduleRecord::class,
        InspectionEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class DormDatabase : RoomDatabase() {

    abstract fun dormDao(): DormDao

    companion object {
        @Volatile
        private var INSTANCE: DormDatabase? = null

        fun getDatabase(context: Context): DormDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    DormDatabase::class.java,
                    "dormitory_local_db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        /**
         * Автоматическое заполнение базы всеми 140 комнатами (этажи со 2 по 5, комнаты 00..34).
         * Сторона 00–15: норма 4 человека.
         * Сторона 16–34: норма 6 человек.
         */
        suspend fun ensureInitialRoomsPopulated(dao: DormDao) {
            if (dao.getRoomsCount() == 0) {
                val initialRooms = mutableListOf<DormRoom>()
                for (floor in 2..5) {
                    for (numOnFloor in 0..34) {
                        val roomNumber = floor * 100 + numOnFloor
                        val isFirstWing = numOnFloor <= 15
                        val wingName = if (isFirstWing) "Северная (00–15)" else "Южная (16–34)"
                        val defaultCapacity = when {
                            numOnFloor == 34 -> 8
                            isFirstWing -> 4
                            else -> 6
                        }

                        initialRooms.add(
                            DormRoom(
                                roomNumber = roomNumber,
                                floor = floor,
                                wing = wingName,
                                capacity = defaultCapacity
                            )
                        )
                    }
                }
                dao.insertRooms(initialRooms)
            } else {
                // Проверяем крайние 34-е комнаты (234, 334, 434, 534) на случай ранее созданной базы
                for (floor in 2..5) {
                    val room34 = floor * 100 + 34
                    val room = dao.getRoomByNumber(room34)
                    if (room != null && room.capacity < 8) {
                        dao.updateRoom(room.copy(capacity = 8))
                    }
                }
            }
            ensureFloorDutySchedulePopulated(dao)
        }

        private suspend fun ensureFloorDutySchedulePopulated(dao: DormDao) {
            val dayNames = listOf(
                "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота", "Воскресенье"
            )
            for (floor in 2..5) {
                val existing = dao.getFloorDutyScheduleList(floor)
                if (existing.isEmpty()) {
                    val schedule = mutableListOf<FloorDutyScheduleRecord>()
                    for (day in 1..7) {
                        val (person, role) = when {
                            floor == 5 && day == 1 -> "Кохан К. А." to "Староста этажа"
                            floor == 5 && day == 2 -> "Дегтярев К. Р." to "Инициативная группа"
                            floor == 5 && day == 3 -> "Рогов В. О." to "Командир этажа"
                            else -> "" to ""
                        }
                        schedule.add(
                            FloorDutyScheduleRecord(
                                floor = floor,
                                dayOfWeek = day,
                                dayName = dayNames[day - 1],
                                personName = person,
                                personRole = role
                            )
                        )
                    }
                    dao.saveFloorDutySchedule(schedule)
                }
            }
        }
    }
}

