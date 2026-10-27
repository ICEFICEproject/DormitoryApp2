package com.example.dormitoryapp.data

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Сервис формирования и отправки отчетов руководству.
 * Поддерживает 4 типа отчетов:
 * 1. «Численность» — ночной обход комнат, норма мест, фактически ночующие и отсутствующие.
 * 2. «Чистота» — санитарное состояние всех комнат (включая чистые комнаты!), кухни и рейтинг штрафов.
 * 3. «Тишина» — соблюдение комендантского часа, журнал инцидентов и штрафные баллы.
 * 4. «Сводный» — все 3 раздела вместе в одном документе Excel (.xlsx).
 */
object ReportService {

    // Тестовый email для отправки отчетов
    const val TEST_RECIPIENT_EMAIL = "degtarevkonstantin20@gmail.com"

    enum class ReportType(val title: String, val shortTitle: String, val filePrefix: String) {
        HEADCOUNT("Численность", "Численность", "Chislennost"),
        CLEANLINESS("Чистота", "Чистота", "Chistota"),
        SILENCE("Тишина", "Тишина", "Tishina"),
        ALL("Сводный отчёт", "Сводный", "Svodny")
    }

    enum class ReportRecipient(val title: String, val subtitle: String, val email: String) {
        FLOOR_COMMANDER("Командиру этажа", "Руководитель вашего этажа", TEST_RECIPIENT_EMAIL),
        DEPUTY_EDUCATOR("Заместителю воспитателя", "Администрация общежития", TEST_RECIPIENT_EMAIL),
        EDUCATOR("Воспитателю", "Воспитатель общежития", TEST_RECIPIENT_EMAIL)
    }

    private data class SheetDef(val name: String, val xmlContent: String)

    /**
     * Генерация текста отчета для тела электронного письма.
     */
    fun generateReportText(
        reportType: ReportType = ReportType.ALL,
        date: String,
        floor: Int,
        inspectorName: String,
        inspectorRole: String,
        recipient: ReportRecipient,
        allRooms: List<DormRoom>,
        headcounts: List<HeadcountRecord>,
        cleanliness: List<CleanlinessRecord>,
        allCleanliness: List<CleanlinessRecord> = cleanliness,
        kitchenDuty: List<KitchenDutyRecord>,
        silenceIncidents: List<SilenceIncidentRecord>
    ): String {
        val floorRooms = allRooms.filter { it.floor == floor }.sortedBy { it.roomNumber }
        val floorHeadcounts = headcounts.filter { it.floor == floor }
        val floorCleanliness = cleanliness.filter { it.floor == floor }
        val floorAllCleanliness = allCleanliness.filter { it.floor == floor }
        val floorKitchens = kitchenDuty.filter { it.floor == floor }
        val floorSilence = silenceIncidents.filter { it.floor == floor }

        val totalRooms = floorRooms.size
        val totalCapacity = floorRooms.sumOf { it.capacity }
        val checkedRoomsCount = floorHeadcounts.size
        val totalPresent = floorHeadcounts.sumOf { it.presentCount }
        val totalAbsent = floorHeadcounts.sumOf { hc ->
            val roomCap = floorRooms.find { it.roomNumber == hc.roomNumber }?.capacity ?: 0
            (roomCap - hc.presentCount).coerceAtLeast(0)
        }

        val roomsWithAbsentees = floorHeadcounts.mapNotNull { hc ->
            val room = floorRooms.find { it.roomNumber == hc.roomNumber }
            val cap = room?.capacity ?: 0
            val absent = cap - hc.presentCount
            if (absent > 0) "• комн. ${hc.roomNumber}: ${hc.presentCount} из $cap (отсутствует $absent чел.)" else null
        }

        val cleanCount = floorCleanliness.count { it.status == "Чисто" }
        val remarkRooms = floorCleanliness.filter { it.status != "Чисто" }

        val penaltyStatsMap = PenaltyUtils.calculateAllRoomsStats(floorRooms, floorAllCleanliness, floorSilence)
        val sortedPenalties = penaltyStatsMap.values.sortedByDescending { it.totalPoints }
        val penalizedRooms = sortedPenalties.filter { it.totalPoints > 0 }
        val totalFloorPenaltyPoints = sortedPenalties.sumOf { it.totalPoints }
        val totalFloorCleanlinessPoints = sortedPenalties.sumOf { it.cleanlinessPoints }
        val totalFloorSilencePoints = sortedPenalties.sumOf { it.silencePoints }

        val sb = StringBuilder()
        val titleText = when (reportType) {
            ReportType.HEADCOUNT -> "ОТЧЁТ: ЧИСЛЕННОСТЬ И НОЧНОЙ ОБХОД"
            ReportType.CLEANLINESS -> "ОТЧЁТ: САНИТАРНОЕ СОСТОЯНИЕ (ЧИСТОТА)"
            ReportType.SILENCE -> "ОТЧЁТ: СОБЛЮДЕНИЕ РЕЖИМА ТИШИНЫ"
            ReportType.ALL -> "СВОДНЫЙ ОТЧЁТ ПО ОБХОДУ ОБЩЕЖИТИЯ"
        }

        sb.appendLine(titleText)
        sb.appendLine("════════════════════════════════════════")
        sb.appendLine("📅 Дата: $date")
        sb.appendLine("🏢 Этаж: $floor этаж")
        sb.appendLine("👤 Кому: ${recipient.title}")
        sb.appendLine("✍️ Составил: $inspectorRole $inspectorName")
        sb.appendLine("📎 К письму прикреплена таблица Excel (.xlsx)")
        sb.appendLine("════════════════════════════════════════\n")

        when (reportType) {
            ReportType.HEADCOUNT -> {
                sb.appendLine("1. ИТОГИ НОЧНОГО ОБХОДА")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Всего комнат: $totalRooms (проверено: $checkedRoomsCount)")
                sb.appendLine("• Штатная норма этажа: $totalCapacity чел.")
                sb.appendLine("• Фактически ночует: $totalPresent чел.")
                sb.appendLine("• Отсутствует (ночуют вне общежития): $totalAbsent чел.")
                if (roomsWithAbsentees.isNotEmpty()) {
                    sb.appendLine("\nКомнаты с отсутствующими:")
                    roomsWithAbsentees.forEach { sb.appendLine(it) }
                } else if (checkedRoomsCount == totalRooms) {
                    sb.appendLine("• Все проживающие на месте, отсутствующих нет.")
                }

                sb.appendLine("\nПокомнатный отчёт присутствия ($date):")
                floorRooms.forEach { room ->
                    val hc = floorHeadcounts.find { it.roomNumber == room.roomNumber }
                    if (hc != null) {
                        val present = hc.presentCount
                        val absent = (room.capacity - present).coerceAtLeast(0)
                        val st = if (absent == 0) "Все на месте" else "Отсутствует $absent чел."
                        sb.appendLine("• комн. ${room.roomNumber}: $present из ${room.capacity} ($st)")
                    } else {
                        sb.appendLine("• комн. ${room.roomNumber}: норма ${room.capacity} чел. (Не проверено)")
                    }
                }
            }

            ReportType.CLEANLINESS -> {
                sb.appendLine("1. САНИТАРНОЕ СОСТОЯНИЕ КОМНАТ ($date)")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Всего комнат: $totalRooms")
                sb.appendLine("• Проверено: ${floorCleanliness.size} из $totalRooms")
                sb.appendLine("• Чисто (0 б.): $cleanCount комн.")
                sb.appendLine("• С замечаниями / пересдача: ${remarkRooms.size} комн.")

                // Запись ВСЕХ комнат (чистые комнаты не пропускаются!)
                sb.appendLine("\nПокомнатный отчёт о чистоте (все комнаты этажа):")
                floorRooms.forEach { room ->
                    val rec = floorCleanliness.find { it.roomNumber == room.roomNumber }
                    if (rec != null) {
                        val pts = PenaltyUtils.getCleanlinessPoints(rec.status)
                        val commentPart = if (rec.comment.isNotBlank()) " — ${rec.comment}" else ""
                        val ptsStr = if (pts == 0) "0 б." else "+$pts штр. б."
                        sb.appendLine("• комн. ${room.roomNumber}: ${rec.status} ($ptsStr)$commentPart")
                    } else {
                        sb.appendLine("• комн. ${room.roomNumber}: Не проверена")
                    }
                }

                sb.appendLine("\n2. ДЕЖУРСТВО ПО КУХНЯМ")
                sb.appendLine("────────────────────────────────────────")
                if (floorKitchens.isEmpty()) {
                    sb.appendLine("• Отметки по кухням за этот день отсутствуют.")
                } else {
                    floorKitchens.forEach { k ->
                        val comment = if (k.comment.isNotBlank()) " (${k.comment})" else ""
                        sb.appendLine("• ${k.kitchenSide} кухня: комн. ${k.dutyRoomNumber} — ${k.status}$comment")
                    }
                }

                sb.appendLine("\n3. НАКОПИТЕЛЬНЫЙ РЕЙТИНГ ШТРАФОВ ЗА ЧИСТОТУ (ЗА ВСЁ ВРЕМЯ)")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Всего штрафных баллов за чистоту на этаже: $totalFloorCleanlinessPoints б.")
                val cleanPenalizedRooms = sortedPenalties.filter { it.cleanlinessPoints > 0 }
                sb.appendLine("• Комнат с замечаниями по чистоте: ${cleanPenalizedRooms.size} из $totalRooms")
                sb.appendLine("• Идеально чистых комнат за всё время (0 б.): ${totalRooms - cleanPenalizedRooms.size}")
                if (cleanPenalizedRooms.isNotEmpty()) {
                    sb.appendLine("\nРейтинг комнат по штрафам чистоты:")
                    cleanPenalizedRooms.forEachIndexed { index, stat ->
                        sb.appendLine("${index + 1}. комн. ${stat.roomNumber}: ${stat.cleanlinessPoints} штр. б.")
                    }
                }
            }

            ReportType.SILENCE -> {
                sb.appendLine("1. СОБЛЮДЕНИЕ РЕЖИМА ТИШИНЫ ($date)")
                sb.appendLine("────────────────────────────────────────")
                if (floorSilence.isEmpty()) {
                    sb.appendLine("• Нарушений режима тишины за $date не зафиксировано (0 штр. б.).")
                    sb.appendLine("• Ночь прошла спокойно.")
                } else {
                    sb.appendLine("Зафиксировано нарушений: ${floorSilence.size}")
                    floorSilence.forEach { s ->
                        val comment = if (s.comment.isNotBlank()) " (${s.comment})" else ""
                        sb.appendLine("• ${s.time} | комн. ${s.roomNumber}: ${s.violationType} (+1 штр. б.)$comment")
                    }
                }

                sb.appendLine("\n2. НАКОПИТЕЛЬНЫЙ РЕЙТИНГ ШТРАФОВ ЗА ТИШИНУ (ЗА ВСЁ ВРЕМЯ)")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Всего штрафных баллов за тишину на этаже: $totalFloorSilencePoints б.")
                val silencePenalizedRooms = sortedPenalties.filter { it.silencePoints > 0 }
                sb.appendLine("• Комнат с нарушениями тишины: ${silencePenalizedRooms.size} из $totalRooms")
                sb.appendLine("• Комнат без замечаний по тишине (0 б.): ${totalRooms - silencePenalizedRooms.size}")
                if (silencePenalizedRooms.isNotEmpty()) {
                    sb.appendLine("\nРейтинг комнат по нарушениям тишины:")
                    silencePenalizedRooms.forEachIndexed { index, stat ->
                        sb.appendLine("${index + 1}. комн. ${stat.roomNumber}: ${stat.silencePoints} штр. б.")
                    }
                }
            }

            ReportType.ALL -> {
                // Раздел 1: Численность
                sb.appendLine("1. ЧИСЛЕННОСТЬ И НОЧНОЙ ОБХОД")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Всего комнат: $totalRooms (проверено: $checkedRoomsCount)")
                sb.appendLine("• Штатная норма этажа: $totalCapacity чел.")
                sb.appendLine("• Фактически ночует: $totalPresent чел.")
                sb.appendLine("• Отсутствует (ночуют вне общежития): $totalAbsent чел.")
                if (roomsWithAbsentees.isNotEmpty()) {
                    sb.appendLine("\nКомнаты с отсутствующими:")
                    roomsWithAbsentees.forEach { sb.appendLine(it) }
                } else if (checkedRoomsCount == totalRooms) {
                    sb.appendLine("• Все проживающие на месте, отсутствующих нет.")
                }
                sb.appendLine()

                // Раздел 2: Санитарное состояние (запись всех комнат!)
                sb.appendLine("2. САНИТАРНОЕ СОСТОЯНИЕ (ЧИСТОТА ЗА СЕГОДНЯ)")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Проверено комнат: ${floorCleanliness.size} из $totalRooms")
                sb.appendLine("• Чисто (0 б.): $cleanCount комн.")
                sb.appendLine("• С замечаниями / пересдача: ${remarkRooms.size} комн.")

                sb.appendLine("\nПокомнатный отчёт о чистоте (все комнаты этажа):")
                floorRooms.forEach { room ->
                    val rec = floorCleanliness.find { it.roomNumber == room.roomNumber }
                    if (rec != null) {
                        val pts = PenaltyUtils.getCleanlinessPoints(rec.status)
                        val commentPart = if (rec.comment.isNotBlank()) " — ${rec.comment}" else ""
                        val ptsStr = if (pts == 0) "0 б." else "+$pts штр. б."
                        sb.appendLine("• комн. ${room.roomNumber}: ${rec.status} ($ptsStr)$commentPart")
                    } else {
                        sb.appendLine("• комн. ${room.roomNumber}: Не проверена")
                    }
                }
                sb.appendLine()

                // Раздел 3: Кухни
                sb.appendLine("3. ДЕЖУРСТВО ПО КУХНЯМ")
                sb.appendLine("────────────────────────────────────────")
                if (floorKitchens.isEmpty()) {
                    sb.appendLine("• Отметки по кухням за этот день отсутствуют.")
                } else {
                    floorKitchens.forEach { k ->
                        val comment = if (k.comment.isNotBlank()) " (${k.comment})" else ""
                        sb.appendLine("• ${k.kitchenSide} кухня: комн. ${k.dutyRoomNumber} — ${k.status}$comment")
                    }
                }
                sb.appendLine()

                // Раздел 4: Режим тишины
                sb.appendLine("4. СОБЛЮДЕНИЕ РЕЖИМА ТИШИНЫ")
                sb.appendLine("────────────────────────────────────────")
                if (floorSilence.isEmpty()) {
                    sb.appendLine("• Нарушений режима тишины не зафиксировано (0 штр. б.).")
                } else {
                    sb.appendLine("Зафиксировано нарушений: ${floorSilence.size}")
                    floorSilence.forEach { s ->
                        val comment = if (s.comment.isNotBlank()) " (${s.comment})" else ""
                        sb.appendLine("• ${s.time} | комн. ${s.roomNumber}: ${s.violationType} (+1 штр. б.)$comment")
                    }
                }
                sb.appendLine()

                // Раздел 5: Накопительный рейтинг
                sb.appendLine("5. НАКОПИТЕЛЬНЫЙ РЕЙТИНГ ШТРАФОВ (ЗА ВСЁ ВРЕМЯ)")
                sb.appendLine("────────────────────────────────────────")
                sb.appendLine("• Сумма штрафных баллов на этаже: $totalFloorPenaltyPoints б.")
                sb.appendLine("  (за чистоту: $totalFloorCleanlinessPoints б., за режим тишины: $totalFloorSilencePoints б.)")
                sb.appendLine("• Образцовых комнат без замечаний (0 б.): ${totalRooms - penalizedRooms.size} из $totalRooms")
                sb.appendLine("• Комнат с накопленными штрафами: ${penalizedRooms.size}")
                if (penalizedRooms.isNotEmpty()) {
                    sb.appendLine("\nРейтинг комнат по штрафам (от худших к лучшим):")
                    penalizedRooms.forEachIndexed { index, stat ->
                        sb.appendLine("${index + 1}. комн. ${stat.roomNumber}: ${stat.totalPoints} штр. б. (чистота: ${stat.cleanlinessPoints} б., тишина: ${stat.silencePoints} б.)")
                    }
                } else {
                    sb.appendLine("• Все комнаты этажа имеют идеальный показатель (0 штрафных баллов).")
                }
            }
        }

        sb.appendLine("\n════════════════════════════════════════")
        sb.appendLine("Сформировано автоматически через приложение DormitoryApp")

        return sb.toString()
    }

    /**
     * Создает полноценный файл Excel (.xlsx) с листами, соответствующими выбранному типу отчета.
     */
    fun generateExcelReport(
        context: Context,
        reportType: ReportType = ReportType.ALL,
        date: String,
        floor: Int,
        inspectorName: String,
        inspectorRole: String,
        recipient: ReportRecipient,
        allRooms: List<DormRoom>,
        headcounts: List<HeadcountRecord>,
        cleanliness: List<CleanlinessRecord>,
        allCleanliness: List<CleanlinessRecord> = cleanliness,
        kitchenDuty: List<KitchenDutyRecord>,
        silenceIncidents: List<SilenceIncidentRecord>
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        // Очищаем старые временные отчеты (старше 24 часов)
        reportsDir.listFiles()?.forEach { oldFile ->
            if (System.currentTimeMillis() - oldFile.lastModified() > 24 * 60 * 60 * 1000) {
                oldFile.delete()
            }
        }

        val fileName = "Otchet_${reportType.filePrefix}_Etazh${floor}_${date}.xlsx"
        val file = File(reportsDir, fileName)

        val floorRooms = allRooms.filter { it.floor == floor }.sortedBy { it.roomNumber }
        val floorHeadcounts = headcounts.filter { it.floor == floor }.associateBy { it.roomNumber }
        val floorCleanliness = cleanliness.filter { it.floor == floor }.associateBy { it.roomNumber }
        val floorAllCleanliness = allCleanliness.filter { it.floor == floor }
        val floorKitchens = kitchenDuty.filter { it.floor == floor }
        val floorSilence = silenceIncidents.filter { it.floor == floor }.sortedBy { it.time }

        val penaltyStatsMap = PenaltyUtils.calculateAllRoomsStats(floorRooms, floorAllCleanliness, floorSilence)

        val totalRooms = floorRooms.size
        val totalCapacity = floorRooms.sumOf { it.capacity }
        val checkedRoomsCount = floorRooms.count { floorHeadcounts.containsKey(it.roomNumber) }
        val totalPresent = floorRooms.sumOf { floorHeadcounts[it.roomNumber]?.presentCount ?: 0 }
        val totalAbsent = floorRooms.sumOf { room ->
            val hc = floorHeadcounts[room.roomNumber]
            if (hc != null) (room.capacity - hc.presentCount).coerceAtLeast(0) else 0
        }

        val cleanCount = floorRooms.count { floorCleanliness[it.roomNumber]?.status == "Чисто" }
        val remarkCount = floorRooms.count {
            val st = floorCleanliness[it.roomNumber]?.status
            st != null && st != "Чисто"
        }

        // Подготовка листов в зависимости от выбранного типа отчета
        val sheet1Xml by lazy {
            buildSheet1Xml(
                date = date,
                floor = floor,
                recipient = recipient,
                inspectorName = inspectorName,
                inspectorRole = inspectorRole,
                rooms = floorRooms,
                headcounts = floorHeadcounts,
                totalRooms = totalRooms,
                totalCapacity = totalCapacity,
                checkedRoomsCount = checkedRoomsCount,
                totalPresent = totalPresent,
                totalAbsent = totalAbsent
            )
        }

        val sheet2Xml by lazy {
            buildSheet2Xml(
                date = date,
                floor = floor,
                recipient = recipient,
                inspectorName = inspectorName,
                inspectorRole = inspectorRole,
                rooms = floorRooms,
                cleanliness = floorCleanliness,
                penaltyStats = penaltyStatsMap,
                totalRooms = totalRooms,
                cleanCount = cleanCount,
                remarkCount = remarkCount
            )
        }

        val sheet3Xml by lazy {
            buildSheet3Xml(
                date = date,
                floor = floor,
                recipient = recipient,
                inspectorName = inspectorName,
                inspectorRole = inspectorRole,
                kitchens = floorKitchens,
                silenceIncidents = floorSilence
            )
        }

        val sheetKitchenOnlyXml by lazy {
            buildKitchenOnlySheetXml(
                date = date,
                floor = floor,
                recipient = recipient,
                inspectorName = inspectorName,
                inspectorRole = inspectorRole,
                kitchens = floorKitchens
            )
        }

        val sheetSilenceOnlyXml by lazy {
            buildSilenceOnlySheetXml(
                date = date,
                floor = floor,
                recipient = recipient,
                inspectorName = inspectorName,
                inspectorRole = inspectorRole,
                silenceIncidents = floorSilence,
                rooms = floorRooms,
                penaltyStats = penaltyStatsMap
            )
        }

        val sheets = when (reportType) {
            ReportType.HEADCOUNT -> listOf(
                SheetDef("Численность", sheet1Xml)
            )
            ReportType.CLEANLINESS -> listOf(
                SheetDef("Чистота комнат", sheet2Xml),
                SheetDef("Кухни этажа", sheetKitchenOnlyXml)
            )
            ReportType.SILENCE -> listOf(
                SheetDef("Режим тишины", sheetSilenceOnlyXml)
            )
            ReportType.ALL -> listOf(
                SheetDef("Численность", sheet1Xml),
                SheetDef("Чистота комнат", sheet2Xml),
                SheetDef("Кухни и Тишина", sheet3Xml)
            )
        }

        writeZipWorkbook(file, sheets)
        return file
    }

    /**
     * Создание ZIP-пакета OpenXML (.xlsx) с заданными листами и стилями.
     */
    private fun writeZipWorkbook(file: File, sheets: List<SheetDef>) {
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            fun addZipEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 1. [Content_Types].xml
            val contentTypesXml = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                appendLine("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
                appendLine("""  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
                appendLine("""  <Default Extension="xml" ContentType="application/xml"/>""")
                appendLine("""  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
                appendLine("""  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
                for (i in 1..sheets.size) {
                    appendLine("""  <Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
                }
                appendLine("""</Types>""")
            }
            addZipEntry("[Content_Types].xml", contentTypesXml)

            // 2. _rels/.rels
            addZipEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            // 3. xl/_rels/workbook.xml.rels
            val workbookRelsXml = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                appendLine("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
                for (i in 1..sheets.size) {
                    appendLine("""  <Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
                }
                val stylesRId = sheets.size + 1
                appendLine("""  <Relationship Id="rId$stylesRId" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
                appendLine("""</Relationships>""")
            }
            addZipEntry("xl/_rels/workbook.xml.rels", workbookRelsXml)

            // 4. xl/workbook.xml
            val workbookXml = buildString {
                appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
                appendLine("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
                appendLine("""  <sheets>""")
                for (i in 1..sheets.size) {
                    val s = sheets[i - 1]
                    appendLine("""    <sheet name="${escapeXml(s.name)}" sheetId="$i" r:id="rId$i"/>""")
                }
                appendLine("""  </sheets>""")
                appendLine("""</workbook>""")
            }
            addZipEntry("xl/workbook.xml", workbookXml)

            // 5. xl/styles.xml
            addZipEntry(
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="4">
    <font><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><name val="Calibri"/></font>
    <font><b/><sz val="14"/><color rgb="FF1F497D"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF9C0006"/><name val="Calibri"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFD9E1F2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF2F2F2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFC7CE"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFC6EFCE"/></patternFill></fill>
  </fills>
  <borders count="2">
    <border><left/><right/><top/><bottom/><diagonal/></border>
    <border>
      <left style="thin"><color rgb="FFB0B0B0"/></left>
      <right style="thin"><color rgb="FFB0B0B0"/></right>
      <top style="thin"><color rgb="FFB0B0B0"/></top>
      <bottom style="thin"><color rgb="FFB0B0B0"/></bottom>
      <diagonal/>
    </border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="9">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="2" fillId="0" borderId="0" xfId="0" applyFont="1"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
      <alignment horizontal="center" vertical="center" wrapText="1"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1">
      <alignment vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="0" borderId="1" xfId="0" applyFont="1" applyBorder="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="3" fillId="4" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="0" fillId="5" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
      <alignment horizontal="left" vertical="center"/>
    </xf>
    <xf numFmtId="0" fontId="1" fillId="3" borderId="1" xfId="0" applyFont="1" applyFill="1" applyBorder="1">
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>"""
            )

            // Запись листов
            for (i in 1..sheets.size) {
                addZipEntry("xl/worksheets/sheet$i.xml", sheets[i - 1].xmlContent)
            }
        }
    }

    /**
     * Формирование текста отчета за 15-дневную смену для отправки сообщением/почтой.
     */
    fun generateShift15DaysReportText(
        summary: ShiftRankingSummary,
        inspectorName: String,
        inspectorRole: String,
        recipient: ReportRecipient
    ): String {
        val sb = StringBuilder()
        val floorLabel = if (summary.filterFloor != null) "${summary.filterFloor} этаж" else "Всё общежитие (2–5 этажи)"

        sb.appendLine("ИТОГИ 15-ДНЕВНОЙ СМЕНЫ ДЕЖУРСТВА")
        sb.appendLine("════════════════════════════════════════")
        sb.appendLine("📅 Период смены: ${summary.startDate} — ${summary.endDate} (${summary.daysCount} дн.)")
        sb.appendLine("🏢 Охват: $floorLabel")
        sb.appendLine("👤 Кому: ${recipient.title}")
        sb.appendLine("✍️ Составил: $inspectorRole $inspectorName")
        sb.appendLine("📎 К сообщению прикреплена таблица Excel (.xlsx)")
        sb.appendLine("════════════════════════════════════════\n")

        sb.appendLine("1. ОБЩАЯ СВОДКА ДИСЦИПЛИНЫ ЗА СМЕНУ")
        sb.appendLine("────────────────────────────────────────")
        sb.appendLine("• Всего комнат на контроле: ${summary.allRoomStats.size}")
        sb.appendLine("• Образцовых комнат (0 замечаний за смену): ${summary.exemplaryRooms.size}")
        sb.appendLine("• Комнат со штрафами: ${summary.topOverallPenaltyRooms.size}")
        sb.appendLine("• Сумма штрафных баллов за смену: ${summary.totalPenaltyPoints} б.")
        sb.appendLine("  (за санитарное состояние: ${summary.totalCleanlinessPoints} б., за режим тишины: ${summary.totalSilencePoints} б.)")
        sb.appendLine("• Зафиксировано инцидентов тишины после отбоя: ${summary.totalSilenceIncidentsCount}")
        sb.appendLine()

        sb.appendLine("2. АНТИРЕЙТИНГ: САМЫЕ ПРОБЛЕМНЫЕ КОМНАТЫ ПО ЧИСТОТЕ")
        sb.appendLine("────────────────────────────────────────")
        if (summary.topCleanlinessProblemRooms.isEmpty()) {
            sb.appendLine("• За смену нарушений чистоты не зафиксировано! Во всех проверенных комнатах порядок.")
        } else {
            summary.topCleanlinessProblemRooms.take(5).forEachIndexed { i, stat ->
                val badge = if (i == 0) " 🚨 [Худшая по чистоте]" else ""
                sb.appendLine("${i + 1}. комн. ${stat.roomNumber} (${stat.floor} эт.): ${stat.cleanlinessPoints} штр. б.$badge")
            }
        }
        sb.appendLine()

        sb.appendLine("3. АНТИРЕЙТИНГ: САМЫЕ ШУМНЫЕ КОМНАТЫ (НАРУШИТЕЛИ ТИШИНЫ)")
        sb.appendLine("────────────────────────────────────────")
        if (summary.topSilenceViolatorRooms.isEmpty()) {
            sb.appendLine("• За смену нарушений тишины после отбоя не зафиксировано! Ночи прошли спокойно.")
        } else {
            summary.topSilenceViolatorRooms.take(5).forEachIndexed { i, stat ->
                val badge = if (i == 0) " 📢 [Самая громкая комната]" else ""
                sb.appendLine("${i + 1}. комн. ${stat.roomNumber} (${stat.floor} эт.): ${stat.silenceIncidentsCount} замеч. (+${stat.silencePoints} штр. б.)$badge")
            }
        }
        sb.appendLine()

        sb.appendLine("4. АНТИ-ТОП ОБЩЕГО ЗАЧЁТА ДИСЦИПЛИНЫ")
        sb.appendLine("────────────────────────────────────────")
        if (summary.topOverallPenaltyRooms.isEmpty()) {
            sb.appendLine("• Все комнаты имеют идеальную дисциплину (0 штрафных баллов за 15 дней).")
        } else {
            summary.topOverallPenaltyRooms.take(5).forEachIndexed { i, stat ->
                sb.appendLine("${i + 1}. комн. ${stat.roomNumber} (${stat.floor} эт.): ${stat.totalPoints} штр. б. (чистота: ${stat.cleanlinessPoints} б., тишина: ${stat.silencePoints} б.)")
            }
        }
        sb.appendLine()

        sb.appendLine("5. ОБРАЗЦОВЫЕ КОМНАТЫ (0 ШТРАФНЫХ БАЛЛОВ ЗА ВСЮ СМЕНУ)")
        sb.appendLine("────────────────────────────────────────")
        if (summary.exemplaryRooms.isEmpty()) {
            sb.appendLine("• К сожалению, ни одна комната не смогла пройти смену без замечаний.")
        } else {
            val listStr = summary.exemplaryRooms.joinToString(", ") { "комн. ${it.roomNumber}" }
            sb.appendLine("👑 Всего ${summary.exemplaryRooms.size} комнат: $listStr")
        }

        sb.appendLine("\n════════════════════════════════════════")
        sb.appendLine("Сформировано автоматически через приложение DormitoryApp")

        return sb.toString()
    }

    /**
     * Создает официальный Excel-отчёт (.xlsx) по итогам 15-дневной смены с 3 листами:
     * 1. Итоги смены (Антирейтинг чистоты, тишины, общий зачет, образцовые комнаты, сводная ведомость)
     * 2. Журнал тишины (все зафиксированные инциденты за период смены)
     * 3. Ведомость чистоты (все проверки санитарного состояния комнат за период смены)
     */
    fun generateShift15DaysExcelReport(
        context: Context,
        summary: ShiftRankingSummary,
        inspectorName: String,
        inspectorRole: String,
        recipient: ReportRecipient,
        allCleanliness: List<CleanlinessRecord>,
        allSilence: List<SilenceIncidentRecord>
    ): File {
        val reportsDir = File(context.cacheDir, "reports")
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }

        val floorSuffix = if (summary.filterFloor != null) "Etazh${summary.filterFloor}" else "VseEtazhi"
        val fileName = "Otchet_Smena_15Dney_${floorSuffix}_${summary.startDate}_${summary.endDate}.xlsx"
        val file = File(reportsDir, fileName)

        val filteredCleanliness = allCleanliness.filter {
            it.date in summary.startDate..summary.endDate &&
                    (summary.filterFloor == null || it.floor == summary.filterFloor)
        }.sortedWith(compareByDescending<CleanlinessRecord> { it.date }.thenBy { it.roomNumber })

        val filteredSilence = allSilence.filter {
            it.date in summary.startDate..summary.endDate &&
                    (summary.filterFloor == null || it.floor == summary.filterFloor)
        }.sortedWith(compareByDescending<SilenceIncidentRecord> { it.date }.thenBy { it.time })

        val sheetSummaryXml = buildShiftSummarySheetXml(
            summary = summary,
            recipient = recipient,
            inspectorName = inspectorName,
            inspectorRole = inspectorRole
        )

        val sheetSilenceXml = buildShiftSilenceSheetXml(
            summary = summary,
            silenceIncidents = filteredSilence,
            recipient = recipient,
            inspectorName = inspectorName,
            inspectorRole = inspectorRole
        )

        val sheetCleanlinessXml = buildShiftCleanlinessSheetXml(
            summary = summary,
            cleanlinessRecords = filteredCleanliness,
            recipient = recipient,
            inspectorName = inspectorName,
            inspectorRole = inspectorRole
        )

        val sheets = listOf(
            SheetDef("Итоги смены", sheetSummaryXml),
            SheetDef("Журнал тишины", sheetSilenceXml),
            SheetDef("Ведомость чистоты", sheetCleanlinessXml)
        )

        writeZipWorkbook(file, sheets)
        return file
    }

    private fun escapeXml(text: String): String {
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun cText(ref: String, text: String, s: Int = 0): String {
        return """<c r="$ref" s="$s" t="inlineStr"><is><t>${escapeXml(text)}</t></is></c>"""
    }

    private fun cNum(ref: String, num: Number, s: Int = 4): String {
        return """<c r="$ref" s="$s"><v>$num</v></c>"""
    }

    /**
     * Лист 1: Численность и ночной обход
     */
    private fun buildSheet1Xml(
        date: String,
        floor: Int,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String,
        rooms: List<DormRoom>,
        headcounts: Map<Int, HeadcountRecord>,
        totalRooms: Int,
        totalCapacity: Int,
        checkedRoomsCount: Int,
        totalPresent: Int,
        totalAbsent: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="22" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="26" customWidth="1"/>""")
        sb.appendLine("""    <col min="7" max="7" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "СВОДНЫЙ ОТЧЁТ: НОЧНОЙ ОБХОД И ЧИСЛЕННОСТЬ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Дата: $date | Этаж: $floor этаж | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Сводка: Всего комнат: $totalRooms (проверено: $checkedRoomsCount) | Норма мест: $totalCapacity чел. | Ночует факт: $totalPresent чел. | Отсутствует: $totalAbsent чел.", 0)}</row>""")
        rowIdx++
        rowIdx++

        val hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("B$hRow", "Крыло (Секция)", 2))
        sb.appendLine("      " + cText("C$hRow", "Норма (чел.)", 2))
        sb.appendLine("      " + cText("D$hRow", "Ночует (факт)", 2))
        sb.appendLine("      " + cText("E$hRow", "Отсутствует", 2))
        sb.appendLine("      " + cText("F$hRow", "Статус присутствия", 2))
        sb.appendLine("      " + cText("G$hRow", "Кто проверил", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        for (room in rooms) {
            val r = rowIdx
            val hc = headcounts[room.roomNumber]

            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cNum("A$r", room.roomNumber, 4))
            sb.appendLine("      " + cText("B$r", room.wing, 3))
            sb.appendLine("      " + cNum("C$r", room.capacity, 4))

            if (hc != null) {
                val present = hc.presentCount
                val absent = (room.capacity - present).coerceAtLeast(0)
                val statusText = if (absent == 0) "Все на месте" else "Отсутствует $absent чел."
                val statusStyle = if (absent == 0) 6 else 5
                val absentStyle = if (absent == 0) 4 else 5

                sb.appendLine("      " + cNum("D$r", present, 4))
                sb.appendLine("      " + cNum("E$r", absent, absentStyle))
                sb.appendLine("      " + cText("F$r", statusText, statusStyle))
                sb.appendLine("      " + cText("G$r", hc.inspectorName, 3))
            } else {
                sb.appendLine("      " + cText("D$r", "—", 4))
                sb.appendLine("      " + cText("E$r", "—", 4))
                sb.appendLine("      " + cText("F$r", "Не проверено", 3))
                sb.appendLine("      " + cText("G$r", "—", 4))
            }

            sb.appendLine("""    </row>""")
            rowIdx++
        }

        val totR = rowIdx
        val totalStatusText = if (totalAbsent == 0) "Все проживающие на месте" else "Всего отсутствуют: $totalAbsent чел."
        val totalStatusStyle = if (totalAbsent == 0) 6 else 5

        sb.appendLine("""    <row r="$totR">""")
        sb.appendLine("      " + cText("A$totR", "ИТОГО", 7))
        sb.appendLine("      " + cText("B$totR", "", 7))
        sb.appendLine("      " + cNum("C$totR", totalCapacity, 8))
        sb.appendLine("      " + cNum("D$totR", totalPresent, 8))
        sb.appendLine("      " + cNum("E$totR", totalAbsent, 8))
        sb.appendLine("      " + cText("F$totR", totalStatusText, totalStatusStyle))
        sb.appendLine("      " + cText("G$totR", "", 8))
        sb.appendLine("""    </row>""")

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист 2: Чистота комнат (все комнаты по порядку, оценки, баллы за всё время и рейтинг)
     */
    private fun buildSheet2Xml(
        date: String,
        floor: Int,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String,
        rooms: List<DormRoom>,
        cleanliness: Map<Int, CleanlinessRecord>,
        penaltyStats: Map<Int, RoomPenaltyStats>,
        totalRooms: Int,
        cleanCount: Int,
        remarkCount: Int
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="12" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="20" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="16" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="34" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="22" customWidth="1"/>""")
        sb.appendLine("""    <col min="7" max="7" width="22" customWidth="1"/>""")
        sb.appendLine("""    <col min="8" max="8" width="26" customWidth="1"/>""")
        sb.appendLine("""    <col min="9" max="9" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "СВОДНЫЙ ОТЧЁТ: САНИТАРНОЕ СОСТОЯНИЕ И НАКОПИТЕЛЬНЫЕ ШТРАФЫ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Дата: $date | Этаж: $floor этаж | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Сводка за день: Всего комнат: $totalRooms | Проверено: ${cleanliness.size} | Чисто: $cleanCount комн. | С замечаниями: $remarkCount комн.", 0)}</row>""")
        rowIdx++
        rowIdx++

        val hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("B$hRow", "Крыло", 2))
        sb.appendLine("      " + cText("C$hRow", "Оценка (день)", 2))
        sb.appendLine("      " + cText("D$hRow", "Штраф (день)", 2))
        sb.appendLine("      " + cText("E$hRow", "Замечания / Комментарий", 2))
        sb.appendLine("      " + cText("F$hRow", "Чистота (за всё время)", 2))
        sb.appendLine("      " + cText("G$hRow", "Тишина (за всё время)", 2))
        sb.appendLine("      " + cText("H$hRow", "ИТОГО ШТРАФОВ (ВСЁ ВРЕМЯ)", 2))
        sb.appendLine("      " + cText("I$hRow", "Кто проверил", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        for (room in rooms) {
            val r = rowIdx
            val cl = cleanliness[room.roomNumber]
            val stats = penaltyStats[room.roomNumber] ?: RoomPenaltyStats(room.roomNumber, 0, 0, 0)
            val dayPts = if (cl != null) PenaltyUtils.getCleanlinessPoints(cl.status) else 0

            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cNum("A$r", room.roomNumber, 4))
            sb.appendLine("      " + cText("B$r", room.wing, 3))

            if (cl != null) {
                val isClean = cl.status == "Чисто"
                val statusStyle = if (isClean) 6 else 5
                val dayPtsStyle = if (dayPts == 0) 6 else 5
                sb.appendLine("      " + cText("C$r", cl.status, statusStyle))
                sb.appendLine("      " + cText("D$r", "+$dayPts б.", dayPtsStyle))
                sb.appendLine("      " + cText("E$r", cl.comment, 3))
            } else {
                sb.appendLine("      " + cText("C$r", "Не проверено", 3))
                sb.appendLine("      " + cText("D$r", "—", 4))
                sb.appendLine("      " + cText("E$r", "", 3))
            }

            val cPtsStyle = if (stats.cleanlinessPoints == 0) 6 else 4
            val sPtsStyle = if (stats.silencePoints == 0) 6 else 4
            val totStyle = if (stats.totalPoints == 0) 6 else 5

            sb.appendLine("      " + cText("F$r", "${stats.cleanlinessPoints} б.", cPtsStyle))
            sb.appendLine("      " + cText("G$r", "${stats.silencePoints} б.", sPtsStyle))
            sb.appendLine("      " + cText("H$r", "${stats.totalPoints} штр. б.", totStyle))
            sb.appendLine("      " + cText("I$r", cl?.inspectorName ?: "—", if (cl != null) 3 else 4))
            sb.appendLine("""    </row>""")
            rowIdx++
        }

        rowIdx++
        rowIdx++

        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "СВОДНЫЙ НАКОПИТЕЛЬНЫЙ РЕЙТИНГ ШТРАФОВ (ЗА ВСЁ ВРЕМЯ)", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Шкала: Чисто (0), Немного грязно (1), Умеренно грязно (2), Грязно (3), Очень грязно (4). Нарушение тишины: 1 штрафной балл.", 0)}</row>""")
        rowIdx++

        val rhRow = rowIdx
        sb.appendLine("""    <row r="$rhRow">""")
        sb.appendLine("      " + cText("A$rhRow", "Место", 2))
        sb.appendLine("      " + cText("B$rhRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$rhRow", "Крыло", 2))
        sb.appendLine("      " + cText("D$rhRow", "Штрафы чистоты", 2))
        sb.appendLine("      " + cText("E$rhRow", "Штрафы тишины", 2))
        sb.appendLine("      " + cText("F$rhRow", "ВСЕГО ШТРАФОВ", 2))
        sb.appendLine("      " + cText("G$rhRow", "Дисциплинарно-санитарный статус", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        val sortedStats = rooms.map { room ->
            room to (penaltyStats[room.roomNumber] ?: RoomPenaltyStats(room.roomNumber, 0, 0, 0))
        }.sortedByDescending { it.second.totalPoints }

        var place = 1
        for ((room, stat) in sortedStats) {
            val r = rowIdx
            val statusText = when {
                stat.totalPoints == 0 -> "Образцовая комната (0 нарушений)"
                stat.totalPoints <= 2 -> "Удовлетворительно"
                stat.totalPoints <= 5 -> "Внимание: частые замечания"
                else -> "КРИТИЧЕСКИЙ УРОВЕНЬ ШТРАФОВ"
            }
            val statStyle = when {
                stat.totalPoints == 0 -> 6
                stat.totalPoints <= 2 -> 4
                else -> 5
            }

            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "#$place", 4))
            sb.appendLine("      " + cNum("B$r", room.roomNumber, 4))
            sb.appendLine("      " + cText("C$r", room.wing, 3))
            sb.appendLine("      " + cText("D$r", "${stat.cleanlinessPoints} б.", if (stat.cleanlinessPoints == 0) 6 else 4))
            sb.appendLine("      " + cText("E$r", "${stat.silencePoints} б.", if (stat.silencePoints == 0) 6 else 4))
            sb.appendLine("      " + cText("F$r", "${stat.totalPoints} штр. б.", statStyle))
            sb.appendLine("      " + cText("G$r", statusText, statStyle))
            sb.appendLine("""    </row>""")
            place++
            rowIdx++
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист для отдельного отчета «Кухни этажа»
     */
    private fun buildKitchenOnlySheetXml(
        date: String,
        floor: Int,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String,
        kitchens: List<KitchenDutyRecord>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="24" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="22" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="22" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="38" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "СВОДНЫЙ ОТЧЁТ: ДЕЖУРСТВО ПО КУХНЯМ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Дата: $date | Этаж: $floor этаж | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        rowIdx++

        val kHeadRow = rowIdx
        sb.appendLine("""    <row r="$kHeadRow">""")
        sb.appendLine("      " + cText("A$kHeadRow", "Кухня / Обязанность", 2))
        sb.appendLine("      " + cText("B$kHeadRow", "Дежурная комната", 2))
        sb.appendLine("      " + cText("C$kHeadRow", "Статус уборки", 2))
        sb.appendLine("      " + cText("D$kHeadRow", "Замечания / Состояние", 2))
        sb.appendLine("      " + cText("E$kHeadRow", "Кто проверил", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (kitchens.isEmpty()) {
            val templateSides = listOf(
                "Северная (мытьё)",
                "Северная (мусор)",
                "Южная (мытьё)",
                "Южная (мусор)"
            )
            for (side in templateSides) {
                val r = rowIdx
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", side, 3))
                sb.appendLine("      " + cText("B$r", "Не назначено", 4))
                sb.appendLine("      " + cText("C$r", "Нет данных", 4))
                sb.appendLine("      " + cText("D$r", "", 3))
                sb.appendLine("      " + cText("E$r", "—", 4))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        } else {
            for (k in kitchens) {
                val r = rowIdx
                val isClean = k.status == "Убрано"
                val statusStyle = if (isClean) 6 else 5
                val sideTitle = if (k.kitchenSide.contains("кухня", ignoreCase = true)) k.kitchenSide else "${k.kitchenSide}"
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", sideTitle, 3))
                sb.appendLine("      " + cText("B$r", "комн. ${k.dutyRoomNumber}", 4))
                sb.appendLine("      " + cText("C$r", k.status, statusStyle))
                sb.appendLine("      " + cText("D$r", k.comment, 3))
                sb.appendLine("      " + cText("E$r", k.inspectorName, 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист для отдельного отчета «Режим тишины»
     */
    private fun buildSilenceOnlySheetXml(
        date: String,
        floor: Int,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String,
        silenceIncidents: List<SilenceIncidentRecord>,
        rooms: List<DormRoom>,
        penaltyStats: Map<Int, RoomPenaltyStats>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="24" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="38" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "СВОДНЫЙ ОТЧЁТ: СОБЛЮДЕНИЕ РЕЖИМА ТИШИНЫ (КОМЕНДАНТСКИЙ ЧАС)", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Дата: $date | Этаж: $floor этаж | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        rowIdx++

        val sHeadRow = rowIdx
        sb.appendLine("""    <row r="$sHeadRow">""")
        sb.appendLine("      " + cText("A$sHeadRow", "Время", 2))
        sb.appendLine("      " + cText("B$sHeadRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$sHeadRow", "Вид нарушения", 2))
        sb.appendLine("      " + cText("D$sHeadRow", "Штрафные баллы", 2))
        sb.appendLine("      " + cText("E$sHeadRow", "Подробности / Принятые меры", 2))
        sb.appendLine("      " + cText("F$sHeadRow", "Кто зафиксировал", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (silenceIncidents.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "Нарушений режима тишины не зафиксировано", 6))
            sb.appendLine("      " + cText("D$r", "0 б.", 6))
            sb.appendLine("      " + cText("E$r", "Ночь прошла спокойно", 3))
            sb.appendLine("      " + cText("F$r", "—", 4))
            sb.appendLine("""    </row>""")
            rowIdx++
        } else {
            for (s in silenceIncidents) {
                val r = rowIdx
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", s.time, 4))
                sb.appendLine("      " + cNum("B$r", s.roomNumber, 4))
                sb.appendLine("      " + cText("C$r", s.violationType, 5))
                sb.appendLine("      " + cText("D$r", "+1 штр. балл", 5))
                sb.appendLine("      " + cText("E$r", s.comment, 3))
                sb.appendLine("      " + cText("F$r", s.inspectorName, 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }

        rowIdx++
        rowIdx++

        // Накопительный рейтинг нарушений тишины за всё время
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "НАКОПИТЕЛЬНЫЙ РЕЙТИНГ НАРУШЕНИЙ ТИШИНЫ (ЗА ВСЁ ВРЕМЯ)", 1)}</row>""")
        rowIdx++

        val rhRow = rowIdx
        sb.appendLine("""    <row r="$rhRow">""")
        sb.appendLine("      " + cText("A$rhRow", "Место", 2))
        sb.appendLine("      " + cText("B$rhRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$rhRow", "Крыло", 2))
        sb.appendLine("      " + cText("D$rhRow", "Штрафы тишины", 2))
        sb.appendLine("      " + cText("E$rhRow", "Статус", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        val sortedStats = rooms.map { room ->
            room to (penaltyStats[room.roomNumber] ?: RoomPenaltyStats(room.roomNumber, 0, 0, 0))
        }.sortedByDescending { it.second.silencePoints }

        var place = 1
        for ((room, stat) in sortedStats) {
            val r = rowIdx
            val statusText = when {
                stat.silencePoints == 0 -> "Без нарушений тишины"
                stat.silencePoints == 1 -> "1 замечание"
                else -> "${stat.silencePoints} замечаний"
            }
            val statStyle = if (stat.silencePoints == 0) 6 else 5

            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "#$place", 4))
            sb.appendLine("      " + cNum("B$r", room.roomNumber, 4))
            sb.appendLine("      " + cText("C$r", room.wing, 3))
            sb.appendLine("      " + cText("D$r", "${stat.silencePoints} б.", statStyle))
            sb.appendLine("      " + cText("E$r", statusText, statStyle))
            sb.appendLine("""    </row>""")
            place++
            rowIdx++
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист 3: Кухни и Тишина (для сводного отчета)
     */
    private fun buildSheet3Xml(
        date: String,
        floor: Int,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String,
        kitchens: List<KitchenDutyRecord>,
        silenceIncidents: List<SilenceIncidentRecord>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="24" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="38" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "СВОДНЫЙ ОТЧЁТ: ДЕЖУРСТВО ПО КУХНЯМ И РЕЖИМ ТИШИНЫ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Дата: $date | Этаж: $floor этаж | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        rowIdx++

        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "1. ДЕЖУРСТВО ПО КУХНЯМ ЭТАЖА", 1)}</row>""")
        rowIdx++

        val kHeadRow = rowIdx
        sb.appendLine("""    <row r="$kHeadRow">""")
        sb.appendLine("      " + cText("A$kHeadRow", "Кухня / Обязанность", 2))
        sb.appendLine("      " + cText("B$kHeadRow", "Дежурная комната", 2))
        sb.appendLine("      " + cText("C$kHeadRow", "Статус уборки", 2))
        sb.appendLine("      " + cText("D$kHeadRow", "Замечания / Состояние", 2))
        sb.appendLine("      " + cText("E$kHeadRow", "Кто проверил", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (kitchens.isEmpty()) {
            val templateSides = listOf(
                "Северная (мытьё)",
                "Северная (мусор)",
                "Южная (мытьё)",
                "Южная (мусор)"
            )
            for (side in templateSides) {
                val r = rowIdx
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", side, 3))
                sb.appendLine("      " + cText("B$r", "Не назначено", 4))
                sb.appendLine("      " + cText("C$r", "Нет данных", 4))
                sb.appendLine("      " + cText("D$r", "", 3))
                sb.appendLine("      " + cText("E$r", "—", 4))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        } else {
            for (k in kitchens) {
                val r = rowIdx
                val isClean = k.status == "Убрано"
                val statusStyle = if (isClean) 6 else 5
                val sideTitle = if (k.kitchenSide.contains("кухня", ignoreCase = true)) k.kitchenSide else "${k.kitchenSide}"
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", sideTitle, 3))
                sb.appendLine("      " + cText("B$r", "комн. ${k.dutyRoomNumber}", 4))
                sb.appendLine("      " + cText("C$r", k.status, statusStyle))
                sb.appendLine("      " + cText("D$r", k.comment, 3))
                sb.appendLine("      " + cText("E$r", k.inspectorName, 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }

        rowIdx++
        rowIdx++

        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "2. СОБЛЮДЕНИЕ РЕЖИМА ТИШИНЫ (КОМЕНДАНТСКИЙ ЧАС)", 1)}</row>""")
        rowIdx++

        val sHeadRow = rowIdx
        sb.appendLine("""    <row r="$sHeadRow">""")
        sb.appendLine("      " + cText("A$sHeadRow", "Время", 2))
        sb.appendLine("      " + cText("B$sHeadRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$sHeadRow", "Вид нарушения", 2))
        sb.appendLine("      " + cText("D$sHeadRow", "Штрафные баллы", 2))
        sb.appendLine("      " + cText("E$sHeadRow", "Подробности / Принятые меры", 2))
        sb.appendLine("      " + cText("F$sHeadRow", "Кто зафиксировал", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (silenceIncidents.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "Нарушений режима тишины не зафиксировано", 6))
            sb.appendLine("      " + cText("D$r", "0 б.", 6))
            sb.appendLine("      " + cText("E$r", "Ночь прошла спокойно", 3))
            sb.appendLine("      " + cText("F$r", "—", 4))
            sb.appendLine("""    </row>""")
        } else {
            for (s in silenceIncidents) {
                val r = rowIdx
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", s.time, 4))
                sb.appendLine("      " + cNum("B$r", s.roomNumber, 4))
                sb.appendLine("      " + cText("C$r", s.violationType, 5))
                sb.appendLine("      " + cText("D$r", "+1 штр. балл", 5))
                sb.appendLine("      " + cText("E$r", s.comment, 3))
                sb.appendLine("      " + cText("F$r", s.inspectorName, 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист 1 (Смена 15 дней): Итоги смены, антирейтинг комнат и образцовые комнаты
     */
    private fun buildShiftSummarySheetXml(
        summary: ShiftRankingSummary,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="18" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="20" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="30" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        val floorLabel = if (summary.filterFloor != null) "${summary.filterFloor} этаж" else "Всё общежитие (2–5 этажи)"
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "ИТОГИ 15-ДНЕВНОЙ СМЕНЫ: АНТИРЕЙТИНГ И ОБРАЗЦОВЫЕ КОМНАТЫ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Период смены: ${summary.startDate} — ${summary.endDate} (${summary.daysCount} дн.) | Охват: $floorLabel | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Сводка: Всего комнат: ${summary.allRoomStats.size} | Образцовых (0 б.): ${summary.exemplaryRooms.size} | Со штрафами: ${summary.topOverallPenaltyRooms.size} | Итого штрафов: ${summary.totalPenaltyPoints} б. (чистота: ${summary.totalCleanlinessPoints} б., тишина: ${summary.totalSilencePoints} б.)", 0)}</row>""")
        rowIdx++
        rowIdx++

        // 1. АНТИРЕЙТИНГ ПО ЧИСТОТЕ
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "1. АНТИРЕЙТИНГ: САМЫЕ ПРОБЛЕМНЫЕ КОМНАТЫ ПО ЧИСТОТЕ (ТОП)", 1)}</row>""")
        rowIdx++
        var hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "Место", 2))
        sb.appendLine("      " + cText("B$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$hRow", "Этаж", 2))
        sb.appendLine("      " + cText("D$hRow", "Штрафы чистоты", 2))
        sb.appendLine("      " + cText("E$hRow", "Оценка дисциплины", 2))
        sb.appendLine("      " + cText("F$hRow", "Примечание", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (summary.topCleanlinessProblemRooms.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "—", 4))
            sb.appendLine("      " + cText("D$r", "0 б.", 6))
            sb.appendLine("      " + cText("E$r", "Все комнаты чистые", 6))
            sb.appendLine("      " + cText("F$r", "Замечаний за 15 дней не выявлено", 3))
            sb.appendLine("""    </row>""")
            rowIdx++
        } else {
            summary.topCleanlinessProblemRooms.take(10).forEachIndexed { i, stat ->
                val r = rowIdx
                val badge = if (i == 0) "Самая грязная комната смены" else if (stat.cleanlinessPoints >= 3) "Многократные замечания" else "Замечания"
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cNum("A$r", i + 1, 4))
                sb.appendLine("      " + cNum("B$r", stat.roomNumber, 4))
                sb.appendLine("      " + cNum("C$r", stat.floor, 4))
                sb.appendLine("      " + cText("D$r", "+${stat.cleanlinessPoints} штр. б.", 5))
                sb.appendLine("      " + cText("E$r", badge, 5))
                sb.appendLine("      " + cText("F$r", "Требует повышенного контроля", 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }
        rowIdx++

        // 2. АНТИРЕЙТИНГ ПО ТИШИНЕ
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "2. АНТИРЕЙТИНГ: САМЫЕ ШУМНЫЕ КОМНАТЫ (НАРУШИТЕЛИ ТИШИНЫ)", 1)}</row>""")
        rowIdx++
        hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "Место", 2))
        sb.appendLine("      " + cText("B$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$hRow", "Этаж", 2))
        sb.appendLine("      " + cText("D$hRow", "Кол-во замечаний", 2))
        sb.appendLine("      " + cText("E$hRow", "Штрафные баллы", 2))
        sb.appendLine("      " + cText("F$hRow", "Статус", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (summary.topSilenceViolatorRooms.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "—", 4))
            sb.appendLine("      " + cText("D$r", "0", 6))
            sb.appendLine("      " + cText("E$r", "0 б.", 6))
            sb.appendLine("      " + cText("F$r", "Нарушений тишины не зафиксировано", 6))
            sb.appendLine("""    </row>""")
            rowIdx++
        } else {
            summary.topSilenceViolatorRooms.take(10).forEachIndexed { i, stat ->
                val r = rowIdx
                val badge = if (i == 0) "Самая шумная комната смены" else "Нарушитель режима"
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cNum("A$r", i + 1, 4))
                sb.appendLine("      " + cNum("B$r", stat.roomNumber, 4))
                sb.appendLine("      " + cNum("C$r", stat.floor, 4))
                sb.appendLine("      " + cNum("D$r", stat.silenceIncidentsCount, 4))
                sb.appendLine("      " + cText("E$r", "+${stat.silencePoints} штр. б.", 5))
                sb.appendLine("      " + cText("F$r", badge, 5))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }
        rowIdx++

        // 3. АНТИ-ТОП ОБЩЕГО ЗАЧЁТА
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "3. АНТИРЕЙТИНГ ОБЩЕГО ЗАЧЁТА ДИСЦИПЛИНЫ", 1)}</row>""")
        rowIdx++
        hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "Место", 2))
        sb.appendLine("      " + cText("B$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("C$hRow", "Этаж", 2))
        sb.appendLine("      " + cText("D$hRow", "Штрафы чистоты", 2))
        sb.appendLine("      " + cText("E$hRow", "Штрафы тишины", 2))
        sb.appendLine("      " + cText("F$hRow", "ИТОГО БАЛЛОВ", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (summary.topOverallPenaltyRooms.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "—", 4))
            sb.appendLine("      " + cText("D$r", "0 б.", 6))
            sb.appendLine("      " + cText("E$r", "0 б.", 6))
            sb.appendLine("      " + cText("F$r", "0 б. (Идеально)", 6))
            sb.appendLine("""    </row>""")
            rowIdx++
        } else {
            summary.topOverallPenaltyRooms.take(10).forEachIndexed { i, stat ->
                val r = rowIdx
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cNum("A$r", i + 1, 4))
                sb.appendLine("      " + cNum("B$r", stat.roomNumber, 4))
                sb.appendLine("      " + cNum("C$r", stat.floor, 4))
                sb.appendLine("      " + cText("D$r", "${stat.cleanlinessPoints} б.", if (stat.cleanlinessPoints > 0) 5 else 4))
                sb.appendLine("      " + cText("E$r", "${stat.silencePoints} б.", if (stat.silencePoints > 0) 5 else 4))
                sb.appendLine("      " + cText("F$r", "${stat.totalPoints} штр. б.", 5))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }
        rowIdx++

        // 4. ОБРАЗЦОВЫЕ КОМНАТЫ
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "4. ОБРАЗЦОВЫЕ КОМНАТЫ (0 ШТРАФНЫХ БАЛЛОВ ЗА 15 ДНЕЙ)", 1)}</row>""")
        rowIdx++
        val rEx = rowIdx
        val exemplaryStr = if (summary.exemplaryRooms.isEmpty()) "Образцовых комнат нет" else summary.exemplaryRooms.joinToString(", ") { "${it.roomNumber}" }
        sb.appendLine("""    <row r="$rEx">${cText("A$rEx", "Комнаты с идеальной дисциплиной (${summary.exemplaryRooms.size} комн.): $exemplaryStr", 6)}</row>""")
        rowIdx++
        rowIdx++

        // 5. ПОЛНАЯ ТАБЛИЦА ВСЕХ КОМНАТ
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "5. ПОЛНАЯ ВЕДОМОСТЬ ВСЕХ КОМНАТ ЗА СМЕНУ", 1)}</row>""")
        rowIdx++
        hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("B$hRow", "Этаж", 2))
        sb.appendLine("      " + cText("C$hRow", "Штрафы чистоты", 2))
        sb.appendLine("      " + cText("D$hRow", "Замечания тишины", 2))
        sb.appendLine("      " + cText("E$hRow", "Итого штрафов", 2))
        sb.appendLine("      " + cText("F$hRow", "Статус дисциплины", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        for (stat in summary.allRoomStats) {
            val r = rowIdx
            val statusText = if (stat.isExemplary) "👑 Образцовая (0 б.)" else "⚠️ Замечания (+${stat.totalPoints} б.)"
            val statusStyle = if (stat.isExemplary) 6 else 5

            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cNum("A$r", stat.roomNumber, 4))
            sb.appendLine("      " + cNum("B$r", stat.floor, 4))
            sb.appendLine("      " + cText("C$r", "${stat.cleanlinessPoints} б.", if (stat.cleanlinessPoints > 0) 5 else 4))
            sb.appendLine("      " + cText("D$r", "${stat.silenceIncidentsCount} зам.", if (stat.silenceIncidentsCount > 0) 5 else 4))
            sb.appendLine("      " + cText("E$r", "${stat.totalPoints} б.", statusStyle))
            sb.appendLine("      " + cText("F$r", statusText, statusStyle))
            sb.appendLine("""    </row>""")
            rowIdx++
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист 2 (Смена 15 дней): Журнал тишины за смену
     */
    private fun buildShiftSilenceSheetXml(
        summary: ShiftRankingSummary,
        silenceIncidents: List<SilenceIncidentRecord>,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="12" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="10" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="12" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="26" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="16" customWidth="1"/>""")
        sb.appendLine("""    <col min="7" max="7" width="32" customWidth="1"/>""")
        sb.appendLine("""    <col min="8" max="8" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        val floorLabel = if (summary.filterFloor != null) "${summary.filterFloor} этаж" else "Все этажи"
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "ЖУРНАЛ НАРУШЕНИЙ РЕЖИМА ТИШИНЫ ЗА 15-ДНЕВНУЮ СМЕНУ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Период: ${summary.startDate} — ${summary.endDate} | Охват: $floorLabel | Инцидентов: ${silenceIncidents.size} | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        rowIdx++

        val hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "Дата", 2))
        sb.appendLine("      " + cText("B$hRow", "Время", 2))
        sb.appendLine("      " + cText("C$hRow", "Этаж", 2))
        sb.appendLine("      " + cText("D$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("E$hRow", "Вид нарушения", 2))
        sb.appendLine("      " + cText("F$hRow", "Штрафной балл", 2))
        sb.appendLine("      " + cText("G$hRow", "Подробности / Принятые меры", 2))
        sb.appendLine("      " + cText("H$hRow", "Кто зафиксировал", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (silenceIncidents.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "—", 4))
            sb.appendLine("      " + cText("D$r", "—", 4))
            sb.appendLine("      " + cText("E$r", "Нарушений тишины за смену не зафиксировано", 6))
            sb.appendLine("      " + cText("F$r", "0 б.", 6))
            sb.appendLine("      " + cText("G$r", "Все ночи прошли спокойно", 3))
            sb.appendLine("      " + cText("H$r", "—", 4))
            sb.appendLine("""    </row>""")
        } else {
            for (s in silenceIncidents) {
                val r = rowIdx
                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", s.date, 4))
                sb.appendLine("      " + cText("B$r", s.time, 4))
                sb.appendLine("      " + cNum("C$r", s.floor, 4))
                sb.appendLine("      " + cNum("D$r", s.roomNumber, 4))
                sb.appendLine("      " + cText("E$r", s.violationType, 5))
                sb.appendLine("      " + cText("F$r", "+1 штр. б.", 5))
                sb.appendLine("      " + cText("G$r", s.comment, 3))
                sb.appendLine("      " + cText("H$r", s.inspectorName, 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }

    /**
     * Лист 3 (Смена 15 дней): Ведомость проверок чистоты за смену
     */
    private fun buildShiftCleanlinessSheetXml(
        summary: ShiftRankingSummary,
        cleanlinessRecords: List<CleanlinessRecord>,
        recipient: ReportRecipient,
        inspectorName: String,
        inspectorRole: String
    ): String {
        val sb = StringBuilder()
        sb.appendLine("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.appendLine("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.appendLine("""  <cols>""")
        sb.appendLine("""    <col min="1" max="1" width="14" customWidth="1"/>""")
        sb.appendLine("""    <col min="2" max="2" width="10" customWidth="1"/>""")
        sb.appendLine("""    <col min="3" max="3" width="12" customWidth="1"/>""")
        sb.appendLine("""    <col min="4" max="4" width="22" customWidth="1"/>""")
        sb.appendLine("""    <col min="5" max="5" width="16" customWidth="1"/>""")
        sb.appendLine("""    <col min="6" max="6" width="32" customWidth="1"/>""")
        sb.appendLine("""    <col min="7" max="7" width="22" customWidth="1"/>""")
        sb.appendLine("""  </cols>""")
        sb.appendLine("""  <sheetData>""")

        var rowIdx = 1
        val floorLabel = if (summary.filterFloor != null) "${summary.filterFloor} этаж" else "Все этажи"
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "ВЕДОМОСТЬ ПРОВЕРОК САНИТАРНОГО СОСТОЯНИЯ ЗА 15-ДНЕВНУЮ СМЕНУ", 1)}</row>""")
        rowIdx++
        sb.appendLine("""    <row r="$rowIdx">${cText("A$rowIdx", "Период: ${summary.startDate} — ${summary.endDate} | Охват: $floorLabel | Проверок в базе: ${cleanlinessRecords.size} | Получатель: ${recipient.title} | Составил: $inspectorRole $inspectorName", 0)}</row>""")
        rowIdx++
        rowIdx++

        val hRow = rowIdx
        sb.appendLine("""    <row r="$hRow">""")
        sb.appendLine("      " + cText("A$hRow", "Дата", 2))
        sb.appendLine("      " + cText("B$hRow", "Этаж", 2))
        sb.appendLine("      " + cText("C$hRow", "№ комн.", 2))
        sb.appendLine("      " + cText("D$hRow", "Оценка чистоты", 2))
        sb.appendLine("      " + cText("E$hRow", "Штрафные баллы", 2))
        sb.appendLine("      " + cText("F$hRow", "Примечание дежурного", 2))
        sb.appendLine("      " + cText("G$hRow", "Кто проверил", 2))
        sb.appendLine("""    </row>""")
        rowIdx++

        if (cleanlinessRecords.isEmpty()) {
            val r = rowIdx
            sb.appendLine("""    <row r="$r">""")
            sb.appendLine("      " + cText("A$r", "—", 4))
            sb.appendLine("      " + cText("B$r", "—", 4))
            sb.appendLine("      " + cText("C$r", "—", 4))
            sb.appendLine("      " + cText("D$r", "Записи отсутствуют", 3))
            sb.appendLine("      " + cText("E$r", "0 б.", 6))
            sb.appendLine("      " + cText("F$r", "—", 3))
            sb.appendLine("      " + cText("G$r", "—", 4))
            sb.appendLine("""    </row>""")
        } else {
            for (rec in cleanlinessRecords) {
                val r = rowIdx
                val pts = PenaltyUtils.getCleanlinessPoints(rec.status)
                val isClean = rec.status == "Чисто"
                val statusStyle = if (isClean) 6 else 5
                val ptsText = if (pts == 0) "0 б." else "+$pts штр. б."

                sb.appendLine("""    <row r="$r">""")
                sb.appendLine("      " + cText("A$r", rec.date, 4))
                sb.appendLine("      " + cNum("B$r", rec.floor, 4))
                sb.appendLine("      " + cNum("C$r", rec.roomNumber, 4))
                sb.appendLine("      " + cText("D$r", rec.status, statusStyle))
                sb.appendLine("      " + cText("E$r", ptsText, statusStyle))
                sb.appendLine("      " + cText("F$r", rec.comment, 3))
                sb.appendLine("      " + cText("G$r", rec.inspectorName, 3))
                sb.appendLine("""    </row>""")
                rowIdx++
            }
        }

        sb.appendLine("""  </sheetData>""")
        sb.appendLine("""</worksheet>""")
        return sb.toString()
    }
}
