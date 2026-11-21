package com.myfun.funclass.core.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
enum class HolidayAdjustType(val code: Int) {
    /** Holiday day off: suppress regular classes on targetDate. */
    OFF(0),
    /** Make-up class day: map targetDate's classes from sourceDate. */
    SHIFT(1);

    companion object {
        fun fromCode(code: Int): HolidayAdjustType = entries.firstOrNull { it.code == code } ?: OFF
    }
}

/**
 * Non-destructive holiday adjustment overlay.
 * tableId == 0L applies globally across all schedule tables; otherwise scoped to one table.
 */
@Serializable
data class HolidayAdjustment(
    val id: Long = 0L,
    val tableId: Long = 0L,
    val targetDate: String, // ISO yyyy-MM-dd
    val adjustType: HolidayAdjustType,
    /** Required when adjustType == SHIFT; ISO yyyy-MM-dd whose classes should be taught on targetDate. */
    val sourceDate: String = "",
    val label: String = "",
    val isPreset: Boolean = false
) {
    fun targetLocalDate(): LocalDate = LocalDate.parse(targetDate)
    fun sourceLocalDate(): LocalDate? = sourceDate.takeIf { it.isNotBlank() }?.let { LocalDate.parse(it) }
}

/**
 * Built-in China statutory holiday & make-up workday presets (non-destructive, user editable/toggleable).
 */
object ChinaHolidayPresets {

    fun presetsForYear(year: Int, tableId: Long = 0L): List<HolidayAdjustment> = when (year) {
        2026 -> listOf(
            // 元旦
            off(tableId, "2026-01-01", "元旦放假"),
            off(tableId, "2026-01-02", "元旦放假"),
            off(tableId, "2026-01-03", "元旦放假"),
            shift(tableId, "2026-01-04", "2026-01-02", "元旦调休(补周五课)"),
            // 清明节
            off(tableId, "2026-04-04", "清明放假"),
            off(tableId, "2026-04-05", "清明放假"),
            off(tableId, "2026-04-06", "清明放假"),
            // 劳动节
            off(tableId, "2026-05-01", "劳动节放假"),
            off(tableId, "2026-05-02", "劳动节放假"),
            off(tableId, "2026-05-03", "劳动节放假"),
            off(tableId, "2026-05-04", "劳动节放假"),
            off(tableId, "2026-05-05", "劳动节放假"),
            shift(tableId, "2026-05-09", "2026-05-05", "劳动节调休(补周二课)"),
            // 端午节
            off(tableId, "2026-06-19", "端午节放假"),
            off(tableId, "2026-06-20", "端午节放假"),
            off(tableId, "2026-06-21", "端午节放假"),
            // 中秋节
            off(tableId, "2026-09-25", "中秋节放假"),
            off(tableId, "2026-09-26", "中秋节放假"),
            off(tableId, "2026-09-27", "中秋节放假"),
            // 国庆节
            off(tableId, "2026-10-01", "国庆放假"),
            off(tableId, "2026-10-02", "国庆放假"),
            off(tableId, "2026-10-03", "国庆放假"),
            off(tableId, "2026-10-04", "国庆放假"),
            off(tableId, "2026-10-05", "国庆放假"),
            off(tableId, "2026-10-06", "国庆放假"),
            off(tableId, "2026-10-07", "国庆放假"),
            shift(tableId, "2026-09-20", "2026-10-05", "国庆调休(补周一课)"),
            shift(tableId, "2026-10-10", "2026-10-06", "国庆调休(补周二课)")
        )
        2027 -> listOf(
            off(tableId, "2027-01-01", "元旦放假"),
            off(tableId, "2027-04-05", "清明放假"),
            off(tableId, "2027-05-01", "劳动节放假"),
            off(tableId, "2027-05-02", "劳动节放假"),
            off(tableId, "2027-05-03", "劳动节放假"),
            off(tableId, "2027-06-09", "端午节放假"),
            off(tableId, "2027-09-15", "中秋节放假"),
            off(tableId, "2027-10-01", "国庆放假"),
            off(tableId, "2027-10-02", "国庆放假"),
            off(tableId, "2027-10-03", "国庆放假"),
            off(tableId, "2027-10-04", "国庆放假"),
            off(tableId, "2027-10-05", "国庆放假"),
            off(tableId, "2027-10-06", "国庆放假"),
            off(tableId, "2027-10-07", "国庆放假")
        )
        else -> emptyList()
    }

    private fun off(tableId: Long, date: String, label: String) = HolidayAdjustment(
        tableId = tableId,
        targetDate = date,
        adjustType = HolidayAdjustType.OFF,
        label = label,
        isPreset = true
    )

    private fun shift(tableId: Long, target: String, source: String, label: String) = HolidayAdjustment(
        tableId = tableId,
        targetDate = target,
        adjustType = HolidayAdjustType.SHIFT,
        sourceDate = source,
        label = label,
        isPreset = true
    )
}
