package com.myfun.funclass.core.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Immutable discrete set of academic week numbers (1-based, typically 1..30).
 * Solves legacy limitation where irregular weeks like "1, 3, 5-9, 12" had to be split into multiple rows.
 */
@Serializable(with = WeekSetSerializer::class)
data class WeekSet(val weeks: Set<Int>) {

    init {
        require(weeks.all { it > 0 }) { "Week numbers must be positive: $weeks" }
    }

    val isEmpty: Boolean get() = weeks.isEmpty()
    val sortedWeeks: List<Int> by lazy { weeks.sorted() }
    val minWeek: Int? get() = sortedWeeks.firstOrNull()
    val maxWeek: Int? get() = sortedWeeks.lastOrNull()

    operator fun contains(week: Int): Boolean = week in weeks

    fun isAllOdd(): Boolean = weeks.isNotEmpty() && weeks.all { it % 2 == 1 }
    fun isAllEven(): Boolean = weeks.isNotEmpty() && weeks.all { it % 2 == 0 }

    /**
     * Compact storage encoding, e.g. "1-8,10,12-16".
     */
    fun encode(): String {
        if (weeks.isEmpty()) return ""
        val sorted = sortedWeeks
        val parts = mutableListOf<String>()
        var rangeStart = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur == prev + 1) {
                prev = cur
            } else {
                parts += if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev"
                rangeStart = cur
                prev = cur
            }
        }
        parts += if (rangeStart == prev) "$rangeStart" else "$rangeStart-$prev"
        return parts.joinToString(",")
    }

    /**
     * Human-readable Chinese summary, e.g. "第1-16周", "第1-15周(单)", "第1-4,6,8-12周".
     */
    fun toDisplayString(): String {
        if (weeks.isEmpty()) return "未设置周次"
        val sorted = sortedWeeks
        val first = sorted.first()
        val last = sorted.last()
        if (sorted.size == last - first + 1) {
            return if (first == last) "第${first}周" else "第${first}-${last}周"
        }
        if (sorted.size >= 2) {
            val step2 = (first..last step 2).toList()
            if (sorted == step2) {
                val tag = if (first % 2 == 1) "单" else "双"
                return "第${first}-${last}周($tag)"
            }
        }
        return "第${encode()}周"
    }

    /**
     * Decomposes arbitrary discrete week set into legacy (startWeek, endWeek, type) segments
     * where type: 0 = every week, 1 = odd weeks, 2 = even weeks.
     * Used for 100% lossless export to `.wakeup_schedule`.
     */
    fun toLegacySegments(): List<LegacyWeekSegment> {
        if (weeks.isEmpty()) return emptyList()
        val sorted = sortedWeeks
        val first = sorted.first()
        val last = sorted.last()
        if (sorted.size == last - first + 1) {
            return listOf(LegacyWeekSegment(first, last, LEGACY_TYPE_ALL))
        }
        if (sorted.size >= 2 && sorted == (first..last step 2).toList()) {
            val type = if (first % 2 == 1) LEGACY_TYPE_ODD else LEGACY_TYPE_EVEN
            return listOf(LegacyWeekSegment(first, last, type))
        }
        // Fallback: split into contiguous runs
        val result = mutableListOf<LegacyWeekSegment>()
        var rangeStart = sorted[0]
        var prev = sorted[0]
        for (i in 1 until sorted.size) {
            val cur = sorted[i]
            if (cur == prev + 1) {
                prev = cur
            } else {
                result += LegacyWeekSegment(rangeStart, prev, LEGACY_TYPE_ALL)
                rangeStart = cur
                prev = cur
            }
        }
        result += LegacyWeekSegment(rangeStart, prev, LEGACY_TYPE_ALL)
        return result
    }

    companion object {
        const val LEGACY_TYPE_ALL = 0
        const val LEGACY_TYPE_ODD = 1
        const val LEGACY_TYPE_EVEN = 2

        fun range(start: Int, end: Int, legacyType: Int = LEGACY_TYPE_ALL): WeekSet {
            if (start <= 0 || end < start) return WeekSet(emptySet())
            val set = (start..end).filter { w ->
                when (legacyType) {
                    LEGACY_TYPE_ODD -> w % 2 == 1
                    LEGACY_TYPE_EVEN -> w % 2 == 0
                    else -> true
                }
            }.toSet()
            return WeekSet(set)
        }

        fun decode(encoded: String): WeekSet {
            val trimmed = encoded.trim()
            if (trimmed.isEmpty()) return WeekSet(emptySet())

            val segments = trimmed.split(Regex("[,，、;；\\s]+"))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (segments.isEmpty()) return WeekSet(emptySet())

            // Detect a global odd/even modifier written only once at the end (e.g. "1-5,7-15周(单)")
            val lastSeg = segments.last()
            val hasPerSegmentTags = segments.dropLast(1).any { seg ->
                seg.contains("单") || seg.contains("双") ||
                    seg.contains("odd", ignoreCase = true) || seg.contains("even", ignoreCase = true)
            }
            val globalOdd = !hasPerSegmentTags && (lastSeg.contains("单") || lastSeg.contains("odd", ignoreCase = true))
            val globalEven = !hasPerSegmentTags && (lastSeg.contains("双") || lastSeg.contains("even", ignoreCase = true))

            val set = mutableSetOf<Int>()
            for (p in segments) {
                val segOdd = p.contains("单") || p.contains("odd", ignoreCase = true)
                val segEven = p.contains("双") || p.contains("even", ignoreCase = true)
                val isOdd = if (segOdd || segEven) segOdd else globalOdd
                val isEven = if (segOdd || segEven) segEven else globalEven

                val cleaned = p.replace(Regex("[^0-9\\-]"), "")
                if (cleaned.contains("-")) {
                    val bounds = cleaned.split("-").filter { it.isNotEmpty() }
                    if (bounds.size >= 2) {
                        val s = bounds[0].toIntOrNull() ?: continue
                        val e = bounds[1].toIntOrNull() ?: continue
                        if (s in 1..30 && e in s..30) {
                            for (w in s..e) {
                                if (isOdd && w % 2 == 0) continue
                                if (isEven && w % 2 == 1) continue
                                set += w
                            }
                        }
                    }
                } else {
                    cleaned.toIntOrNull()?.takeIf { it in 1..30 }?.let { w ->
                        if (!(isOdd && w % 2 == 0) && !(isEven && w % 2 == 1)) {
                            set += w
                        }
                    }
                }
            }
            return WeekSet(set)
        }

        fun of(vararg weeks: Int): WeekSet = WeekSet(weeks.filter { it > 0 }.toSet())
    }
}

@Serializable
data class LegacyWeekSegment(
    val startWeek: Int,
    val endWeek: Int,
    val type: Int
)

object WeekSetSerializer : KSerializer<WeekSet> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("WeekSet", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: WeekSet) {
        encoder.encodeString(value.encode())
    }

    override fun deserialize(decoder: Decoder): WeekSet {
        return WeekSet.decode(decoder.decodeString())
    }
}
