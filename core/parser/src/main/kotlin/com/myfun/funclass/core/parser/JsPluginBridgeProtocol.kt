package com.myfun.funclass.core.parser

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * WebView JS script injection protocol.
 * Compatible with community/custom adapter scripts returning standardized JSON payloads.
 */
object JsPluginBridgeProtocol {

    const val JS_INTERFACE_NAME = "FunClassJsBridge"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Serializable
    data class JsPluginCourseItem(
        val name: String,
        val day: Int,
        val startNode: Int,
        val step: Int = 2,
        val weeks: String = "1-16",
        val room: String = "",
        val teacher: String = "",
        val note: String = "",
        val credit: Float = 0f
    )

    @Serializable
    data class JsPluginSchedulePayload(
        val funclassPluginVersion: Int = 1,
        val tableName: String = "教务导入课表",
        val schoolName: String = "",
        val courses: List<JsPluginCourseItem> = emptyList()
    )

    /**
     * Default JS script injected into native WebView when user clicks "一键提取课表".
     * Collects main document + recursively accessible iframe/frame HTML so Kotlin parsers can inspect it cleanly.
     */
    val DEFAULT_WEBVIEW_EXTRACTOR_JS: String = """
        (function() {
            try {
                var htmlParts = [];
                var visitedDocs = [];
                function collectDoc(doc, depth) {
                    if (!doc || !doc.documentElement || depth > 4) return;
                    for (var k = 0; k < visitedDocs.length; k++) {
                        if (visitedDocs[k] === doc) return;
                    }
                    visitedDocs.push(doc);
                    htmlParts.push(doc.documentElement.outerHTML);
                    var frames = doc.querySelectorAll('iframe, frame');
                    for (var i = 0; i < frames.length; i++) {
                        try {
                            var childDoc = frames[i].contentDocument || (frames[i].contentWindow && frames[i].contentWindow.document);
                            if (childDoc) {
                                collectDoc(childDoc, depth + 1);
                            }
                        } catch (e) {}
                    }
                }
                collectDoc(document, 0);
                var combined = htmlParts.join('\n<!--FRAME_SPLIT-->\n');
                if (window.$JS_INTERFACE_NAME && window.$JS_INTERFACE_NAME.onHtmlExtracted) {
                    window.$JS_INTERFACE_NAME.onHtmlExtracted(combined);
                }
                return combined;
            } catch (err) {
                if (window.$JS_INTERFACE_NAME && window.$JS_INTERFACE_NAME.onError) {
                    window.$JS_INTERFACE_NAME.onError(String(err));
                }
                return '';
            }
        })();
    """.trimIndent()

    fun tryParsePayload(raw: String): ParsedScheduleResult? {
        if (!raw.contains("funclassPluginVersion")) return null
        val payload = runCatching { json.decodeFromString<JsPluginSchedulePayload>(raw) }.getOrNull() ?: return null
        val sessions = payload.courses.map { item ->
            RawParsedSession(
                courseName = item.name,
                dayOfWeek = item.day,
                startNode = item.startNode,
                nodeCount = item.step,
                weeks = CourseGroupingHelper.parseWeeksText(item.weeks),
                room = item.room,
                teacher = item.teacher,
                note = item.note,
                credit = item.credit
            )
        }
        val courses = CourseGroupingHelper.groupSessions(sessions)
        val (maxW, maxN) = CourseGroupingHelper.computeScheduleBounds(courses)
        return ParsedScheduleResult(
            tableName = payload.tableName,
            schoolName = payload.schoolName,
            courses = courses,
            maxWeeks = maxW,
            maxNodes = maxN
        )
    }
}
