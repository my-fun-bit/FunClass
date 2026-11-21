package com.myfun.funclass.core.parser

import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.CourseColorPalette
import com.myfun.funclass.core.model.CourseSession
import com.myfun.funclass.core.model.TimeScheme
import com.myfun.funclass.core.model.WeekSet
import kotlinx.serialization.Serializable

enum class AcademicSystemFamily(val displayName: String) {
    ZHENGFANG("正方 / 新正方教务"),
    KINGOSOFT("青果 / 新青果教务"),
    NEW_URP("新 URP 教务"),
    QIANGZHI("强智教务"),
    JINZHI("金智 / 树维教务"),
    JS_PLUGIN("自定义 JS 脚本"),
    GENERIC_HTML("通用 HTML 课表"),
    CSV_EXCEL("CSV / Excel 表格"),
    WAKEUP_FILE("兼容课表文件 (.wakeup_schedule)")
}

data class ParsedScheduleResult(
    val tableName: String = "导入课表",
    val schoolName: String = "",
    val courses: List<Course> = emptyList(),
    val timeScheme: TimeScheme? = null,
    val termStartDate: String? = null,
    val maxWeeks: Int = 20,
    val maxNodes: Int = 12,
    val sundayFirst: Boolean = false,
    val warnings: List<String> = emptyList()
)

interface ScheduleParser {
    val id: String
    val displayName: String
    val systemFamily: AcademicSystemFamily
    fun canParse(source: String): Boolean
    fun parse(source: String): ParsedScheduleResult
}

@Serializable
data class SchoolAdapterInfo(
    val id: String,
    val name: String,
    val pinyinInitial: String,
    val systemFamily: String,
    val loginUrl: String,
    val parserId: String,
    val customJsScript: String = ""
)

/**
 * Helper representation of a raw parsed course session before grouping by course name.
 */
data class RawParsedSession(
    val courseName: String,
    val dayOfWeek: Int,
    val startNode: Int,
    val nodeCount: Int,
    val weeks: WeekSet,
    val room: String = "",
    val teacher: String = "",
    val note: String = "",
    val credit: Float = 0f,
    val customStartTime: String = "",
    val customEndTime: String = ""
)

object CourseGroupingHelper {
    fun groupSessions(raw: List<RawParsedSession>): List<Course> {
        val grouped = linkedMapOf<String, MutableList<RawParsedSession>>()
        for (item in raw) {
            val cleanName = item.courseName.trim()
            if (cleanName.isEmpty()) continue
            grouped.getOrPut(cleanName) { mutableListOf() }.add(item)
        }
        return grouped.entries.map { (name, list) ->
            val uniqueSessions = list.distinctBy {
                Triple(
                    Triple(it.dayOfWeek, it.startNode, it.nodeCount),
                    it.weeks.encode(),
                    Pair(it.room.trim(), it.teacher.trim())
                )
            }
            val color = CourseColorPalette.colorForName(name)
            Course(
                name = name,
                colorHex = color,
                credit = list.maxOfOrNull { it.credit } ?: 0f,
                note = list.firstOrNull { it.note.isNotBlank() }?.note.orEmpty(),
                sessions = uniqueSessions.map { s ->
                    CourseSession(
                        dayOfWeek = s.dayOfWeek.coerceIn(1, 7),
                        startNode = s.startNode.coerceAtLeast(1),
                        nodeCount = s.nodeCount.coerceAtLeast(1),
                        weeks = if (s.weeks.isEmpty) WeekSet.range(1, 16) else s.weeks,
                        useCustomTime = s.customStartTime.isNotBlank() && s.customEndTime.isNotBlank(),
                        customStartTime = s.customStartTime,
                        customEndTime = s.customEndTime,
                        room = s.room.trim(),
                        teacher = s.teacher.trim(),
                        sessionNote = s.note.trim()
                    )
                }
            )
        }
    }

    /**
     * Computes (maxWeeks, maxNodes) adapting to actual course data while keeping sensible minimums.
     */
    fun computeScheduleBounds(
        courses: List<Course>,
        minWeeks: Int = 16,
        minNodes: Int = 12
    ): Pair<Int, Int> {
        val observedMaxWeek = courses.flatMap { it.sessions }.mapNotNull { it.weeks.maxWeek }.maxOrNull() ?: minWeeks
        val observedMaxNode = courses.flatMap { it.sessions }.maxOfOrNull { it.endNode } ?: minNodes
        return observedMaxWeek.coerceIn(minWeeks, 30) to observedMaxNode.coerceIn(minNodes, 20)
    }

    /**
     * Parses common Chinese academic week strings such as:
     * "1-16周", "1-15周(单)", "2-16(双)", "1-4,6,8-12周", "第1-8周", "1,3,5周", "{第1-16周|双周}".
     */
    fun parseWeeksText(raw: String): WeekSet {
        if (raw.isBlank()) return WeekSet.range(1, 16)
        val parsed = decodeWeeksOrNull(raw)
        return if (parsed == null || parsed.isEmpty) WeekSet.range(1, 16) else parsed
    }

    /**
     * Extracts weeks ONLY when genuine week expressions are present in mixed text
     * (e.g. "1-16周", "1-15(单周)", "{第1-16周|双周}", "周次：1-12").
     * Returns null when no explicit week span exists, preventing false matches on "周二", "周老师", or room numbers.
     */
    fun extractWeeksFromMixedText(raw: String): WeekSet? {
        if (raw.isBlank()) return null

        // 1. Classic ZhengFang brace notation containing digits + 周/第, e.g. "周一第1,2节{第1-16周|双周}"
        val braceMatch = Regex("\\{([^}]*\\d[^}]*)\\}").find(raw)
        if (braceMatch != null && braceMatch.groupValues[1].contains("周")) {
            val decoded = decodeWeeksOrNull(braceMatch.groupValues[1])
            if (decoded != null && !decoded.isEmpty) return decoded
        }

        // 2. Strip node ranges & weekday tokens first so "(1-2节)" or "周一" cannot interfere
        val stripped = stripNodeAndWeekdayTokens(raw)

        // 3. Explicit label form like "周次：1-16" / "周数: 1-4,6-12(单)"
        val labeledMatch = Regex("周[次数]?\\s*[:：]\\s*([^\\s]+(?:\\s*[-~至—－,，、;；]\\s*[^\\s]+)*)")
            .find(stripped)
        if (labeledMatch != null) {
            val candidate = labeledMatch.groupValues[1]
            if (candidate.any { it.isDigit() }) {
                val decoded = decodeWeeksOrNull(candidate)
                if (decoded != null && !decoded.isEmpty) return decoded
            }
        }

        // 4. General week spans where at least one 周 appears inside the matched span
        val segPattern = "(?:第\\s*)?\\d+(?:\\s*[-~至—－]\\s*\\d+)?(?:\\s*[\\(（\\[【]\\s*[单双]?周?\\s*[\\)）\\]】]|周)*"
        val chainRegex = Regex("$segPattern(?:\\s*[,，、;；]\\s*$segPattern)*")
        val weekMatches = chainRegex.findAll(stripped)
            .map { it.value.trim() }
            .filter { it.contains("周") }
            .toList()

        if (weekMatches.isEmpty()) return null
        val decoded = decodeWeeksOrNull(weekMatches.joinToString(","))
        return decoded?.takeIf { !it.isEmpty }
    }

    private fun stripNodeAndWeekdayTokens(raw: String): String {
        return raw
            .replace(Regex("[(\\[（【]\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*节\\s*[)\\]）】]"), " ")
            .replace(Regex("周[一二三四五六日天]第?\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*节"), " ")
            .replace(Regex("第?\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*节"), " ")
            .replace(Regex("星期[一二三四五六日天]|周[一二三四五六日天]"), " ")
    }

    private fun decodeWeeksOrNull(raw: String): WeekSet? {
        val braceContent = Regex("\\{([^}]+)\\}").find(raw)?.groupValues?.get(1)
        val source = braceContent ?: raw
            .replace(Regex("[(\\[（【]\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*节\\s*[)\\]）】]"), " ")
            .replace(Regex("周[一二三四五六日天]第?\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*节"), " ")
            .replace(Regex("第?\\s*\\d+(?:\\s*[-~至—－,，、]\\s*\\d+)*\\s*节"), " ")

        val normalized = source
            .replace("|", "")
            .replace("第", "")
            .replace("周次", "")
            .replace("周数", "")
            .replace("周", "")
            .replace("（", "(")
            .replace("）", ")")
            .replace("[", "(")
            .replace("]", ")")
            .replace("【", "(")
            .replace("】", ")")
            .replace("至", "-")
            .replace("~", "-")
            .replace("—", "-")
            .replace("－", "-")
            .trim()
        val parsed = WeekSet.decode(normalized)
        return parsed.takeIf { !it.isEmpty }
    }

    fun parseChineseDayOfWeek(raw: String): Int? {
        val t = raw.trim()
        t.toIntOrNull()?.takeIf { it in 1..7 }?.let { return it }
        return when {
            t.contains("一") || t.equals("Mon", true) -> 1
            t.contains("二") || t.equals("Tue", true) -> 2
            t.contains("三") || t.equals("Wed", true) -> 3
            t.contains("四") || t.equals("Thu", true) -> 4
            t.contains("五") || t.equals("Fri", true) -> 5
            t.contains("六") || t.equals("Sat", true) -> 6
            t.contains("日") || t.contains("天") || t.equals("Sun", true) -> 7
            else -> null
        }
    }
}

object ParserRegistry {
    val academicParsers: List<ScheduleParser> = listOf(
        ZhengFangParser(),
        QiangZhiParser(),
        NewUrpParser(),
        JinZhiParser(),
        KingosoftParser(),
        GenericHtmlTableParser()
    )

    val builtInSchools: List<SchoolAdapterInfo> = listOf(
        SchoolAdapterInfo("generic_zf", "正方 / 新正方通用教务", "Z", AcademicSystemFamily.ZHENGFANG.name, "", ZhengFangParser.ID),
        SchoolAdapterInfo("generic_qz", "强智通用教务", "Q", AcademicSystemFamily.QIANGZHI.name, "", QiangZhiParser.ID),
        SchoolAdapterInfo("generic_urp", "新 URP 通用教务", "U", AcademicSystemFamily.NEW_URP.name, "", NewUrpParser.ID),
        SchoolAdapterInfo("generic_jz", "金智 / 树维通用教务", "J", AcademicSystemFamily.JINZHI.name, "", JinZhiParser.ID),
        SchoolAdapterInfo("generic_kg", "青果 / 新青果通用教务", "Q", AcademicSystemFamily.KINGOSOFT.name, "", KingosoftParser.ID),
        SchoolAdapterInfo("suda", "苏州大学", "S", AcademicSystemFamily.ZHENGFANG.name, "https://jw.suda.edu.cn/", ZhengFangParser.ID),
        SchoolAdapterInfo("zju", "浙江大学", "Z", AcademicSystemFamily.ZHENGFANG.name, "https://zdbk.zju.edu.cn/", ZhengFangParser.ID),
        SchoolAdapterInfo("thu", "清华大学", "Q", AcademicSystemFamily.GENERIC_HTML.name, "https://zhjw.cic.tsinghua.edu.cn/", GenericHtmlTableParser.ID),
        SchoolAdapterInfo("pku", "北京大学", "B", AcademicSystemFamily.GENERIC_HTML.name, "https://elective.pku.edu.cn/", GenericHtmlTableParser.ID),
        SchoolAdapterInfo("sjtu", "上海交通大学", "S", AcademicSystemFamily.ZHENGFANG.name, "https://i.sjtu.edu.cn/", ZhengFangParser.ID),
        SchoolAdapterInfo("nju", "南京大学", "N", AcademicSystemFamily.JINZHI.name, "https://ehall.nju.edu.cn/", JinZhiParser.ID),
        SchoolAdapterInfo("whu", "武汉大学", "W", AcademicSystemFamily.ZHENGFANG.name, "https://bkjw.whu.edu.cn/", ZhengFangParser.ID),
        SchoolAdapterInfo("hust", "华中科技大学", "H", AcademicSystemFamily.JINZHI.name, "https://hub.hust.edu.cn/", JinZhiParser.ID),
        SchoolAdapterInfo("scu", "四川大学", "S", AcademicSystemFamily.NEW_URP.name, "http://zhjw.scu.edu.cn/", NewUrpParser.ID),
        SchoolAdapterInfo("jlu", "吉林大学", "J", AcademicSystemFamily.GENERIC_HTML.name, "https://uims.jlu.edu.cn/", GenericHtmlTableParser.ID),
        SchoolAdapterInfo("nwpu", "西北工业大学", "X", AcademicSystemFamily.JINZHI.name, "https://jwxt.nwpu.edu.cn/", JinZhiParser.ID),
        SchoolAdapterInfo("csu", "中南大学", "Z", AcademicSystemFamily.QIANGZHI.name, "https://csujwc.its.csu.edu.cn/", QiangZhiParser.ID),
        SchoolAdapterInfo("scut", "华南理工大学", "H", AcademicSystemFamily.ZHENGFANG.name, "https://jw.scut.edu.cn/", ZhengFangParser.ID),
        SchoolAdapterInfo("sysu", "中山大学", "Z", AcademicSystemFamily.JINZHI.name, "https://jwxt.sysu.edu.cn/", JinZhiParser.ID),
        SchoolAdapterInfo("fudan", "复旦大学", "F", AcademicSystemFamily.JINZHI.name, "https://jwfw.fudan.edu.cn/", JinZhiParser.ID)
    )

    fun findById(id: String): ScheduleParser? = academicParsers.firstOrNull { it.id == id }

    fun autoParse(source: String, preferredParserId: String? = null): ParsedScheduleResult {
        val trimmed = source.trim()
        // 1. Check JS plugin JSON payload
        JsPluginBridgeProtocol.tryParsePayload(trimmed)?.let { return it }

        // 2. Preferred parser first
        preferredParserId?.let { findById(it) }?.let { parser ->
            val res = runCatching { parser.parse(trimmed) }.getOrNull()
            if (res != null && res.courses.isNotEmpty()) return res
        }

        // 3. Auto-detect matching academic parser
        for (parser in academicParsers) {
            if (parser.canParse(trimmed)) {
                val res = runCatching { parser.parse(trimmed) }.getOrNull()
                if (res != null && res.courses.isNotEmpty()) return res
            }
        }

        // 4. Fallback try all parsers
        for (parser in academicParsers) {
            val res = runCatching { parser.parse(trimmed) }.getOrNull()
            if (res != null && res.courses.isNotEmpty()) return res
        }

        return ParsedScheduleResult(warnings = listOf("未能自动识别课表内容，请确认页面已进入课表界面或尝试切换教务类型"))
    }
}
