package com.myfun.funclass.core.parser

import com.fleeksoft.ksoup.Ksoup
import com.fleeksoft.ksoup.nodes.Document
import com.fleeksoft.ksoup.nodes.Element
import com.myfun.funclass.core.model.WeekSet
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private val lenientJson = Json {
    ignoreUnknownKeys = true
    isLenient = true
}

/**
 * 1. 正方 / 新正方教务系统解析器 (支持新正方 JSON `kbList` 与传统/新版 HTML 表格)
 */
class ZhengFangParser : ScheduleParser {
    override val id: String = ID
    override val displayName: String = "正方 / 新正方教务"
    override val systemFamily: AcademicSystemFamily = AcademicSystemFamily.ZHENGFANG

    override fun canParse(source: String): Boolean =
        source.contains("\"kbList\"") ||
            source.contains("kbgrid_table") ||
            source.contains("Table1") ||
            source.contains("timetable_con")

    override fun parse(source: String): ParsedScheduleResult {
        val raw = mutableListOf<RawParsedSession>()
        val trimmed = source.trim()
        if (trimmed.startsWith("{") && trimmed.contains("kbList")) {
            parseJson(trimmed, raw)
        } else {
            parseHtml(trimmed, raw)
        }
        val courses = CourseGroupingHelper.groupSessions(raw)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = "正方教务导入",
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }

    private fun parseJson(text: String, out: MutableList<RawParsedSession>) {
        val root = lenientJson.parseToJsonElement(text).jsonObject
        val kbList = root["kbList"]?.jsonArray ?: return
        for (el in kbList) {
            val obj = el.jsonObject
            val name = obj.str("kcmc") ?: continue
            val day = obj.str("xqj")?.toIntOrNull() ?: continue
            val jcs = obj.str("jcs") ?: obj.str("jcor") ?: "1-2"
            val (startNode, count) = parseNodeRange(jcs)
            val weeks = CourseGroupingHelper.parseWeeksText(obj.str("zcd").orEmpty())
            val room = obj.str("cdmc").orEmpty()
            val teacher = obj.str("xm").orEmpty()
            val credit = obj.str("xf")?.toFloatOrNull() ?: 0f
            out += RawParsedSession(
                courseName = name,
                dayOfWeek = day,
                startNode = startNode,
                nodeCount = count,
                weeks = weeks,
                room = room,
                teacher = teacher,
                credit = credit
            )
        }
    }

    private fun parseHtml(html: String, out: MutableList<RawParsedSession>) {
        val doc = Ksoup.parse(html)
        // New ZhengFang grid cells: td[id^="1-"]..td[id^="7-"] containing div.timetable_con
        val newCells = doc.select("td.td_wrap, td[id*='-']")
        for (td in newCells) {
            val idParts = td.id().split("-")
            val dayFromId = idParts.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..7 }
            val startFromId = idParts.getOrNull(1)?.toIntOrNull()
            val tdRowspan = td.attr("rowspan").toIntOrNull()?.coerceAtLeast(1) ?: 2
            val blocks = td.select("div.timetable_con")
            if (blocks.isNotEmpty() && dayFromId != null) {
                for (block in blocks) {
                    val rawTitle = block.selectFirst("span.title, u.title, p.title, a.title, .title")?.text()?.trim()
                        ?: block.selectFirst("font, span")?.text()?.trim().orEmpty()
                    val (name, inlineMeta) = HtmlTableParsingSupport.cleanCourseNameAndInlineMeta(rawTitle)
                    if (name.isEmpty() || HtmlTableParsingSupport.isNonCourseHeaderText(name)) continue

                    val pElements = block.select("p")
                    var weekAndNode = ""
                    var room = ""
                    var teacher = ""
                    val fallbackLines = mutableListOf<String>()

                    for (p in pElements) {
                        val txt = p.text().trim()
                        if (txt.isEmpty()) continue
                        val titleAttr = p.selectFirst("[title]")?.attr("title").orEmpty()
                        when {
                            titleAttr.contains("周") || titleAttr.contains("节") -> {
                                weekAndNode = if (weekAndNode.isEmpty()) txt else "$weekAndNode $txt"
                            }
                            titleAttr.contains("地点") || titleAttr.contains("教室") || titleAttr.contains("场地") -> {
                                room = HtmlTableParsingSupport.stripMetadataPrefix(txt)
                            }
                            titleAttr.contains("教师") || titleAttr.contains("老师") -> {
                                teacher = HtmlTableParsingSupport.stripMetadataPrefix(txt)
                            }
                            else -> fallbackLines += txt
                        }
                    }

                    if (weekAndNode.isEmpty()) {
                        weekAndNode = fallbackLines.firstOrNull {
                            CourseGroupingHelper.extractWeeksFromMixedText(it) != null ||
                                Regex("\\d+\\s*[-~至,]\\s*\\d+\\s*节").containsMatchIn(it)
                        } ?: inlineMeta
                    }
                    if (room.isEmpty()) {
                        room = fallbackLines.firstOrNull {
                            it.contains("教室") || it.contains("上课地点") || it.contains("地点")
                        }?.let { HtmlTableParsingSupport.stripMetadataPrefix(it) }.orEmpty()
                    }
                    if (teacher.isEmpty()) {
                        teacher = fallbackLines.firstOrNull {
                            it.contains("教师") || it.contains("老师")
                        }?.let { HtmlTableParsingSupport.stripMetadataPrefix(it) }.orEmpty()
                    }

                    if (room.isEmpty() || teacher.isEmpty()) {
                        val (inferredRoom, inferredTeacher) = HtmlTableParsingSupport.classifyRoomAndTeacher(
                            lines = fallbackLines,
                            nameLine = rawTitle,
                            weekLine = weekAndNode
                        )
                        if (room.isEmpty()) room = inferredRoom
                        if (teacher.isEmpty()) teacher = inferredTeacher
                    }

                    val combinedTimeText = listOf(weekAndNode, inlineMeta).filter { it.isNotBlank() }.joinToString(" ")
                    val (startNode, count) = extractNodeRangeFromText(combinedTimeText, startFromId ?: 1, tdRowspan)
                    val weeks = CourseGroupingHelper.extractWeeksFromMixedText(combinedTimeText)
                        ?: CourseGroupingHelper.parseWeeksText(weekAndNode)
                    out += RawParsedSession(name, dayFromId, startNode, count, weeks, room, teacher)
                }
            }
        }
        if (out.isNotEmpty()) return

        // Classic ZhengFang #Table1 / #Table6 / table.blacktab parsing using 2D rowspan-aware grid
        val table = HtmlTableParsingSupport.findBestScheduleTable(
            doc = doc,
            preferredSelectors = listOf("#Table1", "#Table6", "table.blacktab")
        ) ?: return
        val grid = HtmlTableParsingSupport.buildTableGrid(table)
        if (grid.isEmpty()) return

        val structure = HtmlTableParsingSupport.detectHeaderAndDayColumns(grid)
        val minDayCol = structure.colToDay.keys.minOrNull() ?: 0
        val defaultRowStep = HtmlTableParsingSupport.inferDefaultRowStep(grid, structure.headerRowIndices)

        var currentNode = 1
        for (rowIdx in grid.indices) {
            if (rowIdx in structure.headerRowIndices) continue
            val row = grid[rowIdx]
            val leadingTexts = (0 until minDayCol).mapNotNull { c ->
                row.getOrNull(c)?.element?.text()?.trim()?.takeIf { it.isNotEmpty() }
            }
            val rowNodeInfo = HtmlTableParsingSupport.parseLeadingRowNodeInfo(leadingTexts)
            if (rowNodeInfo != null) {
                currentNode = rowNodeInfo.first
            }
            val rowDefaultSpan = rowNodeInfo?.second ?: defaultRowStep

            var rowProducedCourses = 0
            for ((colIdx, dayFromCol) in structure.colToDay) {
                val cell = row.getOrNull(colIdx) ?: continue
                if (!cell.isOrigin) continue
                val cellBlocks = HtmlTableParsingSupport.extractCellBlocksAndLines(cell.element)
                for (lines in cellBlocks) {
                    if (lines.isEmpty()) continue
                    val (name, inlineMeta) = HtmlTableParsingSupport.cleanCourseNameAndInlineMeta(lines[0])
                    if (name.isEmpty() || HtmlTableParsingSupport.isNonCourseHeaderText(name)) continue

                    val timeLine = lines.drop(1).firstOrNull {
                        it.contains("{") ||
                            CourseGroupingHelper.extractWeeksFromMixedText(it) != null ||
                            Regex("\\d+\\s*[-~至,]\\s*\\d+\\s*节").containsMatchIn(it)
                    }.orEmpty()
                    val combinedTime = listOf(timeLine, inlineMeta).filter { it.isNotBlank() }.joinToString(" ")

                    val dayFromText = Regex("周([一二三四五六日天])第").find(combinedTime)
                        ?.groupValues?.get(1)
                        ?.let { CourseGroupingHelper.parseChineseDayOfWeek(it) }
                    val day = dayFromText ?: dayFromCol

                    val defaultCount = if (rowDefaultSpan == 1) cell.rowspan else cell.rowspan * rowDefaultSpan
                    val (sNode, nCount) = extractNodeRangeFromText(combinedTime, currentNode, defaultCount)
                    val weeks = CourseGroupingHelper.extractWeeksFromMixedText(combinedTime)
                        ?: WeekSet.range(1, 16)

                    val (room, teacher) = if (lines.size >= 4 && timeLine == lines.getOrNull(1)) {
                        val candidateTeacher = lines.getOrNull(2).orEmpty()
                        val candidateRoom = lines.getOrNull(3).orEmpty()
                        candidateRoom to candidateTeacher
                    } else {
                        HtmlTableParsingSupport.classifyRoomAndTeacher(
                            lines = lines,
                            nameLine = lines[0],
                            weekLine = timeLine
                        )
                    }

                    out += RawParsedSession(name, day, sNode, nCount, weeks, room, teacher)
                    rowProducedCourses++
                }
            }

            if (rowProducedCourses > 0 || rowNodeInfo != null || !HtmlTableParsingSupport.isDecorativeBreakRow(leadingTexts)) {
                currentNode += rowDefaultSpan
            }
        }
    }

    companion object {
        const val ID = "zhengfang"
    }
}

/**
 * 2. 强智教务系统解析器 (HTML `#kbtable` + `div.kbcontent`)
 */
class QiangZhiParser : ScheduleParser {
    override val id: String = ID
    override val displayName: String = "强智教务"
    override val systemFamily: AcademicSystemFamily = AcademicSystemFamily.QIANGZHI

    override fun canParse(source: String): Boolean =
        source.contains("kbcontent") || source.contains("kbtable")

    override fun parse(source: String): ParsedScheduleResult {
        val doc = Ksoup.parse(source)
        val raw = mutableListOf<RawParsedSession>()
        val table = HtmlTableParsingSupport.findBestScheduleTable(
            doc = doc,
            preferredSelectors = listOf("#kbtable", "#timetable", "table.Nsb_r_list")
        )
        if (table != null) {
            val grid = HtmlTableParsingSupport.buildTableGrid(table)
            val structure = HtmlTableParsingSupport.detectHeaderAndDayColumns(grid)
            val minDayCol = structure.colToDay.keys.minOrNull() ?: 0
            var slotRowIdx = 0

            for (rowIdx in grid.indices) {
                if (rowIdx in structure.headerRowIndices) continue
                val row = grid[rowIdx]
                val leadingTexts = (0 until minDayCol).mapNotNull { c ->
                    row.getOrNull(c)?.element?.text()?.trim()?.takeIf { it.isNotEmpty() }
                }
                val rowNodeInfo = HtmlTableParsingSupport.parseLeadingRowNodeInfo(leadingTexts)
                val hasAnyCourseDiv = structure.colToDay.keys.any { colIdx ->
                    val cell = row.getOrNull(colIdx)?.takeIf { it.isOrigin }?.element ?: return@any false
                    cell.selectFirst("div.kbcontent, div.kbcontent1") != null
                }
                if (!hasAnyCourseDiv && rowNodeInfo == null && HtmlTableParsingSupport.isDecorativeBreakRow(leadingTexts)) {
                    continue
                }
                slotRowIdx++
                val defaultStartNode = rowNodeInfo?.first ?: ((slotRowIdx - 1) * 2 + 1)
                val defaultCount = rowNodeInfo?.second ?: 2

                for ((colIdx, day) in structure.colToDay) {
                    val cell = row.getOrNull(colIdx) ?: continue
                    if (!cell.isOrigin) continue
                    val kbDiv = cell.element.selectFirst("div.kbcontent")?.takeIf { it.text().isNotBlank() }
                        ?: cell.element.selectFirst("div.kbcontent1")
                        ?: continue
                    val blocks = kbDiv.html().split(Regex("-{5,}|<hr\\b[^>]*>", RegexOption.IGNORE_CASE))
                    for (blockHtml in blocks) {
                        val bDoc = Ksoup.parse(blockHtml)
                        val fullText = bDoc.text().trim()
                        if (fullText.isEmpty() || fullText == "&nbsp;") continue

                        val bodyHtml = bDoc.body().html()
                            .replace(Regex("^(\\s|<br\\s*/?>|&nbsp;)+", RegexOption.IGNORE_CASE), "")
                        val rawFirstLine = bodyHtml.substringBefore("<br").let { Ksoup.parse(it).text().trim() }
                            .ifEmpty { fullText.substringBefore(" ") }
                        val (name, inlineMeta) = HtmlTableParsingSupport.cleanCourseNameAndInlineMeta(rawFirstLine)
                        if (name.isEmpty() || HtmlTableParsingSupport.isNonCourseHeaderText(name)) continue

                        var teacher = bDoc.selectFirst("font[title*=老师], font[title*=教师]")?.text()?.trim().orEmpty()
                        val weekNodeText = bDoc.selectFirst("font[title*=周次], font[title*=周], font[title*=节]")?.text()?.trim().orEmpty()
                        var room = bDoc.selectFirst("font[title*=教室], font[title*=地点]")?.text()?.trim().orEmpty()

                        val plainLines = HtmlTableParsingSupport.htmlFragmentToLines(bodyHtml)
                        val combinedTime = listOf(weekNodeText, inlineMeta).filter { it.isNotBlank() }.joinToString(" ")
                            .ifEmpty {
                                plainLines.firstOrNull {
                                    CourseGroupingHelper.extractWeeksFromMixedText(it) != null ||
                                        Regex("\\d+\\s*[-~至,]\\s*\\d+\\s*节").containsMatchIn(it)
                                }.orEmpty()
                            }

                        if (room.isEmpty() || teacher.isEmpty()) {
                            val (inferredRoom, inferredTeacher) = HtmlTableParsingSupport.classifyRoomAndTeacher(
                                lines = plainLines,
                                nameLine = rawFirstLine,
                                weekLine = weekNodeText
                            )
                            if (room.isEmpty()) room = inferredRoom
                            if (teacher.isEmpty()) teacher = inferredTeacher
                        }

                        val (sNode, count) = extractNodeRangeFromText(combinedTime, defaultStartNode, defaultCount)
                        val weeks = CourseGroupingHelper.extractWeeksFromMixedText(combinedTime)
                            ?: CourseGroupingHelper.parseWeeksText(weekNodeText.substringBefore("["))
                        raw += RawParsedSession(name, day, sNode, count, weeks, room, teacher)
                    }
                }
            }
        }
        val courses = CourseGroupingHelper.groupSessions(raw)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = "强智教务导入",
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }

    companion object {
        const val ID = "qiangzhi"
    }
}

/**
 * 3. 新 URP 教务系统解析器 (支持 URP JSON `dateList`/`selectCourseList`/`timeAndPlaceList`)
 */
class NewUrpParser : ScheduleParser {
    override val id: String = ID
    override val displayName: String = "新 URP 教务"
    override val systemFamily: AcademicSystemFamily = AcademicSystemFamily.NEW_URP

    override fun canParse(source: String): Boolean =
        source.contains("timeAndPlaceList") || source.contains("selectCourseList")

    override fun parse(source: String): ParsedScheduleResult {
        val raw = mutableListOf<RawParsedSession>()
        val root = lenientJson.parseToJsonElement(source.trim()).jsonObject
        val courseLists = mutableListOf<JsonArray>()
        root["dateList"]?.jsonArray?.forEach { d ->
            d.jsonObject["selectCourseList"]?.jsonArray?.let { courseLists += it }
        }
        root["selectCourseList"]?.jsonArray?.let { courseLists += it }

        for (arr in courseLists) {
            for (itemEl in arr) {
                val item = itemEl.jsonObject
                val name = item.str("courseName") ?: continue
                val teacher = item.str("attendClassTeacher").orEmpty().trim('*', ' ')
                val credit = item.str("unit")?.toFloatOrNull() ?: 0f
                val places = item["timeAndPlaceList"]?.jsonArray ?: continue
                for (pEl in places) {
                    val p = pEl.jsonObject
                    val day = p.int("classDay") ?: continue
                    val startNode = p.int("classSessions") ?: 1
                    val step = p.int("continuingSession") ?: 2
                    val building = p.str("teachingBuildingName").orEmpty()
                    val classroom = p.str("classroomName").orEmpty()
                    val room = "$building$classroom".trim()
                    val classWeekBitmap = p.str("classWeek").orEmpty()
                    val weeks = if (classWeekBitmap.isNotEmpty() && classWeekBitmap.all { it == '0' || it == '1' }) {
                        val set = classWeekBitmap.mapIndexedNotNull { idx, c -> if (c == '1') idx + 1 else null }.toSet()
                        WeekSet(set)
                    } else {
                        CourseGroupingHelper.parseWeeksText(p.str("weekDescription").orEmpty())
                    }
                    raw += RawParsedSession(name, day, startNode, step, weeks, room, teacher, credit = credit)
                }
            }
        }
        val courses = CourseGroupingHelper.groupSessions(raw)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = "新 URP 教务导入",
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }

    companion object {
        const val ID = "new_urp"
    }
}

/**
 * 4. 金智 / 树维教务系统解析器 (支持金智 `datas.xskcb.rows` JSON)
 */
class JinZhiParser : ScheduleParser {
    override val id: String = ID
    override val displayName: String = "金智 / 树维教务"
    override val systemFamily: AcademicSystemFamily = AcademicSystemFamily.JINZHI

    override fun canParse(source: String): Boolean =
        source.contains("\"xskcb\"") || source.contains("\"KCM\"") || source.contains("\"SKXQ\"")

    override fun parse(source: String): ParsedScheduleResult {
        val raw = mutableListOf<RawParsedSession>()
        val root = lenientJson.parseToJsonElement(source.trim()).jsonObject
        val rows = root["datas"]?.jsonObject?.get("xskcb")?.jsonObject?.get("rows")?.jsonArray
            ?: root["rows"]?.jsonArray
            ?: JsonArray(emptyList())
        for (el in rows) {
            val obj = el.jsonObject
            val name = obj.str("KCM") ?: obj.str("KCMC") ?: continue
            val day = obj.int("SKXQ") ?: obj.str("SKXQ")?.toIntOrNull() ?: continue
            val startNode = obj.int("KSJC") ?: obj.str("KSJC")?.toIntOrNull() ?: 1
            val endNode = obj.int("JSJC") ?: obj.str("JSJC")?.toIntOrNull() ?: (startNode + 1)
            val step = (endNode - startNode + 1).coerceAtLeast(1)
            val skzc = obj.str("SKZC").orEmpty()
            val weeks = if (skzc.isNotEmpty() && skzc.all { it == '0' || it == '1' }) {
                WeekSet(skzc.mapIndexedNotNull { i, c -> if (c == '1') i + 1 else null }.toSet())
            } else {
                CourseGroupingHelper.parseWeeksText(obj.str("ZCMC").orEmpty())
            }
            val room = obj.str("JASMC").orEmpty()
            val teacher = obj.str("SKJS").orEmpty()
            raw += RawParsedSession(name, day, startNode, step, weeks, room, teacher)
        }
        val courses = CourseGroupingHelper.groupSessions(raw)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = "金智教务导入",
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }

    companion object {
        const val ID = "jinzhi"
    }
}

/**
 * 5. 青果 / 新青果教务系统解析器 (HTML 表格与文本块解析)
 */
class KingosoftParser : ScheduleParser {
    override val id: String = ID
    override val displayName: String = "青果 / 新青果教务"
    override val systemFamily: AcademicSystemFamily = AcademicSystemFamily.KINGOSOFT

    override fun canParse(source: String): Boolean =
        source.contains("kingosoft", ignoreCase = true) ||
            source.contains("kcb_table") ||
            source.contains("div_nokb")

    override fun parse(source: String): ParsedScheduleResult {
        val doc = Ksoup.parse(source)
        val raw = mutableListOf<RawParsedSession>()
        val table = HtmlTableParsingSupport.findBestScheduleTable(
            doc = doc,
            preferredSelectors = listOf("table#kcb_table", "table#mytable")
        )
        if (table != null) {
            val grid = HtmlTableParsingSupport.buildTableGrid(table)
            val structure = HtmlTableParsingSupport.detectHeaderAndDayColumns(grid)
            val minDayCol = structure.colToDay.keys.minOrNull() ?: 0
            val defaultRowStep = HtmlTableParsingSupport.inferDefaultRowStep(grid, structure.headerRowIndices)
            var rowNode = 1

            for (rowIdx in grid.indices) {
                if (rowIdx in structure.headerRowIndices) continue
                val row = grid[rowIdx]
                val leadingTexts = (0 until minDayCol).mapNotNull { c ->
                    row.getOrNull(c)?.element?.text()?.trim()?.takeIf { it.isNotEmpty() }
                }
                val rowNodeInfo = HtmlTableParsingSupport.parseLeadingRowNodeInfo(leadingTexts)
                if (rowNodeInfo != null) {
                    rowNode = rowNodeInfo.first
                }
                val rowDefaultSpan = rowNodeInfo?.second ?: defaultRowStep

                var rowProduced = 0
                for ((colIdx, day) in structure.colToDay) {
                    val cell = row.getOrNull(colIdx) ?: continue
                    if (!cell.isOrigin) continue
                    for (lines in HtmlTableParsingSupport.extractCellBlocksAndLines(cell.element)) {
                        if (lines.isEmpty()) continue
                        val (name, inlineMeta) = HtmlTableParsingSupport.cleanCourseNameAndInlineMeta(lines[0])
                        if (name.isEmpty() || HtmlTableParsingSupport.isNonCourseHeaderText(name)) continue

                        val infoLine = lines.drop(1).firstOrNull {
                            CourseGroupingHelper.extractWeeksFromMixedText(it) != null ||
                                Regex("\\d+\\s*[-~至,]\\s*\\d+\\s*节").containsMatchIn(it)
                        }.orEmpty()
                        val combinedTime = listOf(infoLine, inlineMeta).filter { it.isNotBlank() }.joinToString(" ")

                        val defaultCount = if (rowDefaultSpan == 1) cell.rowspan else cell.rowspan * rowDefaultSpan
                        val (sNode, count) = extractNodeRangeFromText(combinedTime, rowNode, defaultCount)
                        val weeks = CourseGroupingHelper.extractWeeksFromMixedText(combinedTime)
                            ?: WeekSet.range(1, 16)
                        val (room, teacher) = HtmlTableParsingSupport.classifyRoomAndTeacher(
                            lines = lines,
                            nameLine = lines[0],
                            weekLine = infoLine
                        )
                        raw += RawParsedSession(name, day, sNode, count, weeks, room, teacher)
                        rowProduced++
                    }
                }
                if (rowProduced > 0 || rowNodeInfo != null || !HtmlTableParsingSupport.isDecorativeBreakRow(leadingTexts)) {
                    rowNode += rowDefaultSpan
                }
            }
        }
        val courses = CourseGroupingHelper.groupSessions(raw)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = "青果教务导入",
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }

    companion object {
        const val ID = "kingosoft"
    }
}

/**
 * 6. 通用 HTML 课表表格解析器（兜底）
 */
class GenericHtmlTableParser : ScheduleParser {
    override val id: String = ID
    override val displayName: String = "通用 HTML 课表"
    override val systemFamily: AcademicSystemFamily = AcademicSystemFamily.GENERIC_HTML

    override fun canParse(source: String): Boolean = source.contains("<table", ignoreCase = true)

    override fun parse(source: String): ParsedScheduleResult {
        val doc = Ksoup.parse(source)
        val raw = mutableListOf<RawParsedSession>()
        val bestTable = HtmlTableParsingSupport.findBestScheduleTable(
            doc = doc,
            preferredSelectors = listOf(
                "#manualArrangeCourseTable",
                "#courseTable",
                "#kbgrid_table_0",
                "#Table1",
                "#kbtable",
                "#kcb_table"
            )
        )
        if (bestTable != null) {
            val grid = HtmlTableParsingSupport.buildTableGrid(bestTable)
            val structure = HtmlTableParsingSupport.detectHeaderAndDayColumns(grid)
            val minDayCol = structure.colToDay.keys.minOrNull() ?: 0
            val defaultRowStep = HtmlTableParsingSupport.inferDefaultRowStep(grid, structure.headerRowIndices)

            var currentNode = 1
            for (rowIdx in grid.indices) {
                if (rowIdx in structure.headerRowIndices) continue
                val row = grid[rowIdx]
                val leadingTexts = (0 until minDayCol).mapNotNull { c ->
                    row.getOrNull(c)?.element?.text()?.trim()?.takeIf { it.isNotEmpty() }
                }
                val rowNodeInfo = HtmlTableParsingSupport.parseLeadingRowNodeInfo(leadingTexts)
                if (rowNodeInfo != null) {
                    currentNode = rowNodeInfo.first
                }
                val rowDefaultSpan = rowNodeInfo?.second ?: defaultRowStep

                var rowProduced = 0
                for ((colIdx, day) in structure.colToDay) {
                    val cell = row.getOrNull(colIdx) ?: continue
                    if (!cell.isOrigin) continue
                    for (lines in HtmlTableParsingSupport.extractCellBlocksAndLines(cell.element)) {
                        if (lines.isEmpty()) continue
                        val (name, inlineMeta) = HtmlTableParsingSupport.cleanCourseNameAndInlineMeta(lines[0])
                        if (name.isEmpty() || HtmlTableParsingSupport.isNonCourseHeaderText(name)) continue

                        val weekLine = lines.drop(1).firstOrNull {
                            CourseGroupingHelper.extractWeeksFromMixedText(it) != null
                        }.orEmpty()
                        val combinedTime = listOf(lines.drop(1).joinToString(" "), inlineMeta)
                            .filter { it.isNotBlank() }
                            .joinToString(" ")
                        val weeks = CourseGroupingHelper.extractWeeksFromMixedText(combinedTime)
                            ?: WeekSet.range(1, 16)

                        val defaultCount = if (rowDefaultSpan == 1) cell.rowspan else cell.rowspan * rowDefaultSpan
                        val (sNode, count) = extractNodeRangeFromText(combinedTime, currentNode, defaultCount)
                        val (room, teacher) = HtmlTableParsingSupport.classifyRoomAndTeacher(
                            lines = lines,
                            nameLine = lines[0],
                            weekLine = weekLine
                        )
                        raw += RawParsedSession(name, day, sNode, count, weeks, room, teacher)
                        rowProduced++
                    }
                }
                if (rowProduced > 0 || rowNodeInfo != null || !HtmlTableParsingSupport.isDecorativeBreakRow(leadingTexts)) {
                    currentNode += rowDefaultSpan
                }
            }
        }
        val courses = CourseGroupingHelper.groupSessions(raw)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = "HTML 导入课表",
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }

    companion object {
        const val ID = "generic_html"
    }
}

private object HtmlTableParsingSupport {

    data class GridCell(
        val element: Element,
        val rowIndex: Int,
        val colIndex: Int,
        val rowspan: Int,
        val colspan: Int,
        val isOrigin: Boolean
    )

    data class TableStructure(
        val headerRowIndices: Set<Int>,
        val colToDay: Map<Int, Int>
    )

    private val nonCourseExactLabels = setOf(
        "时间", "节次", "节数", "课节", "课程表", "课表", "星期", "日期", "周次",
        "上午", "下午", "晚上", "早晨", "早读", "早自习", "午休", "中午", "晚间",
        "备注", "说明", "无", "空", "调停课", "未安排时间", "未排时间", "实践环节",
        "星期/节次", "节次/星期", "时间/星期", "星期\\节次", "节次\\时间", "合计", "其它"
    )

    private val roomKeywords = listOf(
        "楼", "室", "教", "馆", "场", "栋", "座", "区", "实验", "机房", "操场",
        "中心", "校区", "教学", "大厅", "阶梯", "#"
    )

    private val chineseDigits = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10
    )

    fun directRowsOf(table: Element): List<Element> {
        return table.select("tr").filter { tr ->
            tr.parents().firstOrNull { it.tagName().equals("table", true) } === table
        }
    }

    fun directCellsOf(row: Element): List<Element> {
        return row.children().filter {
            val tag = it.tagName()
            tag.equals("td", ignoreCase = true) || tag.equals("th", ignoreCase = true)
        }
    }

    fun findBestScheduleTable(
        doc: Document,
        preferredSelectors: List<String> = emptyList()
    ): Element? {
        for (selector in preferredSelectors) {
            val matches = doc.select(selector).filter { table ->
                val rows = directRowsOf(table)
                rows.isNotEmpty() && rows.any { it.text().isNotBlank() }
            }
            if (matches.isNotEmpty()) {
                return matches.maxByOrNull { scoreTable(it) }
            }
        }
        val allTables = doc.select("table").filter { table ->
            val rows = directRowsOf(table)
            rows.isNotEmpty() && rows.any { it.text().isNotBlank() }
        }
        return allTables.maxByOrNull { scoreTable(it) }
    }

    private fun scoreTable(table: Element): Int {
        val rows = directRowsOf(table)
        if (rows.isEmpty()) return Int.MIN_VALUE

        var maxDirectCols = 0
        var bestHeaderWeekdays = 0
        for (row in rows) {
            val cells = directCellsOf(row)
            maxDirectCols = maxOf(maxDirectCols, cells.size)
            val daysInRow = cells.mapNotNull { isWeekdayHeaderCell(it.text()) }.toSet().size
            if (daysInRow > bestHeaderWeekdays) bestHeaderWeekdays = daysInRow
        }

        val hasNestedScheduleTable = table.select("table").any { child ->
            if (child === table) return@any false
            val cRows = directRowsOf(child)
            if (cRows.isEmpty()) return@any false
            val childHeaderDays = cRows.maxOfOrNull { r ->
                directCellsOf(r).mapNotNull { isWeekdayHeaderCell(it.text()) }.toSet().size
            } ?: 0
            childHeaderDays >= 4 || (cRows.size >= 4 && cRows.any { directCellsOf(it).size >= 6 })
        }

        val text = table.text()
        var signalCount = 0
        if (table.selectFirst("div.timetable_con, div.kbcontent, div.kbcontent1") != null) signalCount += 15
        signalCount += minOf(table.select("td[rowspan]").size, 20)
        if (text.contains("周")) signalCount += 5
        if (text.contains("节")) signalCount += 5

        var score = 0
        score += bestHeaderWeekdays * 500
        if (bestHeaderWeekdays >= 5) score += 2000
        if (maxDirectCols >= 6) score += 300
        score += minOf(rows.size, 25) * 40
        score += signalCount * 20
        score += minOf(text.length, 4000) / 100
        if (hasNestedScheduleTable) score -= 100_000
        return score
    }

    fun buildTableGrid(table: Element): List<List<GridCell?>> {
        val rows = directRowsOf(table)
        val occupancy = mutableListOf<MutableList<GridCell?>>()

        for (rIdx in rows.indices) {
            while (occupancy.size <= rIdx) occupancy.add(mutableListOf())
            var cCursor = 0
            for (cellEl in directCellsOf(rows[rIdx])) {
                while (cCursor < occupancy[rIdx].size && occupancy[rIdx][cCursor] != null) {
                    cCursor++
                }
                val rs = (cellEl.attr("rowspan").toIntOrNull() ?: 1).coerceIn(1, 30)
                val cs = (cellEl.attr("colspan").toIntOrNull() ?: 1).coerceIn(1, 20)
                for (dr in 0 until rs) {
                    val targetRow = rIdx + dr
                    while (occupancy.size <= targetRow) occupancy.add(mutableListOf())
                    while (occupancy[targetRow].size < cCursor + cs) occupancy[targetRow].add(null)
                    for (dc in 0 until cs) {
                        if (occupancy[targetRow][cCursor + dc] == null) {
                            occupancy[targetRow][cCursor + dc] = GridCell(
                                element = cellEl,
                                rowIndex = rIdx,
                                colIndex = cCursor,
                                rowspan = rs,
                                colspan = cs,
                                isOrigin = (dr == 0 && dc == 0)
                            )
                        }
                    }
                }
                cCursor += cs
            }
        }

        val maxCols = occupancy.maxOfOrNull { it.size } ?: 0
        for (row in occupancy) {
            while (row.size < maxCols) row.add(null)
        }
        return occupancy
    }

    fun isWeekdayHeaderCell(rawText: String): Int? {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty() || trimmed.length > 24) return null
        if (trimmed.contains("节") && !trimmed.contains("时间")) return null
        val compact = trimmed
            .replace(Regex("[\\s\\u00A0]+"), "")
            .replace(Regex("[（(][^）)]*[）)]"), "")
            .replace(Regex("\\d{1,4}[-/.年]\\d{1,2}(?:[-/.月]\\d{1,2}日?)?"), "")
            .trim()
        return when {
            compact in listOf("周一", "星期一", "礼拜一") || compact.equals("Mon", true) || compact.equals("Monday", true) -> 1
            compact in listOf("周二", "星期二", "礼拜二") || compact.equals("Tue", true) || compact.equals("Tuesday", true) -> 2
            compact in listOf("周三", "星期三", "礼拜三") || compact.equals("Wed", true) || compact.equals("Wednesday", true) -> 3
            compact in listOf("周四", "星期四", "礼拜四") || compact.equals("Thu", true) || compact.equals("Thursday", true) -> 4
            compact in listOf("周五", "星期五", "礼拜五") || compact.equals("Fri", true) || compact.equals("Friday", true) -> 5
            compact in listOf("周六", "星期六", "礼拜六") || compact.equals("Sat", true) || compact.equals("Saturday", true) -> 6
            compact in listOf("周日", "周天", "星期日", "星期天", "礼拜日", "礼拜天") ||
                compact.equals("Sun", true) || compact.equals("Sunday", true) -> 7
            else -> null
        }
    }

    fun isNonCourseHeaderText(rawText: String): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.length <= 1) return true
        if (isWeekdayHeaderCell(trimmed) != null) return true
        val normalized = trimmed
            .replace(Regex("[\\s\\u00A0:：]+"), "")
            .trim()
        if (normalized in nonCourseExactLabels) return true
        if (Regex("^第?[一二三四五六七八九十\\d]{1,2}(?:\\s*[-~至,、]\\s*\\d{1,2})?大?节$").matches(normalized)) return true
        if (Regex("^\\d{1,2}(?:-\\d{1,2})?$").matches(normalized)) return true
        if (Regex("^(?:星期|周)[一二三四五六日天]$").matches(normalized)) return true
        return false
    }

    fun detectHeaderAndDayColumns(grid: List<List<GridCell?>>): TableStructure {
        val headerRows = mutableSetOf<Int>()
        val colToDay = linkedMapOf<Int, Int>()

        for (rIdx in grid.indices) {
            val row = grid[rIdx]
            val rowDayCols = mutableListOf<Pair<Int, Int>>()
            for (cIdx in row.indices) {
                val cell = row[cIdx] ?: continue
                if (!cell.isOrigin) continue
                val day = isWeekdayHeaderCell(cell.element.text())
                if (day != null) {
                    rowDayCols += cIdx to day
                }
            }
            if (rowDayCols.distinctBy { it.second }.size >= 3) {
                headerRows += rIdx
                if (colToDay.isEmpty()) {
                    for ((col, day) in rowDayCols) {
                        colToDay.putIfAbsent(col, day)
                    }
                }
            }
        }

        // Also treat immediate secondary date sub-header rows right after header row (e.g., "11/23 | 11/24 ...")
        if (headerRows.isNotEmpty()) {
            val nextRowIdx = (headerRows.maxOrNull() ?: 0) + 1
            if (nextRowIdx in grid.indices && nextRowIdx !in headerRows) {
                val nonBlankDayCells = colToDay.keys.mapNotNull { c ->
                    grid[nextRowIdx].getOrNull(c)?.takeIf { it.isOrigin }?.element?.text()?.trim()?.takeIf { it.isNotEmpty() }
                }
                if (nonBlankDayCells.isNotEmpty() && nonBlankDayCells.all {
                        Regex("^\\d{1,2}[/-月]\\d{1,2}日?$").matches(it) || isWeekdayHeaderCell(it) != null
                    }
                ) {
                    headerRows += nextRowIdx
                }
            }
        }

        if (colToDay.isNotEmpty()) {
            return TableStructure(headerRows, colToDay)
        }

        // Fallback when snippet HTML omits <thead> / weekday header row (e.g., unit test fixtures)
        val totalCols = grid.maxOfOrNull { it.size } ?: 0
        val leadingCount = when {
            totalCols >= 7 -> totalCols - 7
            totalCols == 6 -> 1
            else -> 0
        }
        for (c in leadingCount until totalCols) {
            val day = c - leadingCount + 1
            if (day in 1..7) colToDay[c] = day
        }
        return TableStructure(headerRows, colToDay)
    }

    fun inferDefaultRowStep(grid: List<List<GridCell?>>, headerRows: Set<Int>): Int {
        val dataRows = grid.indices.count { rIdx ->
            if (rIdx in headerRows) return@count false
            val texts = grid[rIdx].mapNotNull { it?.takeIf { c -> c.isOrigin }?.element?.text()?.trim() }
            texts.any { it.isNotEmpty() } && !isDecorativeBreakRow(texts.take(2))
        }
        return if (dataRows >= 8) 1 else 2
    }

    fun isDecorativeBreakRow(leadingTexts: List<String>): Boolean {
        if (leadingTexts.isEmpty()) return false
        val joined = leadingTexts.joinToString(" ")
        return joined.contains("午休") ||
            joined.contains("中午") ||
            joined.contains("早晨") ||
            joined.contains("早自习") ||
            joined.contains("早读") ||
            joined.contains("备注") ||
            joined.contains("调停课") ||
            joined.contains("未安排") ||
            joined.contains("合计")
    }

    fun parseLeadingRowNodeInfo(leadingTexts: List<String>): Pair<Int, Int>? {
        val clockTimeRegex = Regex("\\d{1,2}\\s*[:：]\\s*\\d{2}(?:\\s*[-~至—－]\\s*\\d{1,2}\\s*[:：]\\s*\\d{2})?")
        for (rawTxt in leadingTexts) {
            val txt = rawTxt.replace(clockTimeRegex, " ").trim()
            if (txt.isEmpty()) continue
            val rangeMatch = Regex("(\\d{1,2})\\s*[-~至,、]\\s*(\\d{1,2})").find(txt)
            if (rangeMatch != null) {
                val s = rangeMatch.groupValues[1].toInt()
                val e = rangeMatch.groupValues[2].toInt().coerceAtLeast(s)
                if (s in 1..20 && e in s..20) return s to (e - s + 1)
            }
            val chineseMatch = Regex("第(十[一二]|[一二三四五六七八九十])(大?)节").find(txt)
            if (chineseMatch != null) {
                val token = chineseMatch.groupValues[1]
                val num = when (token) {
                    "十一" -> 11
                    "十二" -> 12
                    else -> chineseDigits[token.firstOrNull()]
                } ?: continue
                return if (chineseMatch.groupValues[2] == "大") {
                    ((num - 1) * 2 + 1) to 2
                } else {
                    num to 1
                }
            }
            val singleNodeMatch = Regex("第\\s*(\\d{1,2})\\s*节").find(txt)
            if (singleNodeMatch != null) {
                val n = singleNodeMatch.groupValues[1].toInt()
                if (n in 1..20) return n to 1
            }
            val bareNum = txt.toIntOrNull()
            if (bareNum != null && bareNum in 1..20) {
                return bareNum to 1
            }
        }
        return null
    }

    fun extractCellBlocksAndLines(cell: Element): List<List<String>> {
        val childBlocks = cell.select("div.timetable_con, div.kbcontent, div.kbcontent1, div.course-item, div.kb_item")
            .filter { it.text().isNotBlank() }
        if (childBlocks.isNotEmpty()) {
            return childBlocks.map { htmlFragmentToLines(it.html()) }.filter { it.isNotEmpty() }
        }

        val rawHtml = cell.html()
        val fragments = rawHtml.split(
            Regex("<hr\\b[^>]*>|-{5,}|<br\\s*/?>\\s*(?:&nbsp;|\\u00A0|\\s)*<br\\s*/?>", RegexOption.IGNORE_CASE)
        )
        return fragments.map { htmlFragmentToLines(it) }.filter { it.isNotEmpty() }
    }

    fun htmlFragmentToLines(fragmentHtml: String): List<String> {
        val withNewlines = fragmentHtml
            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("</(?:p|div|li|tr|h[1-6])>", RegexOption.IGNORE_CASE), "\n")
        val parsedText = Ksoup.parse(withNewlines).wholeText()
        return parsedText
            .replace('\u00A0', ' ')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "&nbsp;" }
    }

    fun cleanCourseNameAndInlineMeta(rawFirstLine: String): Pair<String, String> {
        val trimmed = rawFirstLine.trim()
        if (trimmed.isEmpty()) return "" to ""
        val metaRegex = Regex(
            "[(\\[（【][^\\])）】]*(?:周|节)[^\\])）】]*[\\])）】]|\\[\\s*\\d+\\s*[-~至—－]\\s*\\d+(?:\\s*,\\s*\\d+(?:\\s*[-~至—－]\\s*\\d+)?)*\\s*\\]|\\{[^}]*\\}"
        )
        val matches = metaRegex.findAll(trimmed).map { it.value }.toList()
        if (matches.isEmpty()) return trimmed to ""
        val cleaned = metaRegex.replace(trimmed, " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        return cleaned to matches.joinToString(" ")
    }

    fun stripMetadataPrefix(text: String): String {
        return text
            .replace(Regex("^(?:上课地点|上课教室|教室|地点|场地|授课教师|任课教师|教师|老师)\\s*[:：]\\s*"), "")
            .trim()
    }

    fun classifyRoomAndTeacher(
        lines: List<String>,
        nameLine: String,
        weekLine: String
    ): Pair<String, String> {
        var room = ""
        var teacher = ""
        val candidates = mutableListOf<String>()

        for (line in lines) {
            if (line == nameLine || line == weekLine || line.isBlank()) continue
            if (line.contains("教室") || line.contains("上课地点") || line.contains("地点") || line.contains("场地")) {
                if (room.isEmpty()) room = stripMetadataPrefix(line)
                continue
            }
            if (line.contains("教师") || line.contains("老师")) {
                if (teacher.isEmpty()) teacher = stripMetadataPrefix(line)
                continue
            }
            // Skip lines that are purely week/node specs
            if (isPureWeekOrNodeLine(line)) continue
            candidates += line
        }

        for (cand in candidates) {
            if (room.isEmpty() && looksLikeRoom(cand)) {
                room = stripMetadataPrefix(cand)
            } else if (teacher.isEmpty() && looksLikeTeacher(cand)) {
                teacher = stripMetadataPrefix(cand)
            }
        }

        // Last-resort fallback for unlabeled extra lines
        if (room.isEmpty() && teacher.isEmpty() && candidates.size >= 2) {
            teacher = candidates[0]
            room = candidates.last()
        } else if (room.isEmpty() && candidates.size == 1 && candidates[0] != teacher) {
            room = candidates[0]
        } else if (teacher.isEmpty() && candidates.size == 1 && candidates[0] != room) {
            teacher = candidates[0]
        }
        return room to teacher
    }

    private fun isPureWeekOrNodeLine(line: String): Boolean {
        val stripped = line
            .replace(Regex("周[一二三四五六日天]"), "")
            .replace(Regex("第?\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*[节周]"), "")
            .replace(Regex("[(\\[（【{}\\])）】|\\s,，、;；单双-]"), "")
        return stripped.isEmpty()
    }

    private fun looksLikeRoom(text: String): Boolean {
        if (roomKeywords.any { text.contains(it) }) return true
        return Regex("[A-Za-z]?\\d+[-－—]\\d+|\\b\\d{3,4}\\b").containsMatchIn(text)
    }

    private fun looksLikeTeacher(text: String): Boolean {
        if (text.any { it.isDigit() }) return false
        if (text.contains("周") && text.length > 4) return false
        if (text.contains("节")) return false
        return text.length in 2..16
    }
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
private fun JsonObject.int(key: String): Int? = this[key]?.jsonPrimitive?.intOrNull

private fun parseNodeRange(raw: String): Pair<Int, Int> {
    val nums = Regex("\\d+").findAll(raw).map { it.value.toInt() }.toList()
    if (nums.isEmpty()) return 1 to 2
    val start = nums.first()
    val end = nums.last().coerceAtLeast(start)
    return start to (end - start + 1)
}

private fun extractNodeRangeFromText(text: String, defaultStart: Int, defaultCount: Int = 2): Pair<Int, Int> {
    val listMatch = Regex("(\\d+(?:\\s*[-~至,、]\\s*\\d+)+)\\s*节").find(text)
    if (listMatch != null) {
        val nums = Regex("\\d+").findAll(listMatch.groupValues[1]).map { it.value.toInt() }.toList()
        if (nums.size >= 2) {
            val s = nums.first()
            val e = nums.last().coerceAtLeast(s)
            if (s in 1..20 && e in s..20) return s to (e - s + 1)
        }
    }
    val afterWeekBracketMatch = Regex("周\\s*[\\[(（【]\\s*(\\d+)\\s*[-~至,]\\s*(\\d+)\\s*[\\])）】]").find(text)
    if (afterWeekBracketMatch != null) {
        val s = afterWeekBracketMatch.groupValues[1].toInt()
        val e = afterWeekBracketMatch.groupValues[2].toInt().coerceAtLeast(s)
        if (s in 1..20 && e in s..20) return s to (e - s + 1)
    }
    val singleMatch = Regex("第\\s*(\\d+)\\s*节").find(text)
    if (singleMatch != null) {
        val n = singleMatch.groupValues[1].toInt()
        if (n in 1..20) return n to 1
    }
    return defaultStart to defaultCount.coerceAtLeast(1)
}
