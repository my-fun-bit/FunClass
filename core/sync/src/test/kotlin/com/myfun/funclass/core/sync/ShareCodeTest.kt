package com.myfun.funclass.core.sync

import com.myfun.funclass.core.model.Course
import com.myfun.funclass.core.model.CourseSession
import com.myfun.funclass.core.model.DefaultTimeSchemes
import com.myfun.funclass.core.model.ScheduleTable
import com.myfun.funclass.core.model.WeekSet
import com.myfun.funclass.core.parser.WakeupScheduleCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPOutputStream

class ShareCodeTest {

    private fun sampleWakeupPayload(): String {
        val scheme = DefaultTimeSchemes.standardCollege(1L)
        val table = ScheduleTable(id = 1L, name = "计算机学院课表", termStartDate = "2026-09-07", maxWeeks = 18, maxNodes = 12)
        val courses = listOf(
            Course(
                id = 1L,
                tableId = 1L,
                name = "操作系统_双语",
                sessions = listOf(
                    CourseSession(
                        id = 1L,
                        courseId = 1L,
                        tableId = 1L,
                        dayOfWeek = 2,
                        startNode = 3,
                        nodeCount = 2,
                        weeks = WeekSet.range(1, 16),
                        room = "教4-105",
                        teacher = "王教授"
                    )
                )
            )
        )
        return WakeupScheduleCodec.encode(table, scheme, courses)
    }

    @Test
    fun portableTokens_roundTripWithWrappedNewlinesUnderscoresAndStandardBase64() {
        val payload = sampleWakeupPayload()
        val token = ShareCodeCodec.encodePortableToken(payload)
        assertTrue(token.startsWith("FUNCLASS1:"))

        // Simulated messaging-app wrapped token inside Chinese text
        val half = token.length / 2
        val wrapped = "请复制导入：\n${token.substring(0, half)}\n${token.substring(half)}\n谢谢"
        val extracted = ShareCodeCodec.extractPortablePayload(wrapped)
        assertNotNull(extracted)
        val decoded = WakeupScheduleCodec.decode(extracted!!)
        assertEquals("计算机学院课表", decoded.tableName)
        assertEquals("操作系统_双语", decoded.courses.first().name)

        // Token containing '_' and '-' in URL-safe Base64 must NOT lose underscores during normalization
        val fullWidthColon = token.replace("FUNCLASS1:", "FUNCLASS1：")
        assertEquals(payload, ShareCodeCodec.extractPortablePayload(fullWidthColon))

        // Standard Base64 (with '+' '/' '=' padding) under FUNCLASS1: must also decode cleanly
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { it.write(payload.toByteArray(Charsets.UTF_8)) }
        val stdB64Token = "FUNCLASS1:" + Base64.getEncoder().encodeToString(bos.toByteArray())
        assertEquals(payload, ShareCodeCodec.extractPortablePayload(stdB64Token))
    }

    @Test
    fun unresolvedShareCodes_returnNullAndProduceActionableDiagnostics() {
        assertNull(ShareCodeCodec.extractPortablePayload("FUNCLASS1:not_valid_gzip"))
        assertNull(ShareCodeCodec.extractPortablePayload("FC-AB12CD"))

        val brokenOfflineDiag = ShareCodeRepository.diagnoseUnresolvedShareCode("FUNCLASS1:not_valid_gzip")
        assertTrue(brokenOfflineDiag.contains("不完整"))

        val wakeupThirdPartyDiag = ShareCodeRepository.diagnoseUnresolvedShareCode("这是来自「WakeUp课程表」的分享，口令「abc123」")
        assertTrue(wakeupThirdPartyDiag.contains("纯本地"))

        val genericDiag = ShareCodeRepository.diagnoseUnresolvedShareCode("随便输入的内容")
        assertTrue(genericDiag.contains("FUNCLASS1"))
    }
}
