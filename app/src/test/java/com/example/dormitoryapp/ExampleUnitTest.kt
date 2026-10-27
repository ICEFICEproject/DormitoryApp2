package com.example.dormitoryapp

import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class ExampleUnitTest {
    @Test
    fun testXlsxGeneration() {
        val file = File("test_full_output.xlsx")
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            fun addEntry(name: String, content: String) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 1. [Content_Types].xml
            addEntry(
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet3.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>"""
            )

            // 2. _rels/.rels
            addEntry(
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )

            // 3. xl/_rels/workbook.xml.rels
            addEntry(
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet3.xml"/>
  <Relationship Id="rId4" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )

            // 4. xl/workbook.xml
            addEntry(
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Численность" sheetId="1" r:id="rId1"/>
    <sheet name="Чистота комнат" sheetId="2" r:id="rId2"/>
    <sheet name="Кухни и Тишина" sheetId="3" r:id="rId3"/>
  </sheets>
</workbook>"""
            )

            // 5. xl/styles.xml
            addEntry(
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
  <cellXfs count="8">
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
      <alignment horizontal="center" vertical="center"/>
    </xf>
  </cellXfs>
</styleSheet>"""
            )

            // Sheet 1
            addEntry(
                "xl/worksheets/sheet1.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="16" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="14" customWidth="1"/>
    <col min="4" max="4" width="14" customWidth="1"/>
    <col min="5" max="5" width="14" customWidth="1"/>
    <col min="6" max="6" width="26" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1">
      <c r="A1" s="1" t="inlineStr"><is><t>СВОДНЫЙ ОТЧЁТ: НОЧНОЙ ОБХОД И ЧИСЛЕННОСТЬ</t></is></c>
    </row>
    <row r="2">
      <c r="A2" s="0" t="inlineStr"><is><t>Дата: 2026-09-27 | Этаж: 5 этаж</t></is></c>
    </row>
    <row r="4">
      <c r="A4" s="2" t="inlineStr"><is><t>№ комнаты</t></is></c>
      <c r="B4" s="2" t="inlineStr"><is><t>Крыло</t></is></c>
      <c r="C4" s="2" t="inlineStr"><is><t>Норма (чел.)</t></is></c>
      <c r="D4" s="2" t="inlineStr"><is><t>Ночует (факт)</t></is></c>
      <c r="E4" s="2" t="inlineStr"><is><t>Отсутствует</t></is></c>
      <c r="F4" s="2" t="inlineStr"><is><t>Статус</t></is></c>
    </row>
    <row r="5">
      <c r="A5" s="4"><v>501</v></c>
      <c r="B5" s="3" t="inlineStr"><is><t>Северная (00–15)</t></is></c>
      <c r="C5" s="4"><v>4</v></c>
      <c r="D5" s="4"><v>4</v></c>
      <c r="E5" s="4"><v>0</v></c>
      <c r="F5" s="6" t="inlineStr"><is><t>Все на месте</t></is></c>
    </row>
    <row r="6">
      <c r="A6" s="4"><v>534</v></c>
      <c r="B6" s="3" t="inlineStr"><is><t>Южная (16–34)</t></is></c>
      <c r="C6" s="4"><v>8</v></c>
      <c r="D6" s="4"><v>6</v></c>
      <c r="E6" s="5"><v>2</v></c>
      <c r="F6" s="5" t="inlineStr"><is><t>Отсутствует 2 чел.</t></is></c>
    </row>
    <row r="7">
      <c r="A7" s="7" t="inlineStr"><is><t>ИТОГО</t></is></c>
      <c r="B7" s="7" t="inlineStr"><is><t></t></is></c>
      <c r="C7" s="7"><v>12</v></c>
      <c r="D7" s="7"><v>10</v></c>
      <c r="E7" s="7"><v>2</v></c>
      <c r="F7" s="7" t="inlineStr"><is><t>Отсутствует 2 чел.</t></is></c>
    </row>
  </sheetData>
</worksheet>"""
            )

            // Sheet 2
            addEntry(
                "xl/worksheets/sheet2.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="16" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="36" customWidth="1"/>
    <col min="4" max="4" width="24" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1">
      <c r="A1" s="1" t="inlineStr"><is><t>СВОДНЫЙ ОТЧЁТ: ЧИСТОТА И САНИТАРНОЕ СОСТОЯНИЕ</t></is></c>
    </row>
    <row r="2">
      <c r="A2" s="0" t="inlineStr"><is><t>Дата: 2026-09-27 | Этаж: 5 этаж</t></is></c>
    </row>
    <row r="4">
      <c r="A4" s="2" t="inlineStr"><is><t>№ комнаты</t></is></c>
      <c r="B4" s="2" t="inlineStr"><is><t>Оценка чистоты</t></is></c>
      <c r="C4" s="2" t="inlineStr"><is><t>Замечания / Комментарий</t></is></c>
      <c r="D4" s="2" t="inlineStr"><is><t>Проверил</t></is></c>
    </row>
    <row r="5">
      <c r="A5" s="4"><v>501</v></c>
      <c r="B5" s="6" t="inlineStr"><is><t>Чисто</t></is></c>
      <c r="C5" s="3" t="inlineStr"><is><t></t></is></c>
      <c r="D5" s="4" t="inlineStr"><is><t>Староста</t></is></c>
    </row>
  </sheetData>
</worksheet>"""
            )

            // Sheet 3
            addEntry(
                "xl/worksheets/sheet3.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols>
    <col min="1" max="1" width="20" customWidth="1"/>
    <col min="2" max="2" width="22" customWidth="1"/>
    <col min="3" max="3" width="24" customWidth="1"/>
    <col min="4" max="4" width="36" customWidth="1"/>
  </cols>
  <sheetData>
    <row r="1">
      <c r="A1" s="1" t="inlineStr"><is><t>ДЕЖУРСТВО ПО КУХНЯМ И РЕЖИМ ТИШИНЫ</t></is></c>
    </row>
    <row r="2">
      <c r="A2" s="0" t="inlineStr"><is><t>Дата: 2026-09-27 | Этаж: 5 этаж</t></is></c>
    </row>
    <row r="4">
      <c r="A4" s="2" t="inlineStr"><is><t>Кухня</t></is></c>
      <c r="B4" s="2" t="inlineStr"><is><t>Дежурная комната</t></is></c>
      <c r="C4" s="2" t="inlineStr"><is><t>Статус уборки</t></is></c>
      <c r="D4" s="2" t="inlineStr"><is><t>Комментарий</t></is></c>
    </row>
    <row r="5">
      <c r="A5" s="3" t="inlineStr"><is><t>Северная</t></is></c>
      <c r="B5" s="4" t="inlineStr"><is><t>комн. 504</t></is></c>
      <c r="C5" s="6" t="inlineStr"><is><t>Убрано</t></is></c>
      <c r="D5" s="3" t="inlineStr"><is><t>Плиты и столы чистые</t></is></c>
    </row>
  </sheetData>
</worksheet>"""
            )
        }

        assertTrue(file.exists())
        assertTrue(file.length() > 1000)
        println("Generated XLSX size: ${file.length()} bytes")
        file.delete()
    }
}