package com.myfun.funclass.core.parser

import com.myfun.funclass.core.model.Course

/**
 * CSV & Excel-exported CSV/TSV schedule importer/exporter.
 * Compatible with common timetable CSV template columns:
 * 课程名称,星期,开始节数,结束节数,老师,地点,周数
 */
object CsvExcelImporter {

    private val defaultHeader = listOf("课程名称", "星期", "开始节数", "结束节数", "老师", "地点", "周数")

    fun parse(rawContent: String, tableName: String = "表格导入课表"): ParsedScheduleResult {
        val cleaned = rawContent.removePrefix("\uFEFF")
        val rows = parseCsvRows(cleaned)
        if (rows.isEmpty()) {
            return ParsedScheduleResult(tableName = tableName, warnings = listOf("CSV 文件内容为空"))
        }

        val firstRow = rows.first().map { it.trim() }
        val hasHeader = firstRow.any { it.contains("课程") || it.contains("星期") || it.equals("name", true) }
        val dataRows = if (hasHeader) rows.drop(1) else rows

        // Column index resolution (supports custom header ordering too)
        val colName = findCol(firstRow, hasHeader, 0, "课程名称", "课程", "name", "course")
        val colDay = findCol(firstRow, hasHeader, 1, "星期", "周几", "day")
        val colStart = findCol(firstRow, hasHeader, 2, "开始节数", "开始节次", "startNode", "start")
        val colEnd = findCol(firstRow, hasHeader, 3, "结束节数", "结束节次", "endNode", "end")
        val colTeacher = findCol(firstRow, hasHeader, 4, "老师", "教师", "teacher")
        val colRoom = findCol(firstRow, hasHeader, 5, "地点", "教室", "room", "location")
        val colWeeks = findCol(firstRow, hasHeader, 6, "周数", "周次", "weeks")

        val rawSessions = mutableListOf<RawParsedSession>()
        val warnings = mutableListOf<String>()

        dataRows.forEachIndexed { idx, row ->
            if (row.all { it.isBlank() }) return@forEachIndexed
            val name = row.getOrNull(colName)?.trim().orEmpty()
            if (name.isEmpty()) {
                warnings += "第 ${idx + 2} 行缺少课程名称，已跳过"
                return@forEachIndexed
            }
            val day = CourseGroupingHelper.parseChineseDayOfWeek(row.getOrNull(colDay).orEmpty())
            if (day == null) {
                warnings += "第 ${idx + 2} 行星期格式无法识别(${row.getOrNull(colDay)})，已跳过"
                return@forEachIndexed
            }
            val startNode = Regex("\\d+").find(row.getOrNull(colStart).orEmpty())?.value?.toIntOrNull()?.coerceIn(1, 20) ?: 1
            val endNode = Regex("\\d+").find(row.getOrNull(colEnd).orEmpty())?.value?.toIntOrNull()?.coerceIn(startNode, 20) ?: startNode
            val teacher = row.getOrNull(colTeacher)?.trim().orEmpty()
            val room = row.getOrNull(colRoom)?.trim().orEmpty()
            val weeksText = row.getOrNull(colWeeks)?.trim().orEmpty()
            val weeks = CourseGroupingHelper.parseWeeksText(weeksText)

            rawSessions += RawParsedSession(
                courseName = name,
                dayOfWeek = day,
                startNode = startNode,
                nodeCount = (endNode - startNode + 1).coerceAtLeast(1),
                weeks = weeks,
                room = room,
                teacher = teacher
            )
        }

        val courses = CourseGroupingHelper.groupSessions(rawSessions)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = tableName,
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN,
            warnings = warnings
        )
    }

    fun exportCsv(courses: List<Course>): String {
        val sb = StringBuilder()
        sb.appendLine(defaultHeader.joinToString(","))
        for (course in courses.filter { !it.isDeleted }) {
            for (session in course.sessions.filter { !it.isDeleted }) {
                val fields = listOf(
                    course.name,
                    session.dayOfWeek.toString(),
                    session.startNode.toString(),
                    session.endNode.toString(),
                    session.teacher,
                    session.room,
                    session.weeks.encode()
                ).map { escapeCsvField(it) }
                sb.appendLine(fields.joinToString(","))
            }
        }
        return sb.toString()
    }

    fun sampleTemplateCsv(): String = buildString {
        appendLine(defaultHeader.joinToString(","))
        appendLine("高等数学,1,1,2,张教授,教A-101,1-16")
        appendLine("大学英语,3,3,4,李老师,外语楼205,1-15单")
        appendLine("数据结构,5,5,6,王老师,信息楼302,1-8、10-16")
    }

    private fun findCol(header: List<String>, hasHeader: Boolean, defaultIdx: Int, vararg keywords: String): Int {
        if (!hasHeader) return defaultIdx
        val found = header.indexOfFirst { h -> keywords.any { kw -> h.contains(kw, ignoreCase = true) } }
        return if (found >= 0) found else defaultIdx
    }

    private fun escapeCsvField(value: String): String {
        return if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
    }

    private fun parseCsvRows(text: String): List<List<String>> {
        val delimiter = if (text.lineSequence().firstOrNull()?.contains('\t') == true &&
            text.lineSequence().firstOrNull()?.contains(',') != true
        ) '\t' else ','

        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes -> {
                    if (c == '"') {
                        if (i + 1 < text.length && text[i + 1] == '"') {
                            field.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        field.append(c)
                    }
                }
                c == '"' -> inQuotes = true
                c == delimiter -> {
                    currentRow += field.toString()
                    field.clear()
                }
                c == '\r' -> {
                    if (i + 1 < text.length && text[i + 1] == '\n') i++
                    currentRow += field.toString()
                    field.clear()
                    rows += currentRow.toList()
                    currentRow.clear()
                }
                c == '\n' -> {
                    currentRow += field.toString()
                    field.clear()
                    rows += currentRow.toList()
                    currentRow.clear()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow += field.toString()
            rows += currentRow.toList()
        }
        return rows
    }
}
