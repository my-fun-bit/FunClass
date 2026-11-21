package com.myfun.funclass.core.sync

import com.myfun.funclass.core.database.repository.ScheduleRepository
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.parser.ParsedScheduleResult
import com.myfun.funclass.core.parser.WakeupScheduleCodec
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pure-JVM offline share code codec (`FUNCLASS1:<gzip+url-safe-base64>`).
 */
object ShareCodeCodec {

    private val invisibleCharsRegex = Regex("[\\u200B-\\u200D\\uFEFF\\u2060]")
    private val strictInlineTokenRegex = Regex("FUNCLASS1\\s*:\\s*([A-Za-z0-9+/=_-]+)")
    private val wrappedInlineTokenRegex = Regex("FUNCLASS1\\s*:\\s*([A-Za-z0-9+/=_\\-\\r\\n]+)")

    fun encodePortableToken(wakeupPayload: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(wakeupPayload.toByteArray(Charsets.UTF_8)) }
        val b64 = Base64.getUrlEncoder().withoutPadding().encodeToString(bos.toByteArray())
        return "FUNCLASS1:$b64"
    }

    fun extractPortablePayload(rawInput: String): String? {
        val normalized = normalizeInput(rawInput)
        strictInlineTokenRegex.find(normalized)?.let { match ->
            decompressPortableToken(match.groupValues[1])?.let { return it }
        }
        wrappedInlineTokenRegex.find(normalized)?.let { match ->
            val cleaned = match.groupValues[1].replace(Regex("\\s+"), "")
            decompressPortableToken(cleaned)?.let { return it }
        }
        return null
    }

    fun decompressPortableToken(b64: String): String? = runCatching {
        val compact = b64.replace(Regex("\\s+"), "")
            .replace('+', '-')
            .replace('/', '_')
        val bytes = Base64.getUrlDecoder().decode(compact)
        GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).readText()
    }.getOrNull()

    fun normalizeInput(raw: String): String {
        val noInvisible = raw.replace(invisibleCharsRegex, "")
        val sb = StringBuilder(noInvisible.length)
        for (ch in noInvisible) {
            sb.append(
                when (ch) {
                    '\u3000' -> ' '
                    in '\uFF01'..'\uFF5E' -> (ch.code - 0xFEE0).toChar()
                    else -> ch
                }
            )
        }
        return sb.toString()
    }
}

@Singleton
class ShareCodeRepository @Inject constructor(
    private val scheduleRepository: ScheduleRepository
) {
    data class GeneratedShareOutput(
        val shareText: String,
        val portableToken: String,
        val wakeupFileContent: String
    )

    suspend fun generateShareForTable(tableId: Long): GeneratedShareOutput {
        val table = scheduleRepository.getTable(tableId)
            ?: error("未找到课表 #$tableId")
        val scheme = scheduleRepository.getTimeScheme(table.timeSchemeId)
            ?: DefaultTimeSchemes.standardCollege(table.timeSchemeId)
        val courses = scheduleRepository.getCourses(tableId)
        val payload = WakeupScheduleCodec.encode(table, scheme, courses)
        val portable = ShareCodeCodec.encodePortableToken(payload)
        val friendlyText = "我用 FunClass 分享了课表「${table.name}」，复制整段文字打开 FunClass 即可导入：\n$portable"
        return GeneratedShareOutput(
            shareText = friendlyText,
            portableToken = portable,
            wakeupFileContent = payload
        )
    }

    fun parseShareCode(input: String): ParsedScheduleResult {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            ShareCodeCodec.extractPortablePayload(trimmed)?.let { payload ->
                return WakeupScheduleCodec.decode(payload)
            }
            val nonBlankLines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (nonBlankLines.size >= 5 && nonBlankLines.first().startsWith("{")) {
                return WakeupScheduleCodec.decode(trimmed)
            }
        }
        error(diagnoseUnresolvedShareCode(input))
    }

    companion object {
        fun diagnoseUnresolvedShareCode(input: String): String {
            val normalized = ShareCodeCodec.normalizeInput(input)
            if (normalized.contains("FUNCLASS1", ignoreCase = true)) {
                return "离线口令不完整或已被截断，请重新复制完整口令"
            }
            if (input.contains("WakeUp课程表") || input.contains("口令「")) {
                return "本应用为纯本地课表，不支持拉取第三方云端短口令；请导出 .wakeup_schedule 或 .ics 文件后导入"
            }
            return "未识别到有效的离线口令，请粘贴完整的 FUNCLASS1:... 口令"
        }
    }
}
